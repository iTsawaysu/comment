package com.sun;

import com.sun.entity.Shop;
import com.sun.service.impl.ShopServiceImpl;
import com.sun.utils.CacheClient;
import com.sun.utils.RedisIdWorker;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import javax.annotation.Resource;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.sun.utils.RedisConstants.CACHE_SHOP_KEY;
import static com.sun.utils.RedisConstants.TTL_TEN;

@Slf4j
@SpringBootTest(classes = CommentApplication.class)
class CommentApplicationTests {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private ShopServiceImpl shopService;

    @Autowired
    private CacheClient cacheClient;

    @Resource
    private RedisIdWorker redisIdWorker;

    private ExecutorService es = Executors.newFixedThreadPool(500);


    @Test
    void testConn() {
        stringRedisTemplate.keys("*").forEach(System.out::println);
        System.out.println(System.currentTimeMillis());
    }

    @Test
    void test() {
        try {
            shopService.saveHotspot2Redis(1L, 10L);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void advanceHotspot() {
        Shop shop = shopService.getById(1L);
        cacheClient.setWithLogicalExpiration(CACHE_SHOP_KEY + 1L, shop, TTL_TEN, TimeUnit.SECONDS);
    }

    @Test
    void testRedisIdWorker() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(300);
        Runnable task = () -> {
            for (int i = 0; i < 100; i++) {
                long id = redisIdWorker.nextId("sun");
                System.out.println("id = " + id);
            }
            latch.countDown();
        };

        long begin = System.currentTimeMillis();
        for (int i = 0; i < 300; i++) {
            es.submit(task);
        }
        long end = System.currentTimeMillis();
        latch.await();
        System.out.println("Execution Time: " + (end - begin));
    }
}
