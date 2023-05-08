package com.sun;

import cn.hutool.core.util.BooleanUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.common.RedisConstants;
import com.sun.entity.Shop;
import com.sun.entity.User;
import com.sun.service.impl.ShopServiceImpl;
import com.sun.utils.RedisIdWorker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.core.StringRedisTemplate;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;


@SpringBootTest
class CommentApplicationTests {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 测试 Redis 存储序列化和反序列化
     */
    @Test
    void testSerialization() throws JsonProcessingException {
        User user = new User();
        user.setId(1L);
        user.setNickName("abc");
        user.setPassword("abc123");
        user.setPhone("123213132");
        user.setIcon("123213132abc");

        ObjectMapper objectMapper = new ObjectMapper();

        // Serialization and then put into Redis
        String serializableUser = objectMapper.writeValueAsString(user);
        stringRedisTemplate.opsForValue().set("user:001", serializableUser);

        // Deserialization and then get from Redis
        String serializableStr = stringRedisTemplate.opsForValue().get("user:001");
        user = objectMapper.readValue(serializableStr, User.class);

        System.out.println("serializableStr = " + serializableStr);
        System.out.println("user = " + user);
    }

    @Resource
    private ShopServiceImpl shopService;

    /**
     * 缓存预热
     */
    @Test
    void testSaveHotDataIn2Redis() throws InterruptedException {
        shopService.saveHotDataIn2Redis(1L, 1L);
    }

    @Test
    void test4Timestamp() {
        long timestamp = LocalDateTime.of(2023, 1, 1, 0, 0, 0).toEpochSecond(ZoneOffset.UTC);
        System.out.println("timestamp = " + timestamp);   // timestamp = 1672531200

        LocalDateTime datetime = LocalDateTime.ofEpochSecond(timestamp, 0, ZoneOffset.UTC);
        System.out.println("datetime = " + datetime);   // 2023-01-01T00:00
    }

    @Resource
    private RedisIdWorker redisIdWorker;

    public static final ExecutorService ES = Executors.newFixedThreadPool(500);

    @Test
    void testGloballyUniqueID() throws Exception {
        // 程序是异步的，分线程全部走完之后主线程再走，使用 CountDownLatch；否则异步程序没有执行完时主线程就已经执行完了
        CountDownLatch latch = new CountDownLatch(300);
        Runnable task = () -> {
            for (int i = 0; i < 100; i++) {
                long globallyUniqueID = redisIdWorker.nextId("sun");
                System.out.println("globallyUniqueID = " + globallyUniqueID);
            }
            latch.countDown();
        };

        long begin = System.currentTimeMillis();
        for (int i = 0; i < 300; i++) {
            ES.submit(task);
        }
        latch.await();
        long end = System.currentTimeMillis();
        System.out.println("Execution Time: " + (end - begin));
    }

    @Resource
    private RedissonClient redissonClient;

    @Test
    void testRedissonLock() throws InterruptedException {
        // 创建锁对象并指定名称（可重入锁）
        RLock lock = redissonClient.getLock("aLock");
        /**
         * 尝试获取锁
         * waitTime：获取锁失败后的最大等待时间，也就是在获取锁失败后 n 秒内会重试获取锁，n 秒内依然未获取到锁后才会返回 false（默认为 -1，不重试）
         * leaseTime：锁的自动释放时间
         * unit：时间单位
         */
        boolean tryLock = lock.tryLock(1, 10, TimeUnit.SECONDS);
        if (BooleanUtil.isTrue(tryLock)) {
            try {
                System.out.println("执行业务..");
            } finally {
                // 释放锁
                lock.unlock();
            }
        }
    }

    private RLock lock;

    @BeforeEach
    void beforeTestMethod() {
        lock = redissonClient.getLock("aLock");
    }

    @Test
    void m1() {
        boolean tryLock = lock.tryLock();
        try {
            if (tryLock) {
                System.out.println("m1 tryLock...");
                m2();
            }
        } finally {
            lock.unlock();
            System.out.println("m1 unlock...");
        }
    }

    @Test
    void m2() {
        boolean tryLock = lock.tryLock();
        try {
            if (tryLock) {
                System.out.println("m2 tryLock...");
            }
        } finally {
            lock.unlock();
            System.out.println("m2 unlock...");
        }
    }

    @Test
    void loadShopData() {
        List<Shop> shopList = shopService.list();
        // 1. 店铺按照 TypeId 分组
        Map<Long, List<Shop>> map = shopList.stream().collect(Collectors.groupingBy(Shop::getTypeId));
        // 2. 分批写入 Redis
        for (Map.Entry<Long, List<Shop>> entry : map.entrySet()) {
            Long typeId = entry.getKey();
            String key = RedisConstants.SHOP_GEO_KEY + typeId;
            shopList = entry.getValue();
            for (Shop shop : shopList) {
                // GEOADD key longitude latitude member
                stringRedisTemplate.opsForGeo().add(key, new Point(shop.getX(), shop.getY()), shop.getId().toString());
            }
        }
    }

    @Test
    void millionDataHyperLogLogTest() {
        String[] users = new String[1000];
        int j = 0;
        for (int i = 0; i < 1000000; i++) {
            j = i % 1000;
            users[j] = "user_" + i;
            // 分批导入，每 1000 条数据写入一次
            if (j == 999) {
                stringRedisTemplate.opsForHyperLogLog().add("hll", users);
            }
        }
        Long hllSize = stringRedisTemplate.opsForHyperLogLog().size("hll");
        System.out.println("size = " + hllSize);
    }
}
