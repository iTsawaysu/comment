package com.sun;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import com.sun.entity.Shop;
import com.sun.service.ShopService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;

import javax.annotation.Resource;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

import static com.sun.utils.RedisConstants.BLOG_LIKED_KEY;

/**
 * @Author Sun Jianda
 * @Date 2022/10/20
 */

@SpringBootTest(classes = CommentApplication.class)
public class OtherTest {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private ShopService shopService;

    @Test
    void strUtilTest() {
        String filename = "abc.jpg";
        // jpg
        System.out.println(StrUtil.subAfter(filename, ".", true));
        // .jpg
        System.out.println(filename.substring(filename.lastIndexOf(".")));
        // jpg
        System.out.println(FileUtil.extName(filename));
    }

    @Test
    void timeTest() {
        LocalDate now = LocalDate.now();
        DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy/MM/dd");
        String formatTime = dateTimeFormatter.format(now);
        System.out.println("/Users/sun/" + formatTime);
    }

    @Test
    void t1() {
        List<Integer> list = Arrays.asList(1, 2, 3, 4, 5);
        String join = StrUtil.join(", ", list);
        System.out.println(join);   // 1, 2, 3, 4, 5
    }

    @Test
    void t2() {
        String key = BLOG_LIKED_KEY + "23";
        System.out.println(stringRedisTemplate.opsForZSet().score(key, "1010"));
    }

    @Test
    void loadShopData() {
        List<Shop> shopList = shopService.list();
        // 1. 店铺按照 TypeId 分组
        Map<Long, List<Shop>> map = shopList.stream().collect(Collectors.groupingBy(Shop::getTypeId));
        // 2. 分批写入 Redis
        for (Map.Entry<Long, List<Shop>> entry : map.entrySet()) {
            Long typeId = entry.getKey();
            String key = "shop:geo:" + typeId;
            List<Shop> shops = entry.getValue();
            List<RedisGeoCommands.GeoLocation<String>> locations = new ArrayList<>(shops.size());
            for (Shop shop : shops) {
                // stringRedisTemplate.opsForGeo().add(key, new Point(shop.getX(), shop.getY()), shop.getId().toString());
                locations.add(new RedisGeoCommands.GeoLocation<>(shop.getId().toString(), new Point(shop.getX(), shop.getY())));
            }
            stringRedisTemplate.opsForGeo().add(key, locations);
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
        System.out.println("size = " + hllSize);    // size = 997593
    }
}
