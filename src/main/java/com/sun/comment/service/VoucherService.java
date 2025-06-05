package com.sun.comment.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.sun.comment.common.Result;
import com.sun.comment.entity.Voucher;

import java.util.List;

/**
 * @author sun
 */
public interface VoucherService extends IService<Voucher> {

    /**
     * 新增秒杀券的同时将其存储到 Redis，同时还需要在优惠券表中新增优惠券
     */
    Result<Long> addSeckillVoucher(Voucher voucher);

    /**
     * 根据 ShopId 查询店铺的优惠券列表
     */
    Result<List<Voucher>> getVoucherByShopId(Long shopId);
}
