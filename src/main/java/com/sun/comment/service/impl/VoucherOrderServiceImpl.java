package com.sun.comment.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.sun.comment.common.CommonResult;
import com.sun.comment.common.ErrorCode;
import com.sun.comment.common.exception.BusinessException;
import com.sun.comment.common.exception.ThrowUtils;
import com.sun.comment.entity.SeckillVoucher;
import com.sun.comment.entity.VoucherOrder;
import com.sun.comment.mapper.VoucherOrderMapper;
import com.sun.comment.service.SeckillVoucherService;
import com.sun.comment.service.VoucherOrderService;
import com.sun.comment.utils.RedisIdWorker;
import com.sun.comment.utils.UserHolder;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.aop.framework.AopContext;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

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

    @Override
    public CommonResult<Long> seckillVoucher(Long voucherId) {
        // 判断秒杀是否开始或结束、库存是否充足
        SeckillVoucher seckillVoucher = seckillVoucherService.lambdaQuery().eq(SeckillVoucher::getVoucherId, voucherId).one();
        ThrowUtils.throwIf(seckillVoucher == null, ErrorCode.NOT_FOUND_ERROR);
        LocalDateTime now = LocalDateTime.now();
        ThrowUtils.throwIf(now.isBefore(seckillVoucher.getBeginTime()), ErrorCode.OPERATION_ERROR, "秒杀尚未开始");
        ThrowUtils.throwIf(now.isAfter(seckillVoucher.getEndTime()), ErrorCode.OPERATION_ERROR, "秒杀已经结束");
        ThrowUtils.throwIf(seckillVoucher.getStock() < 1, ErrorCode.OPERATION_ERROR, "库存不足");
        // 下单
        RLock lock = redissonClient.getLock("seckillVoucherOrder");
        try {
            boolean tryLock = lock.tryLock(3, 10, TimeUnit.SECONDS);
            ThrowUtils.throwIf(!tryLock, ErrorCode.OPERATION_ERROR, "禁止重复下单");
            VoucherOrderService proxy = (VoucherOrderService) AopContext.currentProxy();
            return proxy.createVoucherOrder(voucherId);
        } catch (InterruptedException e) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, e.getMessage());
        } finally {
            lock.unlock();
        }
    }

    /**
     * 下单（超卖、一人一单）
     */
    @Override
    @Transactional
    public CommonResult<Long> createVoucherOrder(Long voucherId) {
        // 再次判断当前用户是否下过单
        Long count = this.lambdaQuery()
                .eq(VoucherOrder::getVoucherId, voucherId)
                .eq(VoucherOrder::getUserId, UserHolder.getUser().getId())
                .count();
        ThrowUtils.throwIf(count > 0, ErrorCode.OPERATION_ERROR, "禁止重复下单");
        // 扣减库存
        boolean isUpdated = seckillVoucherService.lambdaUpdate()
                .setSql("stock = stock - 1")
                .eq(SeckillVoucher::getVoucherId, voucherId)
                .gt(SeckillVoucher::getStock, 0)
                .update();
        ThrowUtils.throwIf(!isUpdated, ErrorCode.OPERATION_ERROR, "扣减库存失败");
        // 下单
        VoucherOrder voucherOrder = VoucherOrder.builder()
                .id(redisIdWorker.nextId("seckillVoucherOrder"))
                .voucherId(voucherId)
                .userId(UserHolder.getUser().getId())
                .build();
        ThrowUtils.throwIf(!this.save(voucherOrder), ErrorCode.OPERATION_ERROR, "下单失败");
        return CommonResult.success(voucherOrder.getId());
    }
}

