package com.sun.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.sun.common.CommonResult;
import com.sun.entity.Voucher;

import java.util.List;

/**
 * @author sun
 */
public interface VoucherService extends IService<Voucher> {

    /**
     * 新增秒杀券的同时将其存储到 Redis，同时还需要在优惠券表中新增优惠券
     */
    CommonResult<Long> addSeckillVoucher(Voucher voucher);

    /**
     * 根据 ShopId 查询店铺的优惠券列表
     */
    CommonResult<List<Voucher>> getVoucherByShopId(Long shopId);
}
