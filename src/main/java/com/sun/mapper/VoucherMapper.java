package com.sun.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sun.entity.Voucher;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * @author sun
 */
public interface VoucherMapper extends BaseMapper<Voucher> {

    /**
     * 通过 shopId 对 tb_voucher 和 tb_seckill_voucher 进行连表查询
     */
    List<Voucher> getVoucherByShopId(@Param("shopId") Long shopId);
}
