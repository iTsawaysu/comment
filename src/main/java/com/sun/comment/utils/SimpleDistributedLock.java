package com.sun.comment.utils;

import cn.hutool.core.lang.UUID;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.Collections;

/**
 * @author sun
 */
public class SimpleDistributedLock implements DistributedLock {
    // ID_PREFIX 在当前 JVM 中是不变的，主要用于区分不同 JVM
    private static final String THREAD_PREFIX = UUID.randomUUID().toString(true) + "-";
    private static final String KEY_PREFIX = "lock:";
    private final String name;
    private final StringRedisTemplate stringRedisTemplate;

    private static final DefaultRedisScript<Long> SCRIPT;

    static {
        SCRIPT = new DefaultRedisScript<>();
        SCRIPT.setLocation(new ClassPathResource("unlock.lua"));
        SCRIPT.setResultType(Long.class);
    }

    public SimpleDistributedLock(String name, StringRedisTemplate stringRedisTemplate) {
        this.name = name;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public boolean tryLock(long timeout) {
        // getId() is deprecated, use threadId() instead.
        String threadIdentifier = THREAD_PREFIX + Thread.currentThread().threadId();
        Boolean result = stringRedisTemplate.opsForValue().setIfAbsent(KEY_PREFIX + name, threadIdentifier);
        return Boolean.TRUE.equals(result);
    }

    @Override
    public void unlock() {
        stringRedisTemplate.execute(SCRIPT,
                Collections.singletonList(KEY_PREFIX + name),  // KEYS[1]
                THREAD_PREFIX + "-" + Thread.currentThread().threadId()  // ARGV[1]
        );
    }
}
