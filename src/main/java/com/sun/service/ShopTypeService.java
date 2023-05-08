package com.sun.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.sun.common.CommonResult;
import com.sun.entity.ShopType;

import java.util.List;

/**
 * @author sun
 */
public interface ShopTypeService extends IService<ShopType> {
    /**
     * 展示商铺类型（缓存）
     */
    CommonResult<List<ShopType>> getShopTypeList();
}
