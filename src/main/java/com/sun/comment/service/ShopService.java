package com.sun.comment.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.sun.comment.common.Result;
import com.sun.comment.entity.Shop;

import java.util.List;

/**
 * @author sun
 */
public interface ShopService extends IService<Shop> {

    /**
     * 根据 id 查询商铺信息（使用缓存并解决缓存穿透、缓存雪崩、缓存击穿问题）
     */
    Result<Shop> getShopById(Long id);

    /**
     * 更新商铺信息（先操作数据库，后删除缓存）
     */
    Result<String> update(Shop shop);

    /**
     * 根据店铺类型分页查询店铺信息（按照距离排序）
     *
     * @param typeId  店铺类型
     * @param current 当前页码
     * @param x       经度
     * @param y       纬度
     * @return 店铺列表
     */
    Result<List<Shop>> getShopsByTypeOrderByDistance(Integer typeId, Integer current, Double x, Double y);
}
