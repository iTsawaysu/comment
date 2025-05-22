package com.sun.comment.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.sun.comment.common.CommonResult;
import com.sun.comment.common.ErrorCode;
import com.sun.comment.common.exception.ThrowUtils;
import com.sun.comment.entity.SeckillVoucher;
import com.sun.comment.entity.Voucher;
import com.sun.comment.mapper.VoucherMapper;
import com.sun.comment.service.SeckillVoucherService;
import com.sun.comment.service.VoucherService;
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

    /**
     * 新增优惠券的同时新增秒杀券
     */
    @Override
    @Transactional
    public CommonResult<Long> addSeckillVoucher(Voucher voucher) {
        // 新增优惠券
        ThrowUtils.throwIf(!this.save(voucher), ErrorCode.OPERATION_ERROR, "新增优惠券失败");
        // 新增秒杀券
        SeckillVoucher seckillVoucher = SeckillVoucher.builder()
                .voucherId(voucher.getId())
                .stock(voucher.getStock())
                .beginTime(voucher.getBeginTime())
                .endTime(voucher.getEndTime())
                .build();
        ThrowUtils.throwIf(!seckillVoucherService.save(seckillVoucher), ErrorCode.OPERATION_ERROR, "新增秒杀券失败");
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
