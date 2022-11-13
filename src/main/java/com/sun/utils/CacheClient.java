package com.sun.utils;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static com.sun.utils.RedisConstants.*;

/**
 * @Author Sun Jianda
 * @Date 2022/10/13
 */

@Component
@Slf4j
public class CacheClient {
    private final StringRedisTemplate redisTemplate;

    public CacheClient(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * 将任意 Java 对象序列化为 JSON 存储在 String 类型的 Key 中，并且可以设置 TTL 过期时间
     */
    public void set(String key, Object value, Long time, TimeUnit timeUnit) {
        redisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value), time, timeUnit);
    }

    /**
     * 将任意 Java 对象序列化为 JSON 存储在 String 类型的 Key 中，并且可以设置逻辑过期时间，用于处理缓存击穿
     */
    public void setWithLogicalExpiration(String key, Object value, Long time, TimeUnit timeUnit) {
        RedisData redisData = new RedisData();
        redisData.setData(value);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(timeUnit.toSeconds(time)));
        redisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(redisData));
    }

    /**
     * 根据指定的 Key 查询缓存，反序列化为指定类型，利用缓存空值的方式解决缓存穿透问题。
     */
    public <R, ID> R dealWithCachePenetration(String keyPrefix, ID id, Class<R> type, Function<ID, R> dbFallback, Long time, TimeUnit timeUnit) {
        String key = keyPrefix + id;
        // 1. 从 Redis 中查询店铺缓存；
        String json = redisTemplate.opsForValue().get(key);
        // 2. 若 Redis 中存在（命中），则将其转换为 Java 对象后返回；
        if (StrUtil.isNotBlank(json)) {
            return JSONUtil.toBean(json, type);
        }
        // 3. 命中缓存后判断是否为空值
        if (ObjectUtil.equal(json, "")) {
            return null;
        }
        // 4. 若 Redis 中不存在（未命中），则根据 id 从数据库中查询；
        R r = dbFallback.apply(id);

        // 5. 若 数据库 中不存在，将空值写入 Redis（缓存空对象）
        if (r == null) {
            redisTemplate.opsForValue().set(key, "", TTL_TWO, TimeUnit.MINUTES);
            return null;
        }

        // 6. 若 数据库 中存在，则将其返回并存入 Redis 缓存中。
        this.set(key, r, time, timeUnit);
        return r;
    }

    /**
     * 根据指定的 Key 查询缓存，反序列化为指定类型，利用逻辑过期的方式解决缓存击穿问题。
     */
    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);
    public <R, ID> R dealWithCacheHotspotInvalid(String keyPrefix, ID id, Class<R> type, Function<ID, R> dbFallback, Long time, TimeUnit timeUnit) {
        String key = keyPrefix + id;
        // 1. 从 Redis 中查询店铺缓存；
        String json = redisTemplate.opsForValue().get(key);
        // 2. 未命中
        if (StrUtil.isBlank(json)) {
            return null;
        }
        // 3. 命中（先将 JSON 反序列化为 对象）
        RedisData redisData = JSONUtil.toBean(json, RedisData.class);
        R r = JSONUtil.toBean((JSONObject) redisData.getData(), type);
        LocalDateTime expireTime = redisData.getExpireTime();
        // 4. 判断是否过期：未过期，直接返回店铺信息；过期，需要缓存重建。
        if (expireTime.isAfter(LocalDateTime.now())) {
            return r;
        }
        // 5. 缓存重建（未获取到互斥锁，直接返回店铺信息）
        String lockKey = LOCK_SHOP_KEY + id;
        boolean isLocked = getLock(lockKey);
        // 5.1 获取到互斥锁
        // 开启独立线程：根据 id 查询数据库，将数据写入到 Redis，并且设置逻辑过期时间。
        // 此处必须进行 DoubleCheck：多线程并发下，若线程1 和 线程2都到达这一步，线程1 拿到锁，进行操作后释放锁；线程2 拿到锁后会再次进行查询数据库、写入到 Redis 中等操作。
        json = redisTemplate.opsForValue().get(key);
        if (StrUtil.isBlank(json)) {
            return null;
        }
        if (isLocked) {
            CACHE_REBUILD_EXECUTOR.submit(() -> {
                try {
                    // 重建缓存
                    R apply = dbFallback.apply(id);
                    this.setWithLogicalExpiration(key, apply, time, timeUnit);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    // 释放互斥锁
                    releaseLock(lockKey);
                }
            });
        }
        // 5.2 返回店铺信息
        return r;
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
}
