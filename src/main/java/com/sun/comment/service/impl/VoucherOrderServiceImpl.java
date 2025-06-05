package com.sun.comment.service.impl;

import com.sun.comment.common.Result;
import com.sun.comment.entity.VoucherOrder;
import com.sun.comment.mapper.VoucherOrderMapper;
import com.sun.comment.service.VoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

/**
 * @author sun
 */
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements VoucherOrderService {
    /**
     * 秒杀下单优惠券
     */
    @Override
    public Result<Long> seckillVoucher(Long voucherId) {
        return null;
    }

    /**
     * 下单（超卖、一人一单）
     */
    @Override
    public Result<Long> createVoucherOrder(Long voucherId) {
        return null;
    }

    /**
     * 异步下单
     */
    @Override
    public void createVoucherOrder(VoucherOrder voucherOrder) {

    }
}
