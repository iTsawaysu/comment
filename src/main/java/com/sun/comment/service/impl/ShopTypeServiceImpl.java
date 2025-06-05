package com.sun.comment.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.sun.comment.common.Result;
import com.sun.comment.entity.ShopType;
import com.sun.comment.mapper.ShopTypeMapper;
import com.sun.comment.service.ShopTypeService;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * @author sun
 */
@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements ShopTypeService {
    /**
     * 展示商铺类型（缓存）
     */
    @Override
    public Result<List<ShopType>> getShopTypeList() {
        return null;
    }
}
