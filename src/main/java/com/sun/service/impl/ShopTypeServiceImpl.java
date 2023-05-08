package com.sun.service.impl;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.sun.common.CommonResult;
import com.sun.common.ErrorCode;
import com.sun.entity.ShopType;
import com.sun.exception.ThrowUtils;
import com.sun.mapper.ShopTypeMapper;
import com.sun.service.ShopTypeService;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.sun.common.RedisConstants.CACHE_SHOP_TYPE_KEY;
import static com.sun.common.RedisConstants.TTL_TWO;

/**
 * @author sun
 */
@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements ShopTypeService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 展示商铺类型（缓存）
     */
    @Override
    public CommonResult<List<ShopType>> getShopTypeList() {
        // 1. 先从 Redis 中查询数据，存在则将其转换为 Java 对象后返回
        String shopTypeJsonInRedis = stringRedisTemplate.opsForValue().get(CACHE_SHOP_TYPE_KEY);
        List<ShopType> shopTypeList = null;
        if (StringUtils.isNotBlank(shopTypeJsonInRedis)) {
            shopTypeList = JSONUtil.toList(JSONUtil.parseArray(shopTypeJsonInRedis), ShopType.class);
            return CommonResult.success(shopTypeList);
        }

        // 2. 从 Redis 中未查询到数据，则从数据库中查询
        shopTypeList = this.list();
        ThrowUtils.throwIf(CollectionUtil.isEmpty(shopTypeList), ErrorCode.NOT_FOUND_ERROR, "商铺类型列表不存在");

        // 3. 将从数据库中查询到的数据存入 Redis 后返回
        stringRedisTemplate.opsForValue().set(CACHE_SHOP_TYPE_KEY, JSONUtil.toJsonStr(shopTypeList), TTL_TWO, TimeUnit.HOURS);
        return CommonResult.success(shopTypeList);
    }

    public CommonResult<List<ShopType>> addCacheWithList() {
        // 1. 先从 Redis 中查询数据，存在则将其转换为 Java 对象后返回
        List<String> shopTypeJsonList = stringRedisTemplate.opsForList().range(CACHE_SHOP_TYPE_KEY, 0, -1);
        List<ShopType> shopTypeList = new ArrayList<>();
        if (CollectionUtil.isNotEmpty(shopTypeJsonList)) {
            for (String shopTypeJsonInRedis : shopTypeJsonList) {
                shopTypeList.add(JSONUtil.toBean(shopTypeJsonInRedis, ShopType.class));
            }
            return CommonResult.success(shopTypeList);
        }

        // 2. 从 Redis 中未查询到数据，则从数据库中查询
        shopTypeList = this.lambdaQuery().orderByAsc(ShopType::getSort).list();
        ThrowUtils.throwIf(CollectionUtil.isEmpty(shopTypeList), ErrorCode.NOT_FOUND_ERROR, "商铺类型列表不存在");

        // 3. 将从数据库中查询到的数据存入 Redis 后返回
        List<String> shopTypeListJson = shopTypeList.stream()
                .map(shopType -> JSONUtil.toJsonStr(shopType))
                .collect(Collectors.toList());
        stringRedisTemplate.opsForList().leftPushAll(CACHE_SHOP_TYPE_KEY, shopTypeListJson);
        stringRedisTemplate.expire(CACHE_SHOP_TYPE_KEY, TTL_TWO, TimeUnit.HOURS);
        return CommonResult.success(shopTypeList);
    }
}
