package com.sun.comment.controller;


import com.sun.comment.common.CommonResult;
import com.sun.comment.common.ErrorCode;
import com.sun.comment.entity.Voucher;
import com.sun.comment.common.exception.ThrowUtils;
import com.sun.comment.service.VoucherService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.List;

/**
 * @author sun
 */
@RestController
@RequestMapping("/voucher")
public class VoucherController {
    @Resource
    private VoucherService voucherService;

    /**
     * 新增普通券
     * @param voucher 优惠券信息
     * @return 优惠券id
     */
    @PostMapping
    public CommonResult<Long> addVoucher(@RequestBody Voucher voucher) {
        boolean result = voucherService.save(voucher);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        return CommonResult.success(voucher.getId());
    }

    /**
     * 新增优惠券的同时新增秒杀券
     * @param voucher 优惠券信息
     * @return 优惠券 id
     */
    @PostMapping("/seckill")
    public CommonResult<Long> addSeckillVoucher(@RequestBody Voucher voucher) {
        return voucherService.addSeckillVoucher(voucher);
    }

    /**
     * 根据 ShopId 查询店铺的优惠券列表
     * @param shopId 店铺id
     * @return 优惠券列表
     */
    @GetMapping("/list/{shopId}")
    public CommonResult<List<Voucher>> getVoucherByShopId(@PathVariable("shopId") Long shopId) {
        return voucherService.getVoucherByShopId(shopId);
    }
}
