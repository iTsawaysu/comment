package com.sun.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.sun.common.CommonResult;
import com.sun.common.ErrorCode;
import com.sun.entity.SeckillVoucher;
import com.sun.entity.VoucherOrder;
import com.sun.exception.BusinessException;
import com.sun.exception.ThrowUtils;
import com.sun.mapper.VoucherOrderMapper;
import com.sun.service.SeckillVoucherService;
import com.sun.service.VoucherOrderService;
import com.sun.utils.RedisIdWorker;
import com.sun.utils.SimpleDistributedLock4Redis;
import com.sun.utils.UserHolder;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.aop.framework.AopContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static com.sun.common.RedisConstants.LOCK_ORDER_KEY;
import static com.sun.common.RedisConstants.TTL_TWO;

/**
 * @author sun
 */
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements VoucherOrderService {

    @Resource
    private SeckillVoucherService seckillVoucherService;

    @Resource
    private RedisIdWorker redisIdWorker;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private RedissonClient redissonClient;

    // Lua 脚本
    private static final DefaultRedisScript<Long> SCRIPT;

    static {
        SCRIPT = new DefaultRedisScript<>();
        SCRIPT.setLocation(new ClassPathResource("SeckillVoucher.lua"));
        SCRIPT.setResultType(Long.class);
    }

    // 阻塞队列：一个线程尝试从队列中获取元素时，若队列中没有元素线程就会被阻塞，直到队列中有元素时线程才会被唤醒并且去获取元素。
    private BlockingQueue<VoucherOrder> orderTasks = new ArrayBlockingQueue<>(1024 * 1024);

    // 代理对象
    VoucherOrderService proxy;

    /**
     * VERSION5.0 - 秒杀下单优惠券（通过 Redisson 解决一人一单问题；通过 Lua 脚本判断用户有购买资格后直接返回，异步下单）
     */
    @Override
    public CommonResult<Long> seckillVoucher(Long voucherId) {
        // 1. 判断秒杀是否开始或结束
        SeckillVoucher seckillVoucher = seckillVoucherService.getById(voucherId);
        ThrowUtils.throwIf(seckillVoucher == null, ErrorCode.NOT_FOUND_ERROR);
        LocalDateTime now = LocalDateTime.now();
        ThrowUtils.throwIf(now.isBefore(seckillVoucher.getBeginTime()), ErrorCode.OPERATION_ERROR, "秒杀尚未开始");
        ThrowUtils.throwIf(now.isAfter(seckillVoucher.getEndTime()), ErrorCode.OPERATION_ERROR, "秒杀已经结束");

        // 2. 判断用户是否有购买资格 —— 库存充足且该用户未下过单，即 Lua 脚本的执行结果为 0。
        Long userId = UserHolder.getUser().getId();
        Long executeResult = stringRedisTemplate.execute(
                SCRIPT,
                Collections.emptyList(),
                voucherId.toString(), userId.toString()
        );
        int result = executeResult.intValue();
        if (result != 0) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, result == 1 ? "库存不足" : "请勿重复下单");
        }

        // 3. 将下单信息保存到阻塞队列中，让线程异步的从队列中获取下单信息并操作数据库
        VoucherOrder voucherOrder = new VoucherOrder();
        voucherOrder.setUserId(userId);
        voucherOrder.setId(redisIdWorker.nextId("seckillVoucherOrder"));
        voucherOrder.setVoucherId(voucherId);
        orderTasks.add(voucherOrder);

        // 4. 获取代理对象后赋值给 proxy
        proxy = (VoucherOrderService) AopContext.currentProxy();

        // 5. 直接返回订单号告诉用户下单成功，业务结束。（异步操作数据库下单）
        return CommonResult.success(voucherOrder.getId());
    }

    // 线程池
    private static final ExecutorService ES = Executors.newSingleThreadExecutor();

    @PostConstruct
    public void init() {
        ES.submit(new VoucherOrderHandler());
    }

    /**
     * 异步任务
     */
    private class VoucherOrderHandler implements Runnable {
        @Override
        public void run() {
            while (true) {
                try {
                    // 获取队列中的消息并操作数据库下单
                    VoucherOrder voucherOrder = orderTasks.take();
                    handleVoucherOrder(voucherOrder);
                } catch (Exception e) {
                    throw new BusinessException(ErrorCode.OPERATION_ERROR, "下单失败");
                }
            }
        }

        private void handleVoucherOrder(VoucherOrder voucherOrder) {
            // userId 存储在 ThreadLocal 中、代理对象在主线程中，在新开启的线程中无法获取到这些信息。
            Long userId = voucherOrder.getUserId();
            RLock lock = redissonClient.getLock(LOCK_ORDER_KEY + userId);
            boolean isLocked = lock.tryLock();
            if (!isLocked) {
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, "获取锁失败");
            }
            try {
                proxy.createVoucherOrder(voucherOrder);
            } finally {
                lock.unlock();
            }
        }
    }

    /**
     * 异步下单
     */
    @Override
    public void createVoucherOrder(VoucherOrder voucherOrder) {
        // 1. 再次判断当前用户是否下过单
        Long voucherId = voucherOrder.getId();
        Long userId = voucherOrder.getUserId();
        Integer count = this.lambdaQuery()
                .eq(VoucherOrder::getVoucherId, voucherId)
                .eq(VoucherOrder::getUserId, userId)
                .count();
        ThrowUtils.throwIf(count > 0, ErrorCode.OPERATION_ERROR, "禁止重复下单");

        // 2. 扣减库存
        boolean result = seckillVoucherService.update()
                .setSql("stock = stock - 1")
                .eq("voucher_id", voucherId)
                .gt("stock", 0)
                .update();
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR, "下单失败");

        // 3. 下单
        result = this.save(voucherOrder);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR, "下单失败");
    }

    /**
     * VERSION4.0 - 秒杀下单优惠券（通过 Redisson 分布式锁解决一人一单问题）
     */
    public CommonResult<Long> seckillVoucherRedisson(Long voucherId) {
        // 判断秒杀是否开始或结束、库存是否充足。
        SeckillVoucher seckillVoucher = seckillVoucherService.getById(voucherId);
        ThrowUtils.throwIf(seckillVoucher == null, ErrorCode.NOT_FOUND_ERROR);
        LocalDateTime now = LocalDateTime.now();
        ThrowUtils.throwIf(now.isBefore(seckillVoucher.getBeginTime()), ErrorCode.OPERATION_ERROR, "秒杀尚未开始");
        ThrowUtils.throwIf(now.isAfter(seckillVoucher.getEndTime()), ErrorCode.OPERATION_ERROR, "秒杀已经结束");
        ThrowUtils.throwIf(seckillVoucher.getStock() < 1, ErrorCode.OPERATION_ERROR, "库存不足");

        // 下单
        // SimpleDistributedLock4Redis lock = new SimpleDistributedLock4Redis("order:" + UserHolder.getUser().getId(), stringRedisTemplate);
        // boolean tryLock = lock.tryLock(TTL_TWO);
        RLock lock = redissonClient.getLock("seckillVoucherOrder");
        boolean tryLock = lock.tryLock();
        ThrowUtils.throwIf(!tryLock, ErrorCode.OPERATION_ERROR, "禁止重复下单");
        try {
            VoucherOrderService voucherOrderService = (VoucherOrderService) AopContext.currentProxy();
            return voucherOrderService.createVoucherOrder(voucherId);
        } finally {
            lock.unlock();
        }
    }

    /**
     * VERSION3.0 - 秒杀下单优惠券（通过分布式锁解决一人一单问题）
     */
    public CommonResult<Long> seckillVoucherDistributedLock(Long voucherId) {
        // 判断秒杀是否开始或结束、库存是否充足。
        SeckillVoucher seckillVoucher = seckillVoucherService.getById(voucherId);
        ThrowUtils.throwIf(seckillVoucher == null, ErrorCode.NOT_FOUND_ERROR);
        LocalDateTime now = LocalDateTime.now();
        ThrowUtils.throwIf(now.isBefore(seckillVoucher.getBeginTime()), ErrorCode.OPERATION_ERROR, "秒杀尚未开始");
        ThrowUtils.throwIf(now.isAfter(seckillVoucher.getEndTime()), ErrorCode.OPERATION_ERROR, "秒杀已经结束");
        ThrowUtils.throwIf(seckillVoucher.getStock() < 1, ErrorCode.OPERATION_ERROR, "库存不足");

        // 下单
        SimpleDistributedLock4Redis lock = new SimpleDistributedLock4Redis("order:" + UserHolder.getUser().getId(), stringRedisTemplate);
        boolean tryLock = lock.tryLock(TTL_TWO);
        ThrowUtils.throwIf(!tryLock, ErrorCode.OPERATION_ERROR, "禁止重复下单");
        try {
            VoucherOrderService voucherOrderService = (VoucherOrderService) AopContext.currentProxy();
            return voucherOrderService.createVoucherOrder(voucherId);
        } finally {
            lock.unlock();
        }
    }

    /**
     * VERSION2.0 - 秒杀下单优惠券（通过 synchronized 解决一人一单问题）
     */
    public CommonResult<Long> seckillVoucherSynchronized(Long voucherId) {
        // 判断秒杀是否开始或结束、库存是否充足。
        SeckillVoucher seckillVoucher = seckillVoucherService.getById(voucherId);
        ThrowUtils.throwIf(seckillVoucher == null, ErrorCode.NOT_FOUND_ERROR);
        LocalDateTime now = LocalDateTime.now();
        ThrowUtils.throwIf(now.isBefore(seckillVoucher.getBeginTime()), ErrorCode.OPERATION_ERROR, "秒杀尚未开始");
        ThrowUtils.throwIf(now.isAfter(seckillVoucher.getEndTime()), ErrorCode.OPERATION_ERROR, "秒杀已经结束");
        ThrowUtils.throwIf(seckillVoucher.getStock() < 1, ErrorCode.OPERATION_ERROR, "库存不足");

        // 下单
        synchronized (UserHolder.getUser().getId().toString().intern()) {
            // 1. 锁释放后才能提交事务，若释放锁的瞬间其他线程抢占到锁则继续执行，仍然存在一人多单的问题，因此需要扩大锁的范围为整个方法。
            // 2. this 指向当前类而非代理类，Spring 事务通过动态代理 AOP 实现，必需使用代理对象调用方法。
            // 3. 导入 aspectjweaver，在主启动类上添加 @EnableAspectJAutoProxy(exposeProxy = true) 注解。（exposeProxy 暴露代理对象）
            VoucherOrderService voucherOrderService = (VoucherOrderService) AopContext.currentProxy();
            return voucherOrderService.createVoucherOrder(voucherId);
            // return this.createVoucherOrder(voucherId);
        }
    }

    /**
     * 下单（超卖、一人一单）
     */
    @Override
    @Transactional
    public CommonResult<Long> createVoucherOrder(Long voucherId) {
        // 1. 判断当前用户是否下过单
        Long userId = UserHolder.getUser().getId();
        Integer count = this.lambdaQuery()
                .eq(VoucherOrder::getVoucherId, voucherId)
                .eq(VoucherOrder::getUserId, userId)
                .count();
        ThrowUtils.throwIf(count > 0, ErrorCode.OPERATION_ERROR, "禁止重复下单");

        // 2. 扣减库存
        boolean result = seckillVoucherService.update()
                .setSql("stock = stock - 1")
                .eq("voucher_id", voucherId)
                .gt("stock", 0)
                .update();
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR, "下单失败");

        // 3. 下单
        VoucherOrder voucherOrder = new VoucherOrder();
        voucherOrder.setUserId(userId);
        voucherOrder.setId(redisIdWorker.nextId("seckillVoucherOrder"));
        voucherOrder.setVoucherId(voucherId);
        result = this.save(voucherOrder);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR, "下单失败");
        return CommonResult.success(voucherOrder.getId());
    }

    /**
     * VERSION1.0 - 秒杀下单优惠券（通过 CAS 解决超卖问题）
     */
    public CommonResult<Long> seckillVoucherCAS(Long voucherId) {
        // 1. 判断秒杀是否开始或结束、库存是否充足。
        SeckillVoucher seckillVoucher = seckillVoucherService.getById(voucherId);
        ThrowUtils.throwIf(seckillVoucher == null, ErrorCode.NOT_FOUND_ERROR);
        LocalDateTime now = LocalDateTime.now();
        ThrowUtils.throwIf(now.isBefore(seckillVoucher.getBeginTime()), ErrorCode.OPERATION_ERROR, "秒杀尚未开始");
        ThrowUtils.throwIf(now.isAfter(seckillVoucher.getEndTime()), ErrorCode.OPERATION_ERROR, "秒杀已经结束");
        Integer stock = seckillVoucher.getStock();
        ThrowUtils.throwIf(stock < 1, ErrorCode.OPERATION_ERROR, "库存不足");

        // 2. 扣减库存
        Long userId = UserHolder.getUser().getId();
        // UPDATE tb_seckill_voucher SET stock = stock - 1 WHERE voucher_id = ?
        boolean result = seckillVoucherService.update()
                .setSql("stock = stock - 1")
                .eq("voucher_id", voucherId)
                .gt("stock", 0)
                .update();
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);

        // 3. 下单
        VoucherOrder voucherOrder = new VoucherOrder();
        voucherOrder.setUserId(userId);
        voucherOrder.setId(redisIdWorker.nextId("seckillVoucherOrder"));
        voucherOrder.setVoucherId(voucherId);
        result = this.save(voucherOrder);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        return CommonResult.success(voucherId);
    }
}
