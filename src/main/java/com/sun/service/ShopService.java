package com.sun.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.sun.dto.Result;
import com.sun.entity.Shop;

/**
 * @author sun
 */
public interface ShopService extends IService<Shop> {

    Result queryById(Long id);

    Result modify(Shop shop);

    Result queryShopByTypeId(Integer typeId, Integer current, Double x, Double y);
}
