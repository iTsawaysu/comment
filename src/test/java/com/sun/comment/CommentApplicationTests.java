package com.sun.comment;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.comment.common.RedisConstants;
import com.sun.comment.entity.Shop;
import com.sun.comment.entity.User;
import com.sun.comment.entity.Voucher;
import com.sun.comment.service.ShopService;
import com.sun.comment.service.VoucherService;
import com.sun.comment.utils.RedisIdWorker;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
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

/**
 * @author sun
 */
@SpringBootTest(classes = CommentApplication.class)
@Slf4j
class CommentApplicationTests {
    @Resource
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

        // Serialization, then putting into Redis
        String serializableUser = objectMapper.writeValueAsString(user);
        stringRedisTemplate.opsForValue().set("user:001", serializableUser);

        // Deserialization, then getting from Redis
        String serializableStr = stringRedisTemplate.opsForValue().get("user:001");
        user = objectMapper.readValue(serializableStr, User.class);

        System.out.println("serializableStr = " + serializableStr);
        System.out.println("user = " + user);
    }

    @Test
    void testTimestamp() {
        long timestamp = LocalDateTime.of(2024, 1, 1, 0, 0, 0).toEpochSecond(ZoneOffset.UTC);
        System.out.println(timestamp);
        System.out.println(LocalDateTime.ofEpochSecond(timestamp, 0, ZoneOffset.UTC));
    }

    @Resource
    private RedisIdWorker redisIdWorker;
    private final ExecutorService pool = Executors.newFixedThreadPool(300);

    @Test
    void testRedisIdWorker() throws InterruptedException {
        CountDownLatch countDownLatch = new CountDownLatch(300);
        Runnable runnable = () -> {
            for (int i = 0; i < 1000; i++) {
                redisIdWorker.nextId("sun");
            }
            countDownLatch.countDown();
        };
        long begin = System.currentTimeMillis();
        for (int i = 0; i < 300; i++) {
            pool.execute(runnable);
        }
        countDownLatch.await();
        pool.shutdown();
        System.out.println(System.currentTimeMillis() - begin + "ms");  // 3330ms
    }

    @Resource
    private VoucherService voucherService;

    @Test
    void testAddVoucher() {
        voucherService.addSeckillVoucher(Voucher.builder()
                .shopId(2L)
                .title("50元代金券")
                .subTitle("周一到周五均可使用")
                .rules("全场通用\\n无需预约\\n可无限叠加\\不兑现、不找零\\n仅限堂食")
                .payValue(8000L)
                .actualValue(10000L)
                .type(1)
                .stock(100)
                .beginTime(LocalDateTime.now())
                .endTime(LocalDateTime.now().plusDays(3))
                .build()
        );
    }

    @Resource
    private RedissonClient redissonClient;
    private RLock lock;
    @BeforeEach
    void getRedissonLock() {
        lock = redissonClient.getLock("aLock");
    }
    @Test
    void testRedisson() throws InterruptedException {
        // 尝试获取锁（1：获取锁失败后的 1s 内会重试获取锁，1s 内仍未获取到锁则返回 false，默认为 -1，即不重试。10：锁的自动释放时间）
        boolean tryLock = lock.tryLock(1, 3, TimeUnit.SECONDS);
        if (tryLock) {
            try {
                System.out.println("1 tryLock");
                redissonReentrant();
            } finally {
                lock.unlock();
                System.out.println("1 unlock");
            }
        }
    }
    void redissonReentrant() {
        if (lock.tryLock()) {
            try {
                System.out.println("2 tryLock");
            } finally {
                lock.unlock();
                System.out.println("2 unlock");
            }
        }
    }

    @Resource
    private ShopService shopService;
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
}
