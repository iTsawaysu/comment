package com.sun.comment.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.sun.comment.common.CommonResult;
import com.sun.comment.entity.Voucher;

import java.util.List;

/**
 * @author sun
 */
public interface VoucherService extends IService<Voucher> {

    /**
     * 新增优惠券的同时新增秒杀券
     */
    CommonResult<Long> addSeckillVoucher(Voucher voucher);

    /**
     * 根据 ShopId 查询店铺的优惠券列表
     */
    CommonResult<List<Voucher>> getVoucherByShopId(Long shopId);
}
