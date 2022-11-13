package com.sun.utils;

import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.BooleanUtil;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.Collections;
import java.util.concurrent.TimeUnit;

/**
 * @Author Sun Jianda
 * @Date 2022/10/14
 */

public class SimpleDistributedLockBasedOnRedis implements DistributedLock {
    private String name;
    private StringRedisTemplate stringRedisTemplate;

    public SimpleDistributedLockBasedOnRedis(String name, StringRedisTemplate stringRedisTemplate) {
        this.name = name;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    private static final String KEY_PREFIX = "lock:";

    private static final String ID_PREFIX = UUID.randomUUID().toString(true) + "-";

    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT;

    static {
        UNLOCK_SCRIPT = new DefaultRedisScript<>();
        UNLOCK_SCRIPT.setLocation(new ClassPathResource("Unlock.lua"));
        UNLOCK_SCRIPT.setResultType(Long.class);
    }

    /**
     * 获取锁
     */
    @Override
    public boolean tryLock(long timeoutSeconds) {
        // 线程标识
        String threadIdentifier = ID_PREFIX + Thread.currentThread().getId();
        Boolean isSucceeded = stringRedisTemplate.opsForValue()
                .setIfAbsent(KEY_PREFIX + name, threadIdentifier, timeoutSeconds, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(isSucceeded);
    }

    /**
     * 释放锁
     */
    @Override
    public void unlock() {
        // 调用 Lua 脚本
        stringRedisTemplate.execute(
                UNLOCK_SCRIPT,  // SCRIPT
                Collections.singletonList(KEY_PREFIX + name),   // KEY[1]
                ID_PREFIX + Thread.currentThread().getId()    // ARGV[1]
        );
    }
}
