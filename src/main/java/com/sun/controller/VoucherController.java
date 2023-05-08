package com.sun.controller;


import com.sun.common.CommonResult;
import com.sun.common.ErrorCode;
import com.sun.entity.Voucher;
import com.sun.exception.ThrowUtils;
import com.sun.service.VoucherService;
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
     * 新增秒杀券的同时将其存储到 Redis，同时还需要在优惠券表中新增优惠券
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
