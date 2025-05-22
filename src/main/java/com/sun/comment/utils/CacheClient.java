package com.sun.comment.utils;

import cn.hutool.core.thread.ThreadUtil;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.sun.comment.common.ErrorCode;
import com.sun.comment.common.exception.BusinessException;
import com.sun.comment.entity.dto.RedisData;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static com.sun.comment.common.RedisConstants.*;

/**
 * @author sun
 */
@Component
public class CacheClient {
    private static final ExecutorService pool = Executors.newFixedThreadPool(1);
    private final StringRedisTemplate stringRedisTemplate;
    public CacheClient(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    public boolean tryLock(String lockKey) {
        return stringRedisTemplate.opsForValue().setIfAbsent(lockKey, "1", TTL_TEN, TimeUnit.SECONDS);
    }
    public void unlock(String lockKey) {
        stringRedisTemplate.delete(lockKey);
    }
    public void dataWarmUp(String key, Object data, Long expireTime, TimeUnit timeUnit) {
        RedisData redisData = new RedisData();
        redisData.setData(data);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(timeUnit.toSeconds(expireTime)));
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(redisData));
    }
    public void set(String key, Object value, Long time, TimeUnit timeUnit) {
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value), time, timeUnit);
    }

    /**
     * 解决缓存穿透问（缓存空值）
     *
     * @param id        id
     * @param type      实体类型
     * @param function  有参有返回值的函数
     * @param time      TTL 过期时间
     * @param timeUnit  时间单位
     * @param <R>       实体类型
     * @param <ID>      id 类型
     * @return 设置某个实体类的缓存，并解决缓存穿透问题
     */
    public <R, ID> R setCachingEmpty(ID id, Class<R> type, Function<ID, R> function, Long time, TimeUnit timeUnit) {
        String key = CACHE_SHOP_KEY + id;
        String json = stringRedisTemplate.opsForValue().get(key);
        if (StrUtil.isNotBlank(json)) {
            return JSONUtil.toBean(json, type);
        }
        // 命中空值
        if ("".equals(json)) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR);
        }
        // 从 Redis 中未查询到数据，则从数据库中查询
        R result = function.apply(id);
        if (result == null) {
            // 若数据中也查询不到，则缓存空值后返回提示信息
            stringRedisTemplate.opsForValue().set(key, "", TTL_TWO, TimeUnit.MINUTES);
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR);
        }
        this.set(key, result, time, timeUnit);
        return result;
    }

    /**
     * 解决缓存击穿问题（synchronized）
     */
    public <R, ID> R setSynchronized(ID id, Class<R> type, Function<ID, R> function, Long time, TimeUnit timeUnit) {
        String key = CACHE_SHOP_KEY + id;
        String json = stringRedisTemplate.opsForValue().get(key);
        if (StrUtil.isNotBlank(json)) {
            return JSONUtil.toBean(json, type);
        }
        // 命中空值
        if ("".equals(json)) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR);
        }
        // 从 Redis 中未查询到数据，则从数据库中查询。（synchronized）
        R result = null;
        synchronized (CacheClient.class) {
            // 再次查询 Redis：若多个线程执行到同步代码块，某个线程拿到锁查询数据库并重建缓存后，其他拿到锁进来的线程直接查询缓存后返回，避免重复查询数据库并重建缓存。
            json = stringRedisTemplate.opsForValue().get(key);
            if (StrUtil.isNotBlank(json)) {
                return JSONUtil.toBean(json, type);
            }
            // 查询数据库、重建缓存、缓存空值避免缓存穿透。
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
     */
    public <R, ID> R setSetNx(ID id, Class<R> type, Function<ID, R> function, Long time, TimeUnit timeUnit) {
        String key = CACHE_SHOP_KEY + id;
        String lockKey = LOCK_SHOP_KEY + id;
        String json = stringRedisTemplate.opsForValue().get(key);
        if (StrUtil.isNotBlank(json)) {
            return JSONUtil.toBean(json, type);
        }
        if ("".equals(json)) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR);
        }
        R result = null;
        boolean tryLock = tryLock(lockKey);
        try {
            // 未获取到锁则等待一段时间后重试（通过递归调用重试）
            if (!tryLock) {
                ThreadUtil.sleep(50);
                this.setSetNx(id, type, function, time, timeUnit);
            }
            // 获取到锁：查询数据库、缓存重建
            if (tryLock) {
                json = stringRedisTemplate.opsForValue().get(key);
                if (StrUtil.isNotBlank(json)) {
                    return JSONUtil.toBean(json, type);
                }
                result = function.apply(id);
                if (result == null) {
                    stringRedisTemplate.opsForValue().set(key, "", TTL_TWO, TimeUnit.MINUTES);
                    throw new BusinessException(ErrorCode.NOT_FOUND_ERROR);
                }
                this.set(key, result, time, timeUnit);
                ThreadUtil.sleep(200);
            }
        } finally {
            unlock(lockKey);
        }
        return result;
    }

    /**
     * 解决缓存击穿问题（逻辑过期时间）
     */
    public <R, ID> R setLogicalExpiration(ID id, Class<R> type, Function<ID, R> function, Long time, TimeUnit timeUnit) {
        String key = CACHE_SHOP_KEY + id;
        String lockKey = LOCK_SHOP_KEY + id;
        // 先从 Redis 中查询数据，未命中则直接返回
        String json = stringRedisTemplate.opsForValue().get(key);
        if (StrUtil.isBlank(json)) {
            return null;
        }
        // 判断是否过期，未过期则直接返回
        RedisData redisData = JSONUtil.toBean(json, RedisData.class);
        JSONObject jsonObject = JSONUtil.parseObj(redisData.getData());
        R result = JSONUtil.toBean(jsonObject, type);
        LocalDateTime expireTime = redisData.getExpireTime();
        if (expireTime.isAfter(LocalDateTime.now())) {
            return result;
        }
        // 未获取到锁直接返回
        boolean tryLock = tryLock(lockKey);
        if (BooleanUtil.isFalse(tryLock)) {
            return result;
        }
        // 获取到锁：开启一个新的线程后返回旧数据。（这个线程负责查询数据库、重建缓存）
        // 此处无需 DoubleCheck，因为未获取到锁直接返回旧数据，能保证只有一个线程执行到此处
        pool.submit(() -> {
            try {
                this.dataWarmUp(key, function.apply(id), time, timeUnit);
            } finally {
                unlock(lockKey);
            }
        });
        return result;
    }
}
