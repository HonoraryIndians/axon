package com.axon.core_service.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.Duration;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Redis Cache configuration for Spring Cache abstraction.
 * Configures JSON serialization to avoid Serializable requirement.
 */
@Configuration
@EnableCaching
public class CacheConfig {

    /**
     * Configure ObjectMapper for Redis JSON serialization.
     * Only registers JavaTimeModule - GenericJackson2JsonRedisSerializer handles type info.
     */
    private ObjectMapper cacheObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();

        // Register Java 8 date/time module for LocalDateTime serialization
        mapper.registerModule(new JavaTimeModule());

        // NOTE: Do NOT use activateDefaultTyping() here!
        // GenericJackson2JsonRedisSerializer already handles polymorphic types
        // using its own @class property mechanism. Adding activateDefaultTyping
        // causes double type wrapping and deserialization errors.

        return mapper;
    }

    /**
     * Configure RedisCacheManager with JSON serialization.
     *
     * @param connectionFactory Redis connection factory injected by Spring Boot
     * @return configured cache manager
     */
    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        // Create JSON serializer with custom ObjectMapper
        GenericJackson2JsonRedisSerializer jsonSerializer =
            new GenericJackson2JsonRedisSerializer(cacheObjectMapper());

        // Configure cache defaults
        RedisCacheConfiguration config = RedisCacheConfiguration.defaultCacheConfig()
            .entryTtl(Duration.ofHours(1))  // Cache entries expire after 1 hour
            .serializeKeysWith(
                RedisSerializationContext.SerializationPair.fromSerializer(
                    new StringRedisSerializer()
                )
            )
            .serializeValuesWith(
                RedisSerializationContext.SerializationPair.fromSerializer(
                    jsonSerializer
                )
            )
            .disableCachingNullValues();  // Don't cache null results

        return RedisCacheManager.builder(connectionFactory)
            .cacheDefaults(config)
            .build();
    }
}
