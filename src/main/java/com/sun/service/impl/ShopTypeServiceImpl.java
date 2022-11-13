package com.sun.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.sun.dto.Result;
import com.sun.entity.ShopType;
import com.sun.mapper.ShopTypeMapper;
import com.sun.service.ShopTypeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static com.sun.utils.RedisConstants.CACHE_SHOP_TYPE_KEY;
import static com.sun.utils.RedisConstants.TTL_THIRTY;

/**
 * @author sun
 */
@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements ShopTypeService {

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Override
    public Result usingListToQueryByCacheOrderByAscSort() {
        // 1. 从 Redis 中查询；
        List<String> shopTypeJsonList = redisTemplate.opsForList().range(CACHE_SHOP_TYPE_KEY, 0, -1);
        // 2. Redis 中存在则直接返回；
        if (!shopTypeJsonList.isEmpty()) {
            ArrayList<ShopType> shopTypeList = new ArrayList<>();
            for (String str : shopTypeJsonList) {
                ShopType shopType = JSONUtil.toBean(str, ShopType.class);
                shopTypeList.add(shopType);
            }
            return Result.ok(shopTypeList);
        }

        // 3.Redis 中不存在则从数据库中查询；数据库中不存在则报错。
        List<ShopType> shopTypeList = query().orderByAsc("sort").list();
        if (shopTypeList.isEmpty() && shopTypeList == null) {
            return Result.fail("该店铺类型不存在！") ;
        }

        for (ShopType shopType : shopTypeList) {
            String shopTypeJson = JSONUtil.toJsonStr(shopType);
            shopTypeJsonList.add(shopTypeJson);
        }
        // 4. 数据库中存在，将其保存到 Redis 中并返回。
        redisTemplate.opsForList().rightPushAll(CACHE_SHOP_TYPE_KEY, shopTypeJsonList);
        return Result.ok(shopTypeList);
    }

    @Override
    public Result usingStringToQueryByCacheOrderByAscSort() {
        // 1. 从 Redis 中查询
        String shopTypeJson = redisTemplate.opsForValue().get(CACHE_SHOP_TYPE_KEY);

        // 2. Redis 中存在则直接返回
        if (StrUtil.isNotBlank(shopTypeJson)) {
            List<ShopType> shopTypeList = JSONUtil.toList(JSONUtil.parseArray(shopTypeJson), ShopType.class);
            return Result.ok(shopTypeList);
        }

        // 3. Redis 中不存在则从数据库中查询；
        List<ShopType> shopTypeList = query().orderByAsc("sort").list();

        // 4. 数据库中不存在则报错
        if (shopTypeList.isEmpty() && shopTypeList == null) {
            return Result.fail("店铺类型不存在！") ;
        }

        // 5. 数据库中存在，将其保存到 Redis 中并返回
        redisTemplate.opsForValue().set(CACHE_SHOP_TYPE_KEY, JSONUtil.toJsonStr(shopTypeList), TTL_THIRTY, TimeUnit.MINUTES);

        return Result.ok(shopTypeList);
    }
}
