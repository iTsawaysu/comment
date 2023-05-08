package com.sun.utils;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.sun.common.ErrorCode;
import com.sun.dto.RedisData;
import com.sun.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static com.sun.common.RedisConstants.LOCK_SHOP_KEY;
import static com.sun.common.RedisConstants.TTL_TWO;

/**
 * @author sun
 */
@Component
@Slf4j
public class CacheClient {

    private static final ExecutorService ES = Executors.newFixedThreadPool(10);

    private final StringRedisTemplate stringRedisTemplate;

    public CacheClient(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    /**
     * 获取锁
     */
    public boolean tryLock(String key) {
        Boolean result = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", TTL_TWO, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(result);
    }

    /**
     * 释放锁
     */
    public void unlock(String key) {
        stringRedisTemplate.delete(key);
    }

    /**
     * 数据预热（将热点数据提前存储到 Redis 中）
     *
     * @param key        预热数据的 Key
     * @param value      预热数据的 Value
     * @param expireTime 逻辑过期时间
     * @param timeUnit   时间单位
     */
    public void dataWarmUp(String key, Object value, Long expireTime, TimeUnit timeUnit) {
        RedisData redisData = new RedisData();
        redisData.setData(value);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(timeUnit.toSeconds(expireTime)));
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(redisData));
    }


    /**
     * 将 Java 对象序列化为 JSON 存储到 Redis 中并且设置 TTL 过期时间
     *
     * @param key      String 类型的键
     * @param value    序列化为 JSON 的值
     * @param time     TTL 过期时间
     * @param timeUnit 时间单位
     */
    public void set(String key, Object value, Long time, TimeUnit timeUnit) {
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value), time, timeUnit);
    }


    /**
     * 解决缓存穿透问（缓存空值）
     *
     * @param keyPrefix Key 前缀
     * @param id        id
     * @param type      实体类型
     * @param function  有参有返回值的函数
     * @param time      TTL 过期时间
     * @param timeUnit  时间单位
     * @param <R>       实体类型
     * @param <ID>      id 类型
     * @return 设置某个实体类的缓存，并解决缓存穿透问题
     */
    public <R, ID> R setWithCachePenetration(String keyPrefix, ID id, Class<R> type, Function<ID, R> function, Long time, TimeUnit timeUnit) {
        String key = keyPrefix + id;

        // 1. 先从 Redis 中查询数据，存在则将其转换为 Java 对象后返回
        String jsonStr = stringRedisTemplate.opsForValue().get(key);
        if (StringUtils.isNotBlank(jsonStr)) {
            return JSONUtil.toBean(jsonStr, type);
        }
        // 命中空值
        if (jsonStr != null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR);
        }

        // 2. 从 Redis 中未查询到数据，则从数据库中查询
        R result = function.apply(id);
        // 若数据中也查询不到，则缓存空值后返回提示信息
        if (result == null) {
            stringRedisTemplate.opsForValue().set(key, "", TTL_TWO, TimeUnit.MINUTES);
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR);
        }

        // 3. 将从数据库中查询到的数据存入 Redis 后返回
        this.set(key, result, time, timeUnit);
        return result;
    }


    /**
     * 解决缓存击穿问题（synchronized）
     *
     * @param keyPrefix Key 前缀
     * @param id        id
     * @param type      实体类型
     * @param function  有参有返回值的函数
     * @param time      TTL 过期时间
     * @param timeUnit  时间单位
     * @param <R>       实体类型
     * @param <ID>      id 类型
     * @return          设置某个实体类的缓存，并解决缓存击穿问题
     */
    public <R, ID> R setWithCacheBreakdown4Synchronized(String keyPrefix, ID id, Class<R> type, Function<ID, R> function, Long time, TimeUnit timeUnit) {
        String key = keyPrefix + id;

        // 1. 先从 Redis 中查询数据，存在则将其转换为 Java 对象后返回
        String jsonStr = stringRedisTemplate.opsForValue().get(key);
        if (StringUtils.isNotBlank(jsonStr)) {
            return JSONUtil.toBean(jsonStr, type);
        }
        // 命中空值
        if (jsonStr != null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR);
        }

        // 2. 从 Redis 中未查询到数据，则从数据库中查询。（synchronized）
        R result = null;
        synchronized (CacheClient.class) {
            // 3. 再次查询 Redis：若多个线程执行到同步代码块，某个线程拿到锁查询数据库并重建缓存后，其他拿到锁进来的线程直接查询缓存后返回，避免重复查询数据库并重建缓存。
            jsonStr = stringRedisTemplate.opsForValue().get(key);
            if (StringUtils.isNotBlank(jsonStr)) {
                return JSONUtil.toBean(jsonStr, type);
            }

            // 4. 查询数据库、缓存空值避免缓存穿透、重建缓存。
            result = function.apply(id);
            if (result == null) {
                stringRedisTemplate.opsForValue().set(key, "", TTL_TWO, TimeUnit.MINUTES);
                throw new BusinessException(ErrorCode.NOT_FOUND_ERROR);
            }
            this.set(key, result, time, timeUnit);
        }
        return result;
    }

    /**
     * 解决缓存击穿问题（setnx）
     *
     * @param keyPrefix Key 前缀
     * @param id        id
     * @param type      实体类型
     * @param function  有参有返回值的函数
     * @param time      TTL 过期时间
     * @param timeUnit  时间单位
     * @param <R>       实体类型
     * @param <ID>      id 类型
     * @return 设置某个实体类的缓存，并解决缓存击穿问题
     */
    public <R, ID> R setWithCacheBreakdown4SetNx(String keyPrefix, ID id, Class<R> type, Function<ID, R> function, Long time, TimeUnit timeUnit) {
        String key = keyPrefix + id;
        String lockKey = LOCK_SHOP_KEY + id;

        // 1. 先从 Redis 中查询数据，存在则将其转换为 Java 对象后返回
        String jsonStr = stringRedisTemplate.opsForValue().get(key);
        if (StringUtils.isNotBlank(jsonStr)) {
            return JSONUtil.toBean(jsonStr, type);
        }
        // 命中空值
        if (jsonStr != null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR);
        }

        // 2. 从 Redis 中未查询到数据，尝试获取锁后从数据库中查询。
        R result = null;
        boolean tryLock = tryLock(lockKey);
        try {
            // 2.1 未获取到锁则等待一段时间后重试（通过递归调用重试）
            if (BooleanUtil.isFalse(tryLock)) {
                Thread.sleep(50);
                this.setWithCacheBreakdown4SetNx(keyPrefix, id, type, function, time, timeUnit);
            }
            // 2.2 获取到锁：查询数据库、缓存重建。
            if (tryLock) {
                // 3. 再次查询 Redis：若多个线程执行到同步代码块，某个线程拿到锁查询数据库并重建缓存后，其他拿到锁进来的线程直接查询缓存后返回，避免重复查询数据库并重建缓存。
                jsonStr = stringRedisTemplate.opsForValue().get(key);
                if (StringUtils.isNotBlank(jsonStr)) {
                    return JSONUtil.toBean(jsonStr, type);
                }

                // 4. 查询数据库、缓存空值避免缓存穿透、重建缓存。
                result = function.apply(id);
                if (result == null) {
                    stringRedisTemplate.opsForValue().set(key, "", TTL_TWO, TimeUnit.MINUTES);
                    throw new BusinessException(ErrorCode.NOT_FOUND_ERROR);
                }
                this.set(key, result, time, timeUnit);
            }
        } catch (Exception e) {
            log.error(e.getMessage());
        } finally {
            unlock(lockKey);
        }
        return result;
    }


    /**
     * 解决缓存击穿问题（逻辑过期时间）
     *
     * @param keyPrefix Key 前缀
     * @param id        id
     * @param type      实体类型
     * @param function  有参有返回值的函数
     * @param time      TTL 过期时间
     * @param timeUnit  时间单位
     * @param <R>       实体类型
     * @param <ID>      id 类型
     * @return 设置某个实体类的缓存，并解决缓存击穿问题
     */
    public <R, ID> R setWithCacheBreakdown4LogicalExpiration(String keyPrefix, ID id, Class<R> type, Function<ID, R> function, Long time, TimeUnit timeUnit) {
        String key = keyPrefix + id;
        String lockKey = LOCK_SHOP_KEY + id;

        // 1. 先从 Redis 中查询数据，未命中则直接返回
        String jsonStr = stringRedisTemplate.opsForValue().get(key);
        if (StringUtils.isBlank(jsonStr)) {
            return null;
        }

        // 2. 判断是否过期，未过期则直接返回
        RedisData redisData = JSONUtil.toBean(jsonStr, RedisData.class);
        JSONObject jsonObject = JSONUtil.parseObj(redisData.getData());
        R result = JSONUtil.toBean(jsonObject, type);
        LocalDateTime expireTime = redisData.getExpireTime();
        if (expireTime.isAfter(LocalDateTime.now())) {
            return result;
        }

        // 3. 未获取到锁直接返回
        boolean tryLock = tryLock(lockKey);
        if (BooleanUtil.isFalse(tryLock)) {
            return result;
        }

        // 4. 获取到锁：开启一个新的线程后返回旧数据。（这个线程负责查询数据库、重建缓存）
        // 此处无需 DoubleCheck，因为未获取到锁直接返回旧数据，能保证只有一个线程执行到此处
        ES.submit(() -> {
            try {
                this.dataWarmUp(key, function.apply(id), time, timeUnit);
            } finally {
                unlock(lockKey);
            }
        });
        return result;
    }
}
