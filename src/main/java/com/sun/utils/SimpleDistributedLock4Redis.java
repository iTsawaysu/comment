package com.sun.utils;

import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.StrUtil;
import com.sun.common.ErrorCode;
import com.sun.exception.BusinessException;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.Collections;
import java.util.concurrent.TimeUnit;

/**
 * @author sun
 */
public class SimpleDistributedLock4Redis implements DistributedLock {

    private static final String KEY_PREFIX = "lock:";
    // ID_PREFIX 在当前 JVM 中是不变的，主要用于区分不同 JVM
    private static final String THREAD_PREFIX = UUID.randomUUID().toString(true);

    private final String name;
    private final StringRedisTemplate stringRedisTemplate;
    public SimpleDistributedLock4Redis(String name, StringRedisTemplate stringRedisTemplate) {
        this.name = name;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    private static final DefaultRedisScript<Long> SCRIPT;
    static {
        SCRIPT = new DefaultRedisScript<>();
        SCRIPT.setLocation(new ClassPathResource("Unlock.lua"));
        SCRIPT.setResultType(Long.class);
    }

    @Override
    public boolean tryLock(long timeout) {
        // UUID 用于区分不同服务器中线程 ID 相同的线程；线程 ID 用于区分同一个服务器中的线程。
        String threadIdentifier = THREAD_PREFIX + "-" + Thread.currentThread().getId();
        Boolean result = stringRedisTemplate.opsForValue()
                .setIfAbsent(KEY_PREFIX + name, threadIdentifier, timeout, TimeUnit.SECONDS);
        // result 是 Boolean 类型，直接返回存在自动拆箱，为防止空指针不直接返回
        return Boolean.TRUE.equals(result);
    }

    /**
     * VERSION 3.0（释放锁前通过判断 Redis 中的线程标识与当前线程的线程标识是否一致，解决误删问题，并通过 Lua 脚本保证释放锁操作的原子性）
     */
    @Override
    public void unlock() {
        // 调用 Lua 脚本
        stringRedisTemplate.execute(
                SCRIPT,
                Collections.singletonList(KEY_PREFIX + name),   // KEYS[1]
                THREAD_PREFIX + "-" + Thread.currentThread().getId()    // ARGV[1]
        );
    }

    /**
     * VERSION2.0（释放锁前通过判断 Redis 中的线程标识与当前线程的线程标识是否一致，解决误删问题）
     */
    public void unlock02() {
        // UUID 用于区分不同服务器中线程 ID 相同的线程；线程 ID 用于区分同一个服务器中的线程。
        String threadIdentifier = THREAD_PREFIX + Thread.currentThread().getId();
        String threadIdentifierFromRedis = stringRedisTemplate.opsForValue().get(KEY_PREFIX + name);
        // 比较 Redis 中的线程标识与当前的线程标识是否一致
        if (!StrUtil.equals(threadIdentifier, threadIdentifierFromRedis)) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "释放锁失败");
        }
        // 释放锁标识
        stringRedisTemplate.delete(KEY_PREFIX + name);
    }

    /**
     * VERSION1.0
     */
    public void unlock01() {
        stringRedisTemplate.delete(KEY_PREFIX + name);
    }
}
