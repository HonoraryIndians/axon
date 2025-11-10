package com.axon.core_service.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

@Configuration
public class RedisConfig {
    /**
     * Create and configure a RedisTemplate for String keys and JSON-serialized values.
     *
     * @param redisConnectionFactory the factory used to obtain Redis connections for the template
     * @return a RedisTemplate<String, Object> configured with String serialization for keys and hash keys,
     *         and JSON serialization for values and hash values
     */
    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory redisConnectionFactory) {
        RedisTemplate<String, Object> redisTemplate = new RedisTemplate<>();
        redisTemplate.setConnectionFactory(redisConnectionFactory);

        //Key -> String으로 직렬화
        redisTemplate.setKeySerializer(new StringRedisSerializer());

        // Value는 JSON으로 직렬화 (객체 지정을 위함)
        redisTemplate.setValueSerializer(new GenericJackson2JsonRedisSerializer());

        redisTemplate.setHashKeySerializer(new StringRedisSerializer());
        redisTemplate.setHashValueSerializer(new GenericJackson2JsonRedisSerializer());
        return redisTemplate;
    }
}