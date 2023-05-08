package com.sun.utils;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * @author sun
 */
@Component
public class RedisIdWorker {

    /**
     * 指定时间戳（2023年1月1日 0:0:00） LocalDateTime.of(2023, 1, 1, 0, 0, 0).toEpochSecond(ZoneOffset.UTC)
     */
    private static final long BEGIN_TIMESTAMP_2023 = 1672531200L;

    /**
     * 序列号位数
     */
    private static final int BIT_COUNT = 32;

    private final StringRedisTemplate stringRedisTemplate;

    public RedisIdWorker(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    public long nextId(String keyPrefix) {
        // 1. 时间戳
        long timestamp = LocalDateTime.now().toEpochSecond(ZoneOffset.UTC) - BEGIN_TIMESTAMP_2023;

        // 2. 生成序列号：自增 1，Key 不存在会自动创建一个 Key。（存储到 Redis 中的 Key 为 keyPrefix:date，Value 为自增的数量）
        Long serialNumber = stringRedisTemplate.opsForValue().increment(keyPrefix + ":" + DateTimeFormatter.ofPattern("yyyy-MM-dd").format(LocalDate.now()));

        // 3. 时间戳左移 32 位，序列号与右边的 32 个 0 进行与运算
        return timestamp << BIT_COUNT | serialNumber;
    }
}
