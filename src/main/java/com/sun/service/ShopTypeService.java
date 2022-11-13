package com.sun.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.sun.dto.Result;
import com.sun.entity.ShopType;

/**
 * @author sun
 */
public interface ShopTypeService extends IService<ShopType> {

    Result usingListToQueryByCacheOrderByAscSort();

    Result usingStringToQueryByCacheOrderByAscSort();
}
