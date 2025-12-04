package com.axon.entry_service.service.redisListener;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.KeyExpirationEventMessageListener;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;

@Slf4j
@Component
public class ReservationExpirationListener extends KeyExpirationEventMessageListener {
    private final StringRedisTemplate redisTemplate;

    public ReservationExpirationListener(RedisMessageListenerContainer listener, StringRedisTemplate redisTemplate) {
        super(listener);
        this.redisTemplate = redisTemplate;
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        String expiredKey =  message.toString(); // 1차 토큰 전체 문장

        // 1차 토큰 만료인지 확인
        if(!expiredKey.startsWith("RESERVATION_TOKEN:")) {return;}

        try {
            String tokenValue = expiredKey.replace("RESERVATION_TOKEN:", "");
            String decoded = new String(Base64.getDecoder().decode(tokenValue), StandardCharsets.UTF_8);

            String[] parts = decoded.split(":");
            if(parts.length < 2) {
                log.warn("Invalid token format ignored: {}", decoded);
                return;
            }

            String userId = parts[0];
            String campaignActivityId = parts[1];

            // 각 파드간 중복 실행 방지용
            String lockKey = "processed:restore:" + tokenValue;
            Boolean isWinner = redisTemplate.opsForValue()
                    .setIfAbsent(lockKey, "1", Duration.ofMinutes(1));

            if(Boolean.TRUE.equals(isWinner)) {
                String counterKey = "campaign:" + campaignActivityId + ":counter";
                Long remaining = redisTemplate.opsForValue().decrement(counterKey);

                String usersKey = "campaign:" + campaignActivityId + ":users";
                redisTemplate.opsForSet().remove(usersKey, userId);

                log.info("♻️ Stock Restored! Activity: {}, User: {}, Remaining: {}", campaignActivityId, userId, remaining);
            } else {
                log.debug("Another pod Accessed and Skipped decrease request");
            }
        } catch (Exception e) {
            log.error("1차 토큰 만료로 인한 재고 복구 실패 token={}", expiredKey, e);
        }
    }
}
