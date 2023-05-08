package com.sun.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.sun.common.CommonResult;
import com.sun.common.ErrorCode;
import com.sun.entity.SeckillVoucher;
import com.sun.entity.Voucher;
import com.sun.exception.ThrowUtils;
import com.sun.mapper.VoucherMapper;
import com.sun.service.SeckillVoucherService;
import com.sun.service.VoucherService;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.List;

import static com.sun.common.RedisConstants.SECKILL_STOCK_KEY;

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
    public CommonResult<Long> addSeckillVoucher(Voucher voucher) {
        // 新增优惠券
        boolean result = this.save(voucher);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR, "新增优惠券失败");

        // 新增秒杀券
        SeckillVoucher seckillVoucher = new SeckillVoucher();
        seckillVoucher.setVoucherId(voucher.getId());
        seckillVoucher.setStock(voucher.getStock());
        seckillVoucher.setBeginTime(voucher.getBeginTime());
        seckillVoucher.setEndTime(voucher.getEndTime());
        result = seckillVoucherService.save(seckillVoucher);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR, "新增秒杀券失败");

        // 将秒杀券存储到 Redis（SECKILL_STOCK_KEY = "seckill:stock:"）
        stringRedisTemplate.opsForValue().set(SECKILL_STOCK_KEY + voucher.getId(), voucher.getStock().toString());
        return CommonResult.success(voucher.getId());
    }

    /**
     * 根据 ShopId 查询店铺的优惠券列表
     */
    @Override
    public CommonResult<List<Voucher>> getVoucherByShopId(Long shopId) {
        // 查询商铺对应的优惠券信息
        // SELECT * FROM tb_voucher v LEFT JOIN tb_seckill_voucher sv ON v.id = sv.voucher_id WHERE v.shop_id = ? AND v.status = 1;
        List<Voucher> voucherList = this.getBaseMapper().getVoucherByShopId(shopId);
        return CommonResult.success(voucherList);
    }
}
