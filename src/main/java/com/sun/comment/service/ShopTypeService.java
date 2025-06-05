package com.sun.comment.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.sun.comment.common.Result;
import com.sun.comment.entity.ShopType;

import java.util.List;

/**
 * @author sun
 */
public interface ShopTypeService extends IService<ShopType> {
    /**
     * 展示商铺类型（缓存）
     */
    Result<List<ShopType>> getShopTypeList();
}
