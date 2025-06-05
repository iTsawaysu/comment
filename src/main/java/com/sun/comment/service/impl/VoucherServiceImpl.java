package com.sun.comment.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.sun.comment.common.Result;
import com.sun.comment.entity.Voucher;
import com.sun.comment.mapper.VoucherMapper;
import com.sun.comment.service.SeckillVoucherService;
import com.sun.comment.service.VoucherService;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.List;

/**
 * @author sun
 */
@Service
public class VoucherServiceImpl extends ServiceImpl<VoucherMapper, Voucher> implements VoucherService {

    @Resource
    private SeckillVoucherService seckillVoucherService;

    @Resource
    private StringRedisTemplate stringRedisTemplate;


    /**
     * 新增秒杀券的同时将其存储到 Redis，同时还需要在优惠券表中新增优惠券
     */
    @Override
    @Transactional
    public Result<Long> addSeckillVoucher(Voucher voucher) {
        return null;
    }

    /**
     * 根据 ShopId 查询店铺的优惠券列表
     */
    @Override
    public Result<List<Voucher>> getVoucherByShopId(Long shopId) {
        // 查询商铺对应的优惠券信息
        // SELECT * FROM tb_voucher v LEFT JOIN tb_seckill_voucher sv ON v.id = sv.voucher_id WHERE v.shop_id = ? AND v.status = 1;
        List<Voucher> voucherList = this.getBaseMapper().getVoucherByShopId(shopId);
        return Result.success(voucherList);
    }
}
