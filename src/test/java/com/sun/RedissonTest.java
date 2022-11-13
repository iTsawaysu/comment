package com.sun;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.boot.test.context.SpringBootTest;

import javax.annotation.Resource;
import java.util.concurrent.TimeUnit;

/**
 * @Author Sun Jianda
 * @Date 2022/10/17
 */

@Slf4j
@SpringBootTest
public class RedissonTest {

    @Resource
    private RedissonClient redissonClient;

    @Resource
    private RedissonClient redissonClientTwo;

    @Resource
    private RedissonClient redissonClientThree;

    RLock multiLock;

    @BeforeEach
    void setUp() {
        RLock lock = redissonClient.getLock("anyLock");
        RLock lockTwo = redissonClientTwo.getLock("anyLock");
        RLock lockThree = redissonClientThree.getLock("anyLock");
        // 创建联锁 MultiLock
        RLock multiLock = redissonClient.getMultiLock(lock, lockTwo, lockThree);

    }

    @Test
    void methodOne() throws InterruptedException {
        boolean isLocked = multiLock.tryLock(1L, TimeUnit.SECONDS);
        if (!isLocked) {
            log.error("Fail To Get Lock~");
            return;
        }
        try {
            log.info("Get Lock Successfully~");
            methodTwo();
        } finally {
            log.info("Release Lock~");
            multiLock.unlock();
        }
    }

    @Test
    void methodTwo() throws InterruptedException {
        boolean isLocked = multiLock.tryLock(1L, TimeUnit.SECONDS);
        if (!isLocked) {
            log.error("Fail To Get Lock!");
            return;
        }
        try {
            log.info("Get Lock Successfully!");
        } finally {
            log.info("Release Lock!");
            multiLock.unlock();
        }
    }
}
