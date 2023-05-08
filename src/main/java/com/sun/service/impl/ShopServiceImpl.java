package com.sun.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.map.MapUtil;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.sun.common.CommonResult;
import com.sun.common.ErrorCode;
import com.sun.dto.RedisData;
import com.sun.entity.Shop;
import com.sun.exception.BusinessException;
import com.sun.exception.ThrowUtils;
import com.sun.mapper.ShopMapper;
import com.sun.service.ShopService;
import com.sun.utils.CacheClient;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
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

import static com.sun.common.RedisConstants.*;
import static com.sun.common.SystemConstants.DEFAULT_PAGE_SIZE;

/**
 * @author sun
 */
@Slf4j
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements ShopService {

    @Resource
    private CacheClient cacheClient;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    private static final ExecutorService ES = Executors.newFixedThreadPool(10);

    /**
     * 获取互斥锁
     */
    public boolean tryLock(String key) {
        Boolean result = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", TTL_TWO, TimeUnit.SECONDS);
        return Boolean.TRUE.equals(result);
    }

    /**
     * 释放互斥锁
     */
    public void unlock(String key) {
        stringRedisTemplate.delete(key);
    }

    /**
     * 根据 id 查询商铺信息（添加缓存并解决缓存穿透、缓存雪崩、缓存击穿问题）
     */
    @Override
    public CommonResult<Shop> getShopById(Long id) {
        // 缓存穿透
        // Shop shop = cacheClient.setWithCachePenetration(CACHE_SHOP_KEY, id, Shop.class, this::getById, TTL_TWO, TimeUnit.MINUTES);

        // 缓存击穿：synchronized
        // Shop shop = cacheClient.setWithCacheBreakdown4Synchronized(CACHE_SHOP_KEY, id, Shop.class, this::getById, TTL_TWO, TimeUnit.HOURS);

        // 缓存击穿：setnx
        // Shop shop = cacheClient.setWithCacheBreakdown4SetNx(CACHE_SHOP_KEY, id, Shop.class, this::getById, TTL_TWO, TimeUnit.HOURS);

        // 缓存击穿：逻辑过期
        Shop shop = cacheClient.setWithCacheBreakdown4LogicalExpiration(CACHE_SHOP_KEY, id, Shop.class, this::getById, TTL_TWO, TimeUnit.HOURS);
        return CommonResult.success(shop);
    }

    /**
     * 缓存预热（将热点数据提前存储到 Redis 中）
     */
    public void saveHotDataIn2Redis(Long id, Long expireSeconds) throws InterruptedException {
        Shop shop = this.getById(id);
        ThrowUtils.throwIf(shop == null, ErrorCode.NOT_FOUND_ERROR, "该数据不存在");
        // 模拟缓存重建延迟，让一部分线程先执行完毕，在此期间会短暂的不一致
        Thread.sleep(200);
        RedisData redisData = new RedisData();
        redisData.setData(shop);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(expireSeconds));
        stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id, JSONUtil.toJsonStr(redisData));
    }

    /**
     * 缓存击穿：逻辑过期
     */
    @SneakyThrows
    public CommonResult<Shop> cacheBreakdown4LogicalExpiration(Long id) {
        ThrowUtils.throwIf(id == null, ErrorCode.PARAMS_ERROR);
        String shopKey = CACHE_SHOP_KEY + id;
        String lockKey = LOCK_SHOP_KEY + id;

        // 1. 先从 Redis 中查询数据，未命中则直接返回
        String redisDataJson = stringRedisTemplate.opsForValue().get(shopKey);
        if (StringUtils.isBlank(redisDataJson)) {
            return CommonResult.success(null);
        }

        // 2. 判断是否过期，未过期则直接返回
        RedisData redisData = JSONUtil.toBean(redisDataJson, RedisData.class);
        JSONObject jsonObject = (JSONObject) redisData.getData();
        Shop shop = JSONUtil.toBean(jsonObject, Shop.class);
        LocalDateTime expireTime = redisData.getExpireTime();
        if (expireTime.isAfter(LocalDateTime.now())) {
            return CommonResult.success(shop);
        }

        // 3. 未获取到锁直接返回
        boolean tryLock = tryLock(lockKey);
        if (BooleanUtil.isFalse(tryLock)) {
            return CommonResult.success(shop);
        }

        // 4. 获取到锁：开启一个新的线程后返回旧数据。（这个线程负责查询数据库、重建缓存）
        // 此处无需 DoubleCheck，因为未获取到锁直接返回旧数据，能保证只有一个线程执行到此处
        ES.submit(() -> {
            try {
                // 查询数据库、重建缓存
                this.saveHotDataIn2Redis(id, 3600 * 24L);
            } catch (Exception e) {
                log.error(e.getMessage());
            } finally {
                unlock(lockKey);
            }
        });
        return CommonResult.success(shop);
    }

    /**
     * 缓存击穿：setnx
     */
    @SneakyThrows
    public CommonResult<Shop> cacheBreakdown4SetNX(Long id) {
        ThrowUtils.throwIf(id == null, ErrorCode.PARAMS_ERROR);
        String shopKey = CACHE_SHOP_KEY + id;
        String lockKey = LOCK_SHOP_KEY + id;

        // 1. 先从 Redis 中查询数据，存在则将其转换为 Java 对象后返回
        String shopJsonInRedis = stringRedisTemplate.opsForValue().get(shopKey);
        if (StringUtils.isNotBlank(shopJsonInRedis)) {
            return CommonResult.success(JSONUtil.toBean(shopJsonInRedis, Shop.class));
        }
        // 命中空值
        if (shopJsonInRedis != null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "该商铺不存在");
        }

        // 2. 从 Redis 中未查询到数据，尝试获取锁后从数据库中查询。
        Shop shop = new Shop();
        boolean tryLock = tryLock(lockKey);
        try {
            // 2.1 未获取到锁则等待一段时间后重试（通过递归调用重试）
            if (BooleanUtil.isFalse(tryLock)) {
                Thread.sleep(50);
                this.getShopById(id);
            }

            // 2.2 获取到锁：查询数据库、缓存重建。
            if (tryLock) {
                // 3. 再次查询 Redis：若多个线程执行到获取锁处，某个线程拿到锁查询数据库并重建缓存后，其他拿到锁进来的线程直接查询缓存后返回，避免重复查询数据库并重建缓存。
                shopJsonInRedis = stringRedisTemplate.opsForValue().get(shopKey);
                if (StringUtils.isNotBlank(shopJsonInRedis)) {
                    return CommonResult.success(JSONUtil.toBean(shopJsonInRedis, Shop.class));
                }

                // 4. 查询数据库，缓存空值避免缓存穿透，重建缓存。
                shop = this.getById(id);
                if (shop == null) {
                    stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id, "", TTL_TWO, TimeUnit.MINUTES);
                    throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "该商铺不存在");
                }
                // 模拟缓存重建延迟
                Thread.sleep(100);
                stringRedisTemplate.opsForValue().set(shopKey, JSONUtil.toJsonStr(shop), TTL_TWO, TimeUnit.HOURS);
            }
        } finally {
            // 5. 释放锁
            unlock(lockKey);
        }
        return CommonResult.success(shop);
    }

    /**
     * 缓存击穿：synchronized
     */
    @SneakyThrows
    public CommonResult<Shop> cacheBreakdown4Synchronized(Long id) {
        ThrowUtils.throwIf(id == null, ErrorCode.PARAMS_ERROR);
        String shopKey = CACHE_SHOP_KEY + id;

        // 1. 先从 Redis 中查询数据，存在则将其转换为 Java 对象后返回
        String shopJsonInRedis = stringRedisTemplate.opsForValue().get(shopKey);
        if (StringUtils.isNotBlank(shopJsonInRedis)) {
            return CommonResult.success(JSONUtil.toBean(shopJsonInRedis, Shop.class));
        }
        // 命中空值
        if (shopJsonInRedis != null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "该商铺不存在");
        }

        // 2. 从 Redis 中未查询到数据，则从数据库中查询。（synchronized）
        Shop shop = new Shop();
        synchronized (ShopServiceImpl.class) {
            // 3. 再次查询 Redis：若多个线程执行到同步代码块，某个线程拿到锁查询数据库并重建缓存后，其他拿到锁进来的线程直接查询缓存后返回，避免重复查询数据库并重建缓存。
            shopJsonInRedis = stringRedisTemplate.opsForValue().get(shopKey);
            if (StringUtils.isNotBlank(shopJsonInRedis)) {
                return CommonResult.success(JSONUtil.toBean(shopJsonInRedis, Shop.class));
            }

            // 4. 查询数据库，缓存空值避免缓存穿透，重建缓存。
            shop = this.getById(id);
            if (shop == null) {
                stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id, "", TTL_TWO, TimeUnit.MINUTES);
                throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "该商铺不存在");
            }
            // 模拟缓存重建延迟
            Thread.sleep(100);
            stringRedisTemplate.opsForValue().set(shopKey, JSONUtil.toJsonStr(shop), TTL_TWO, TimeUnit.HOURS);
        }
        return CommonResult.success(shop);
    }

    /**
     * <p>缓存穿透：缓存空值。</p>
     * <p>缓存雪崩：为 Key 的 TTL 设置随机值。TTL_THIRTY + RandomUtil.randomInt(30)</p>
     */
    public CommonResult<Shop> cachePenetration(Long id) {
        ThrowUtils.throwIf(id == null, ErrorCode.PARAMS_ERROR);
        String shopKey = CACHE_SHOP_KEY + id;

        // 1. 先从 Redis 中查询数据，存在则将其转换为 Java 对象后返回
        String shopJsonInRedis = stringRedisTemplate.opsForValue().get(shopKey);
        if (StringUtils.isNotBlank(shopJsonInRedis)) {
            return CommonResult.success(JSONUtil.toBean(shopJsonInRedis, Shop.class));
        }
        // 命中空值
        if (shopJsonInRedis != null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "该商铺不存在");
        }

        // 2. 从 Redis 中未查询到数据，则从数据库中查询
        Shop shop = this.getById(id);

        // 若数据中也查询不到，则缓存空值后返回提示信息
        if (shop == null) {
            stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id, "", TTL_TWO, TimeUnit.MINUTES);
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "该商铺不存在");
        }

        // 3. 将从数据库中查询到的数据存入 Redis 后返回
        stringRedisTemplate.opsForValue().set(shopKey, JSONUtil.toJsonStr(shop), TTL_TWO, TimeUnit.HOURS);
        return CommonResult.success(shop);
    }

    /**
     * 添加缓存 String
     */
    public CommonResult<Shop> addCacheString(Long id) {
        ThrowUtils.throwIf(id == null, ErrorCode.PARAMS_ERROR);
        String shopKey = CACHE_SHOP_KEY + id;

        // 1. 先从 Redis 中查询数据，存在则将其转换为 Java 对象后返回
        String shopJsonInRedis = stringRedisTemplate.opsForValue().get(shopKey);
        if (StringUtils.isNotBlank(shopJsonInRedis)) {
            return CommonResult.success(JSONUtil.toBean(shopJsonInRedis, Shop.class));
        }

        // 2. 从 Redis 中未查询到数据，则从数据库中查询
        Shop shop = this.getById(id);
        ThrowUtils.throwIf(shop == null, ErrorCode.NOT_FOUND_ERROR, "该商铺不存在");

        // 3. 将从数据库中查询到的数据存入 Redis 后返回
        stringRedisTemplate.opsForValue().set(shopKey, JSONUtil.toJsonStr(shop), TTL_TWO, TimeUnit.HOURS);
        return CommonResult.success(shop);
    }

    /**
     * 添加缓存 Hash
     */
    public CommonResult<Shop> addCacheHash(Long id) {
        ThrowUtils.throwIf(id == null, ErrorCode.PARAMS_ERROR);
        String shopKey = CACHE_SHOP_KEY + id;   // CACHE_SHOP_KEY = "cache:shop:"

        // 1. 先从 Redis 中查询数据，存在则将其转换为 Java 对象后返回
        Map<Object, Object> map4ShopInRedis = stringRedisTemplate.opsForHash().entries(shopKey);
        Shop shop = new Shop();
        if (MapUtil.isNotEmpty(map4ShopInRedis)) {
            shop = BeanUtil.fillBeanWithMap(map4ShopInRedis, shop, false);
            return CommonResult.success(shop);
        }

        // 2. 从 Redis 中未查询到数据，则从数据库中查询
        shop = this.getById(id);
        ThrowUtils.throwIf(shop == null, ErrorCode.NOT_FOUND_ERROR, "该商铺不存在");

        // 3. 将从数据库中查询到的数据存入 Redis 后返回
        Map<String, Object> map4Shop = BeanUtil.beanToMap(shop, new HashMap<>(32),
                CopyOptions.create()
                        .ignoreNullValue()
                        .setFieldValueEditor((fieldName, fieldValue) -> {
                            if (fieldValue == null) {
                                return "";
                            }
                            return fieldValue.toString();
                        })
        );
        stringRedisTemplate.opsForHash().putAll(shopKey, map4Shop);
        stringRedisTemplate.expire(shopKey, TTL_TWO, TimeUnit.HOURS);
        return CommonResult.success(shop);
    }

    /**
     * 更新商铺信息（先操作数据库，后删除缓存）
     */
    @Transactional
    @Override
    public CommonResult<String> update(Shop shop) {
        ThrowUtils.throwIf(shop == null, ErrorCode.PARAMS_ERROR);
        ThrowUtils.throwIf(shop.getId() == null, ErrorCode.PARAMS_ERROR, "商铺 id 不能为空");

        boolean result = updateById(shop);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR, "数据库更新失败");

        result = stringRedisTemplate.delete(CACHE_SHOP_KEY + shop.getId());
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR, "数据库更新成功，但是 Redis 删除失败");

        return CommonResult.success("操作成功");
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
        // 1. 判断是否需要根据坐标排序（不需要则直接从数据库中查询）
        if (x == null || y == null) {
            Page<Shop> shopPage = this.lambdaQuery()
                    .eq(Shop::getTypeId, typeId)
                    .page(new Page<>(current, DEFAULT_PAGE_SIZE));
            ThrowUtils.throwIf(shopPage == null, ErrorCode.NOT_FOUND_ERROR);
            return CommonResult.success(shopPage.getRecords());
        }

        // 2. 计算分页参数
        int start = (current - 1) *DEFAULT_PAGE_SIZE;
        int end = current * DEFAULT_PAGE_SIZE;

        // 3. GEOSEARCH key BYLONLAT x y BYRADIUS 5000 mi WITHDISTANCE（查询 Redis，获取 shopId 和 distance）
        String key = SHOP_GEO_KEY + typeId;
        GeoResults<RedisGeoCommands.GeoLocation<String>> geoResults = stringRedisTemplate.opsForGeo().search(
                key,
                GeoReference.fromCoordinate(x, y),
                new Distance(5000),
                RedisGeoCommands.GeoSearchCommandArgs.newGeoSearchArgs().includeDistance().limit(end)
        );
        ThrowUtils.throwIf(CollectionUtil.isEmpty(geoResults), ErrorCode.NOT_FOUND_ERROR);

        // 4. 解析出 shopId
        List<GeoResult<RedisGeoCommands.GeoLocation<String>>> content = geoResults.getContent();
        if (content.size() < start) {
            return CommonResult.success(Collections.emptyList());
        }
        List<Long> shopIdList = new ArrayList<>(content.size());
        Map<String, Distance> distanceMap = new HashMap<>(content.size());
        // 截取 start ～ end 部分
        content.stream().skip(start).forEach(result -> {
            String shopId = result.getContent().getName();
            shopIdList.add(Long.valueOf(shopId));
            Distance distance = result.getDistance();
            distanceMap.put(shopId, distance);
        });

        // 5. 根据 shopId 查询 Shop
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
