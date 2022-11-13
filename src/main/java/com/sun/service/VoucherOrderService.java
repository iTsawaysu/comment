package com.sun.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.sun.dto.Result;
import com.sun.entity.VoucherOrder;

/**
 * @author sun
 */
public interface VoucherOrderService extends IService<VoucherOrder> {

    Result seckillVoucher(Long voucherId);

    void createVoucherOrder(VoucherOrder voucherOrder);

}
