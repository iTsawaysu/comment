package com.sun.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.sun.dto.Result;
import com.sun.entity.Shop;
import com.sun.mapper.ShopMapper;
import com.sun.service.ShopService;
import com.sun.utils.CacheClient;
import com.sun.utils.RedisData;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.GeoResult;
import org.springframework.data.geo.GeoResults;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.domain.geo.GeoReference;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.sun.utils.RedisConstants.*;
import static com.sun.utils.SystemConstants.DEFAULT_PAGE_SIZE;

/**
 * @author sun
 */
@Service
@Slf4j
@SuppressWarnings("ALL")
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements ShopService {

    @Autowired
    private StringRedisTemplate redisTemplate;

    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    @Resource
    private CacheClient cacheClient;

    @Override
    public Result queryById(Long id) {
        // 缓存穿透
        // Shop shop = dealWithCachePenetrationByNullValue(id);
        // Shop shop = cacheClient.dealWithCachePenetration(CACHE_SHOP_KEY, id, Shop.class, this::getById, TTL_THIRTY, TimeUnit.MINUTES);

        // 缓存击穿(Mutex)
        // Shop shop = dealWithCacheHotspotInvalidByMutex(id);

        // 缓存击穿(Logical Expiration)
        // Shop shop = dealWithCacheHotspotInvalidByLogicalExpiration(id);
        Shop shop = cacheClient.dealWithCacheHotspotInvalid(CACHE_SHOP_KEY, id, Shop.class, this::getById, TTL_TEN, TimeUnit.SECONDS);
        return Result.ok(shop);
    }


    @Transactional
    @Override
    public Result modify(Shop shop) {
        Long id = shop.getId();
        if (id == null) {
            return Result.fail("店铺 ID 不能为空！");
        }

        // 1. 修改数据库
        this.updateById(shop);

        // 2. 删除缓存
        redisTemplate.delete(CACHE_SHOP_KEY + id);
        return Result.ok();
    }

    /**
     * 通过缓存空对象解决 Redis 的缓存穿透问题
     */
    public Shop dealWithCachePenetrationByNullValue(Long id) {
        String key = CACHE_SHOP_KEY + id;

        // 1. 从 Redis 中查询店铺缓存；
        String shopJson = redisTemplate.opsForValue().get(key);

        // 2. 若 Redis 中存在（命中），则将其转换为 Java 对象后返回；
        if (StrUtil.isNotBlank(shopJson)) {
            Shop shop = JSONUtil.toBean(shopJson, Shop.class);
            return shop;
        }

        // 3. 命中缓存后判断是否为空值
        if (ObjectUtil.equals(shopJson, "")) {
            return null;
        }

        // 4. 若 Redis 中不存在（未命中），则根据 id 从数据库中查询；
        Shop shop = getById(id);

        // 5. 若 数据库 中不存在，将空值写入 Redis（缓存空对象）
        if (shop == null) {
            redisTemplate.opsForValue().set(key, "", TTL_TWO, TimeUnit.MINUTES);
            return null;
        }

        // 6. 若 数据库 中存在，则将其返回并存入 Redis 缓存中。
        redisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shop), TTL_THIRTY, TimeUnit.MINUTES);
        return shop;
    }

    /**
     * 通过互斥锁解决 Redis 的缓存击穿问题
     */
    public Shop dealWithCacheHotspotInvalidByMutex(Long id) {
        String key = CACHE_SHOP_KEY + id;

        // 1. 从 Redis 中查询店铺缓存；
        String shopJson = redisTemplate.opsForValue().get(key);

        // 2. 若 Redis 中存在（命中），则将其转换为 Java 对象后返回；
        if (StrUtil.isNotBlank(shopJson)) {
            return JSONUtil.toBean(shopJson, Shop.class);
        }

        // 3. 命中缓存后判断是否为空值
        if (ObjectUtil.equals(shopJson, "")) {
            return null;
        }

        // 4. 若 Redis 中不存在（缓存未命中），实现缓存重建
        // 4.1 获取互斥锁
        String lockKey = LOCK_SHOP_KEY + id;
        Shop shop = null;
        try {
            boolean isLocked = getLock(lockKey);
            // 4.2 获取失败，休眠重试
            if (!isLocked) {
                Thread.sleep(50);
                return dealWithCacheHotspotInvalidByMutex(id);
            }
            // 4.3 获取成功，从数据库中根据 id 查询数据
            shop = getById(id);
            // 4.4 若 数据库 中不存在，将空值写入 Redis（缓存空对象）
            if (shop == null) {
                redisTemplate.opsForValue().set(key, "", TTL_TWO, TimeUnit.MINUTES);
                return null;
            }
            // 4.5 若 数据库 中存在，则将其返回并存入 Redis 缓存中。
            redisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shop), TTL_THIRTY, TimeUnit.MINUTES);
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            // 5. 释放互斥锁
            releaseLock(lockKey);
        }
        return shop;
    }


    /**
     * 通过逻辑过期解决 Redis 的缓存击穿问题
     */
    public Shop dealWithCacheHotspotInvalidByLogicalExpiration(Long id) {
        String key = CACHE_SHOP_KEY + id;

        // 1. 从 Redis 中查询店铺缓存；
        String shopJson = redisTemplate.opsForValue().get(key);

        // 2. 未命中
        if (StrUtil.isBlank(shopJson)) {
            return null;
        }

        // 3. 命中（先将 JSON 反序列化为 对象）
        RedisData redisData = JSONUtil.toBean(shopJson, RedisData.class);
        Shop shop = JSONUtil.toBean((JSONObject) redisData.getData(), Shop.class);
        LocalDateTime expireTime = redisData.getExpireTime();

        // 4. 判断是否过期：未过期，直接返回店铺信息；过期，需要缓存重建。
        if (expireTime.isAfter(LocalDateTime.now())) {
            return shop;
        }

        // 5. 缓存重建（未获取到互斥锁，直接返回店铺信息）
        String lockKey = LOCK_SHOP_KEY + id;
        boolean isLocked = getLock(lockKey);

        // 5.1 获取到互斥锁
        // 开启独立线程：根据 id 查询数据库，将数据写入到 Redis，并且设置逻辑过期时间。
        // 此处必须进行 DoubleCheck：多线程并发下，若线程1 和 线程2都到达这一步，线程1 拿到锁，进行操作后释放锁；线程2 拿到锁后会再次进行查询数据库、写入到 Redis 中等操作。
        shopJson = redisTemplate.opsForValue().get(key);
        if (StrUtil.isBlank(shopJson)) {
            return null;
        }

        if (isLocked) {
            CACHE_REBUILD_EXECUTOR.submit(() -> {
                try {
                    // 重建缓存
                    this.saveHotspot2Redis(id, 30L);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    // 释放互斥锁
                    releaseLock(lockKey);
                }
            });
        }

        // 5.2 返回店铺信息
        return shop;
    }

    /**
     * 获取互斥锁
     */
    private boolean getLock(String key) {
        Boolean flag = redisTemplate.opsForValue().setIfAbsent(key, "1", TTL_TEN, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);
    }

    /**
     * 释放互斥锁
     */
    private void releaseLock(String key) {
        redisTemplate.delete(key);
    }

    /**
     * 对热点数据进行预热（提前存储到 Redis 中）
     */
    public void saveHotspot2Redis(Long id, Long expireSeconds) throws InterruptedException {
        // 1. 查询店铺信息
        Shop shop = getById(id);
        Thread.sleep(200);
        // 2. 封装逻辑过期时间
        RedisData redisData = new RedisData();
        redisData.setData(shop);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(expireSeconds));
        // 3. 写入 Redis
        redisTemplate.opsForValue().set(CACHE_SHOP_KEY + id, JSONUtil.toJsonStr(redisData));
    }

    @Override
    public Result queryShopByTypeId(Integer typeId, Integer current, Double x, Double y) {
        // 1. 判断是否需要根据坐标查询
        if (ObjectUtil.isNull(x) || ObjectUtil.isNull(y)) {
            return Result.ok(lambdaQuery().eq(Shop::getTypeId, typeId).page(new Page<>(current, DEFAULT_PAGE_SIZE)).getRecords());
        }

        // 2. 计算分页参数
        int start = (current - 1) * DEFAULT_PAGE_SIZE;
        int end = current * DEFAULT_PAGE_SIZE;

        // 3. 查询 Redis，按照距离排序 --> GEOSEARCH key BYLONLAT x y BYRADIUS 5000 mi WITHDISTANCE
        String key = SHOP_GEO_KEY + typeId;
        GeoResults<RedisGeoCommands.GeoLocation<String>> geoResults = redisTemplate.opsForGeo().search(
                        key,
                        GeoReference.fromCoordinate(x, y),
                        new Distance(5000),
                        RedisGeoCommands.GeoSearchCommandArgs.newGeoSearchArgs().includeDistance().limit(end));
        if (ObjectUtil.isNull(geoResults)) {
            return Result.ok(Collections.emptyList());
        }

        // 4. 解析出 ID，根据 ID 查询商店
        List<GeoResult<RedisGeoCommands.GeoLocation<String>>> content = geoResults.getContent();
        if (content.size() <= start) {
            return Result.ok(Collections.emptyList());
        }
        List<Long> shopIdList = new ArrayList<>(content.size());
        Map<String, Distance> distanceMap = new HashMap<>(content.size());
        content.stream().skip(start).forEach(result -> {
            String shopIdStr = result.getContent().getName();
            shopIdList.add(Long.valueOf(shopIdStr));
            Distance distance = result.getDistance();
            distanceMap.put(shopIdStr, distance);
        });

        // 5. 根据 shopId 查询 Shop
        String shopIdStrWithComma = StrUtil.join(", ", shopIdList);
        List<Shop> shopList = lambdaQuery().in(Shop::getId, shopIdList).last("ORDER BY FIELD(id, " + shopIdStrWithComma + ")").list();
        for (Shop shop : shopList) {
            shop.setDistance(distanceMap.get(shop.getId().toString()).getValue());
        }

        // 6. Return ShopList
        return Result.ok(shopList);
    }
}
