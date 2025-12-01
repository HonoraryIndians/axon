package com.axon.core_service.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RedissonConfig {

    @Bean
    public RedissonClient redissonClient(
            @Value("${spring.data.redis.host}") String host,
            @Value("${spring.data.redis.port}") int port,
            @Value("${spring.data.redis.password:}") String password) { // Default empty password

        Config config = new Config();
        var singleServerConfig = config.useSingleServer()
              .setAddress("redis://" + host + ":" + port)
              .setConnectionPoolSize(64)
              .setConnectionMinimumIdleSize(10)
              .setRetryAttempts(3)
              .setRetryInterval(1500)
              .setTimeout(3000);

        if (password != null && !password.isBlank()) { // Only set password if it's not empty
            singleServerConfig.setPassword(password);
        }

        return Redisson.create(config);
    }
}
