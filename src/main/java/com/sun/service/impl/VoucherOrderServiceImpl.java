package com.sun.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.sun.dto.Result;
import com.sun.entity.VoucherOrder;
import com.sun.mapper.VoucherOrderMapper;
import com.sun.service.SeckillVoucherService;
import com.sun.service.VoucherOrderService;
import com.sun.utils.RedisIdWorker;
import com.sun.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.aop.framework.AopContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.util.annotation.Nullable;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * @author sun
 */
@Service
@SuppressWarnings("ALL")
@Slf4j
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
    private static final DefaultRedisScript<Long> SECKILL_SCRIPT;
    static {
        SECKILL_SCRIPT = new DefaultRedisScript<>();
        SECKILL_SCRIPT.setLocation(new ClassPathResource("SeckillVoucher.lua"));
        SECKILL_SCRIPT.setResultType(Long.class);
    }

    // 代理对象
    private VoucherOrderService currentProxy;

    @Override
    public Result seckillVoucher(Long voucherId) {
        // 1. 执行 Lua 脚本（有购买资格：向 stream.orders 中添加消息，内容包括 voucherId、userId、orderId）
        Long userId = UserHolder.getUser().getId();
        long orderId = redisIdWorker.nextId("order");
        Long executeResult = stringRedisTemplate.execute(
                SECKILL_SCRIPT,
                Collections.emptyList(),
                voucherId.toString(), userId.toString(), String.valueOf(orderId)
        );

        // 2. Lua 脚本的执行结果不为 0，则没有购买资格
        int result = executeResult.intValue();
        if (result != 0) {
            return Result.fail(result == 1 ? "库存不足！" : "请勿重复下单！");
        }

        // 3. 获取代理对象
        currentProxy = (VoucherOrderService) AopContext.currentProxy();

        // 4. 返回订单号（告诉用户下单成功，业务结束；执行异步下单操作数据库）
        return Result.ok(orderId);
    }


    // 异步处理线程池
    private static final ExecutorService SECKILL_ORDER_EXECUTOR = Executors.newSingleThreadExecutor();

    // 在当前类初始完毕后执行 VoucherOrderHandler 中的 run 方法
    @PostConstruct
    public void init() {
        SECKILL_ORDER_EXECUTOR.submit(new VoucherOrderHandler());
    }

    // 从队列中获取信息
    public class VoucherOrderHandler implements Runnable {
        String queueName = "stream.orders";
        String groupName = "orderGroup";
        String consumerName = "consumerOne";

        @Override
        public void run() {
            while (true) {
                try {
                    // 1. 获取消息队列中的订单信息
                    // XREAD GROUP orderGroup consumerOne COUNT 1 BLOCK 2000 STREAMS stream.orders >
                    // 队列 stream.orders、消费者组 orderGroup、消费者 consumerOne、每次读 1 条消息、阻塞时间 2s、从下一个未消费的消息开始。
                    List<MapRecord<String, Object, Object>> readingList = stringRedisTemplate.opsForStream().read(
                            Consumer.from(groupName, consumerName),
                            StreamReadOptions.empty().count(1).block(Duration.ofSeconds(2)),
                            StreamOffset.create(queueName, ReadOffset.lastConsumed())
                    );

                    // 2. 判断消息是否获取成功
                    if (readingList.isEmpty() || readingList == null) {
                        // 获取失败说明没有消息，则继续下一次循环
                        continue;
                    }

                    // 3. 解析消息中的订单信息
                    // MapRecord：String 代表 消息ID；两个 Object 代表 消息队列中的 Key-Value
                    MapRecord<String, Object, Object> record = readingList.get(0);
                    Map<Object, Object> recordValue = record.getValue();
                    VoucherOrder voucherOrder = BeanUtil.fillBeanWithMap(recordValue, new VoucherOrder(), true);

                    // 4. 获取成功则下单
                    handleVoucherOrder(voucherOrder);

                    // 5. 确认消息 XACK stream.orders orderGroup id
                    stringRedisTemplate.opsForStream().acknowledge(groupName, consumerName, record.getId());
                } catch (Exception e) {
                    log.error("订单处理异常", e);
                    handlePendingMessages();
                }
            }
        }

        private void handlePendingMessages() {
            while (true) {
                try {
                    // 1. 获取 pending-list 中的订单信息
                    // XREAD GROUP orderGroup consumerOne COUNT 1 STREAM stream.orders 0
                    List<MapRecord<String, Object, Object>> readingList = stringRedisTemplate.opsForStream().read(
                            Consumer.from(groupName, consumerName),
                            StreamReadOptions.empty().count(1),
                            StreamOffset.create(queueName, ReadOffset.from("0"))
                    );

                    // 2. 判断消息是否获取成功
                    if (readingList.isEmpty() || readingList == null) {
                        // 获取失败 pending-list 中没有异常消息，结束循环
                        break;
                    }

                    // 3. 解析消息中的订单信息并下单
                    MapRecord<String, Object, Object> record = readingList.get(0);
                    Map<Object, Object> recordValue = record.getValue();
                    VoucherOrder voucherOrder = BeanUtil.fillBeanWithMap(recordValue, new VoucherOrder(), true);
                    handleVoucherOrder(voucherOrder);

                    // 4. XACK
                    stringRedisTemplate.opsForStream().acknowledge(queueName, groupName, record.getId());
                } catch (Exception e) {
                    log.error("订单处理异常（pending-list）", e);
                    try {
                        // 稍微休眠一下再进行循环
                        Thread.sleep(20);
                    } catch (Exception ex) {
                        ex.printStackTrace();
                    }
                }
            }

        }

        @Nullable
        private void handleVoucherOrder(VoucherOrder voucherOrder) {
            Long userId = voucherOrder.getUserId();
            RLock lock = redissonClient.getLock("lock:order:" + userId);
            boolean isLocked = lock.tryLock();
            if (!isLocked) {
                log.error("不允许重复下单！");
                return;
            }
            try {
                // 该方法非主线程调用，代理对象需要在主线程中获取。
                currentProxy.createVoucherOrder(voucherOrder);
            } finally {
                lock.unlock();
            }
        }
    }

    @Transactional
    @Override
    public void createVoucherOrder(VoucherOrder voucherOrder) {
        Long userId = voucherOrder.getUserId();
        // 1. 一人一单
        Integer count = query()
                .eq("voucher_id", voucherOrder.getVoucherId())
                .eq("user_id", userId)
                .count();
        if (count > 0) {
            log.error("不可重复下单！");
            return;
        }

        // 2. 减扣库存
        boolean isAccomplished = seckillVoucherService.update()
                .setSql("stock = stock - 1")
                .eq("voucher_id", voucherOrder.getVoucherId()).gt("stock", 0)
                .update();
        if (!isAccomplished) {
            log.error("库存不足！");
            return;
        }

        // 3. 下单
        boolean isSaved = save(voucherOrder);
        if (!isSaved) {
            log.error("下单失败！");
            return;
        }
    }
}
