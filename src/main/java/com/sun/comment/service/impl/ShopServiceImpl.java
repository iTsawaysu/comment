package com.sun.comment.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.sun.comment.common.Result;
import com.sun.comment.entity.Shop;
import com.sun.comment.mapper.ShopMapper;
import com.sun.comment.service.ShopService;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * @author sun
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements ShopService {

    /**
     * 根据 id 查询商铺信息（使用缓存并解决缓存穿透、缓存雪崩、缓存击穿问题）
     */
    @Override
    public Result<Shop> getShopById(Long id) {
        return null;
    }

    /**
     * 更新商铺信息（先操作数据库，后删除缓存）
     */
    @Override
    public Result<String> update(Shop shop) {
        return null;
    }

    /**
     * 根据店铺类型分页查询店铺信息（按照距离排序）
     *
     * @param typeId  店铺类型
     * @param current 当前页码
     * @param x       经度
     * @param y       纬度
     * @return 店铺列表
     */
    @Override
    public Result<List<Shop>> getShopsByTypeOrderByDistance(Integer typeId, Integer current, Double x, Double y) {
        return null;
    }
}
