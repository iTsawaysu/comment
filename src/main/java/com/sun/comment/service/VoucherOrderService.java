package com.sun.comment.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.sun.comment.common.CommonResult;
import com.sun.comment.entity.VoucherOrder;

/**
 * @author sun
 */
public interface VoucherOrderService extends IService<VoucherOrder> {
    /**
     * 秒杀下单优惠券
     */
    CommonResult<Long> seckillVoucher(Long voucherId);

    /**
     * 下单（超卖、一人一单）
     */
    CommonResult<Long> createVoucherOrder(Long voucherId);
}
