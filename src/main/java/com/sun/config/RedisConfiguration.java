package com.sun.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @Author Sun Jianda
 * @Date 2022/10/17
 */

@Configuration
public class RedisConfiguration {
    @Bean
    public RedissonClient redissonClient() {
        // 配置类
        Config config = new Config();
        // 添加 Redis 地址：此处是单节点地址，也可以通过 config.useClusterServers() 添加集群地址
        config.useSingleServer().setAddress("redis://127.0.0.1:6379").setPassword("root");
        // 创建客户端
        return Redisson.create(config);
    }
}
