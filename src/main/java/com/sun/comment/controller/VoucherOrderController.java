package com.sun.comment.controller;


import com.sun.comment.common.Result;
import com.sun.comment.service.VoucherOrderService;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;

/**
 * @author sun
 */
@RestController
@RequestMapping("/voucher-order")
public class VoucherOrderController {
    @Resource
    private VoucherOrderService voucherOrderService;

    /**
     * 秒杀下单优惠券
     * @param voucherId     优惠券 ID
     * @return              订单 ID
     */
    @PostMapping("/seckill/{id}")
    public Result<Long> seckillVoucher(@PathVariable("id") Long voucherId) {
        return voucherOrderService.seckillVoucher(voucherId);
    }
}
