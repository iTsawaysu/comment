package com.sun.comment.utils;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

@Component
public class RedisIdWorker {
    private static final long BEGIN_TIMESTAMP = 1704067200L;
    // 序列号位数
    private static final int BIT_COUNT = 32;
    private final StringRedisTemplate stringRedisTemplate;

    public RedisIdWorker(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    public long nextId(String keyPrefix) {
        // 时间戳
        long timestamp = LocalDateTime.now().toEpochSecond(ZoneOffset.UTC) - BEGIN_TIMESTAMP;
        // 生成序列号：根据 keyPrefix 和当前日期生成一个键，若键不存在，则会创建并初始化为 1.否则在此基础上递增。
        Long serialNumber = stringRedisTemplate.opsForValue().increment(keyPrefix + ":" + DateTimeFormatter.ofPattern("yyyy-MM-dd").format(LocalDate.now()));
        // 时间戳左移 32 位，序列号占低位的 32 个 bit（与低位的 32 个 0 进行或运算）
        return timestamp << BIT_COUNT | serialNumber;
    }
}
