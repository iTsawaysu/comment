package com.sun.service;

import com.sun.dto.Result;
import com.sun.entity.Voucher;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * @author sun
 */
public interface VoucherService extends IService<Voucher> {

    Result queryVoucherOfShop(Long shopId);

    void addSeckillVoucher(Voucher voucher);
}
