package com.sun.comment.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.thread.ThreadUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.sun.comment.common.CommonResult;
import com.sun.comment.common.ErrorCode;
import com.sun.comment.common.SystemConstants;
import com.sun.comment.common.exception.ThrowUtils;
import com.sun.comment.entity.Shop;
import com.sun.comment.entity.dto.RedisData;
import com.sun.comment.mapper.ShopMapper;
import com.sun.comment.service.ShopService;
import com.sun.comment.utils.CacheClient;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.GeoResult;
import org.springframework.data.geo.GeoResults;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.domain.geo.GeoReference;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.sun.comment.common.RedisConstants.*;

/**
 * @author sun
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements ShopService {
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private CacheClient cacheClient;

    private static final ExecutorService pool = Executors.newFixedThreadPool(1);

    /**
     * 获取互斥锁
     */
    public boolean tryLock(Long id) {
        return stringRedisTemplate.opsForValue().setIfAbsent(LOCK_SHOP_KEY + id, "1", TTL_TWO, TimeUnit.MINUTES);
    }

    /**
     * 释放互斥锁
     */
    public void unlock(Long id) {
        stringRedisTemplate.delete(LOCK_SHOP_KEY + id);
    }

    /**
     * 缓存预热（将热点数据提前存储到 Redis 中）
     */
    public void cacheWarmup(Long id, long expireSeconds) {
        Shop shop = this.getById(id);
        ThrowUtils.throwIf(shop == null, ErrorCode.NOT_FOUND_ERROR, "id 为" + id + "的 Shop 不存在");
        RedisData redisData = new RedisData();
        redisData.setData(shop);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(expireSeconds));
        // 模拟缓存重建延迟，让一部分线程先执行完毕，在此期间会短暂的不一致
        ThreadUtil.sleep(200);
        stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id, JSONUtil.toJsonStr(redisData));
    }

    /**
     * 根据 id 查询商铺信息（使用缓存并解决缓存穿透、缓存雪崩、缓存击穿问题）
     */
    @Override
    public CommonResult<Shop> getShopById(Long id) {
        // Shop shop = cacheClient.setCachingEmpty(id, Shop.class, this::getById, TTL_TWO, TimeUnit.HOURS);
        // Shop shop = cacheClient.setSynchronized(id, Shop.class, this::getById, TTL_TWO, TimeUnit.HOURS);
        Shop shop = cacheClient.setSetNx(id, Shop.class, this::getById, TTL_TWO, TimeUnit.HOURS);
        // Shop shop = cacheClient.setLogicalExpiration(id, Shop.class, this::getById, TTL_TWO, TimeUnit.HOURS);
        return CommonResult.success(shop);
    }

    public CommonResult<Shop> getShopByIdLogicExpiration(Long id) {
        ThrowUtils.throwIf(id == null, ErrorCode.PARAMS_ERROR);
        String shopKey = CACHE_SHOP_KEY + id;
        String lockKey = LOCK_SHOP_KEY + id;
        // 1. 先从 Redis 中查询数据，未命中则直接返回
        String json = stringRedisTemplate.opsForValue().get(shopKey);
        if (StrUtil.isBlank(json)) {
            return CommonResult.success(null);
        }
        // 2. 判断是否过期，未过期则直接返回
        RedisData redisData = JSONUtil.toBean(json, RedisData.class);
        LocalDateTime expireTime = redisData.getExpireTime();
        JSONObject jsonObject = (JSONObject) redisData.getData();
        Shop shop = JSONUtil.toBean(jsonObject, Shop.class);
        if (LocalDateTime.now().isBefore(expireTime)) {
            return CommonResult.success(shop);
        }
        // 3. 未获取到锁，直接返回之前的 Shop
        boolean tryLock = tryLock(id);
        if (!tryLock) {
            return CommonResult.success(shop);
        }
        // 4. 获取到锁：开启一个新的线程后返回旧数据。（这个线程负责查询数据库、重建缓存）
        // 此处无需 DoubleCheck，因为未获取到锁直接返回旧数据，能保证只有一个线程执行到此处
        pool.execute(() -> {
            try {
                cacheWarmup(id, 600L);
            } finally {
                unlock(id);
            }
        });
        return CommonResult.success(shop);
    }

    public CommonResult<Shop> getShopByIdSetnx(Long id) {
        ThrowUtils.throwIf(id == null, ErrorCode.PARAMS_ERROR);
        String shopKey = CACHE_SHOP_KEY + id;  // CACHE_SHOP_KEY = "cache:shop:"
        // 先从 Redis 中查询数据，存在则将其转换为 Java 对象后返回
        String json = stringRedisTemplate.opsForValue().get(shopKey);
        if (StrUtil.isNotBlank(json)) {
            return CommonResult.success(JSONUtil.toBean(json, Shop.class));
        }
        // 命中空值，直接返回
        if ("".equals(json)) {
            return CommonResult.success(null);
        }
        // 缓存击穿：从 Redis 中未查询到数据，防止大量数据打到数据库，通过 setnx 限制重建缓存的线程数量
        Shop shop = null;
        try {
            boolean tryLock = tryLock(id);
            // 未获取到锁：等待一段时间后，通过递归调用进行重试。
            if (!tryLock) {
                ThreadUtil.sleep(100);
                return this.getShopById(id);
            }
            // 获取到锁：查询数据库、缓存重建。
            // 再次查询 Redis：若多个线程执行到同步代码块，某个线程拿到锁查询数据库并重建缓存后，其他拿到锁进来的线程直接查询缓存后返回，避免重复查询数据库并重建缓存
            json = stringRedisTemplate.opsForValue().get(shopKey);
            if (StrUtil.isNotBlank(json)) {
                return CommonResult.success(JSONUtil.toBean(json, Shop.class));
            }
            shop = this.getById(id);
            // 缓存穿透：Redis 和数据库中都无法查询到，缓存一个空值到 Redis 中并设置 TTL 时间。
            if (shop == null) {
                stringRedisTemplate.opsForValue().set(shopKey, "", TTL_TWO, TimeUnit.MINUTES);
                return CommonResult.success(null);
            }
            // 重建缓存并模拟延迟
            ThreadUtil.sleep(100);
            stringRedisTemplate.opsForValue().set(shopKey, JSONUtil.toJsonStr(shop), TTL_TWO, TimeUnit.HOURS);
        } finally {
            unlock(id);
        }
        return CommonResult.success(shop);
    }

    /**
     * 更新商铺信息（先操作数据库，后删除缓存）
     */
    @Override
    public CommonResult<String> update(Shop shop) {
        ThrowUtils.throwIf(shop == null || shop.getId() == null, ErrorCode.PARAMS_ERROR);
        boolean isUpdated = this.updateById(shop);
        ThrowUtils.throwIf(!isUpdated, ErrorCode.OPERATION_ERROR, "数据库更新失败");
        boolean isDeleted = stringRedisTemplate.delete(CACHE_SHOP_KEY + shop.getId());
        ThrowUtils.throwIf(!isDeleted, ErrorCode.OPERATION_ERROR, "数据库更新成功，Redis 删除失败");
        return CommonResult.success("更新成功");
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
    public CommonResult<List<Shop>> getShopsByTypeOrderByDistance(Integer typeId, Integer current, Double x, Double y) {
        // 判断是否需要根据坐标排序（不需要则直接从数据库中查询）
        if (x == null || y == null) {
            Page<Shop> page = this.lambdaQuery().eq(Shop::getTypeId, typeId).page(new Page<>(current, SystemConstants.DEFAULT_PAGE_SIZE));
            ThrowUtils.throwIf(page == null, ErrorCode.NOT_FOUND_ERROR);
            return CommonResult.success(page.getRecords());
        }
        // 计算分页参数
        int start = (current - 1) * SystemConstants.DEFAULT_PAGE_SIZE;
        int end = current * SystemConstants.DEFAULT_PAGE_SIZE;
        // 查询 Redis，获取 shopId 和 distance（geosearch key fromcoord x y byradius 5000 m withdistance limit 0 5）
        // 位于给定坐标周围 5 公里范围内，返回的结果中包含与搜索中心点的距离信息
        String key = SHOP_GEO_KEY + typeId;
        GeoResults<RedisGeoCommands.GeoLocation<String>> results = stringRedisTemplate.opsForGeo().search(
                key,
                GeoReference.fromCoordinate(x, y),
                new Distance(5000),
                RedisGeoCommands.GeoSearchCommandArgs.newGeoSearchArgs().includeDistance().limit(end)
        );
        ThrowUtils.throwIf(CollUtil.isEmpty(results), ErrorCode.NOT_FOUND_ERROR);
        // 解析出 shopId
        List<GeoResult<RedisGeoCommands.GeoLocation<String>>> content = results.getContent();
        if (content.size() < start) {
            return CommonResult.success(Collections.emptyList());
        }
        List<Long> shopIdList = new ArrayList<>(content.size());
        Map<String, Distance> distanceMap = new HashMap<>(content.size());
        content.stream().skip(start).forEach(result -> {
            String shopId = result.getContent().getName();
            shopIdList.add(Long.valueOf(shopId));
            Distance distance = result.getDistance();
            distanceMap.put(shopId, distance);
        });
        // 根据 shopId 查询 Shop
        String shopIdStr = StrUtil.join(", ", shopIdList);
        List<Shop> shopList = lambdaQuery().in(Shop::getId, shopIdList).last("ORDER BY FIELD(id, " + shopIdStr + ")").list();
        for (Shop shop : shopList) {
            Distance distance = distanceMap.get(shop.getId().toString());
            if (distance != null) {
                shop.setDistance(distance.getValue());
            }
        }
        return CommonResult.success(shopList);
    }
}
