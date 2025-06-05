package com.sun.comment;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.comment.entity.User;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import javax.annotation.Resource;

@SpringBootTest(classes = App.class)
class AppTests {
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
}
