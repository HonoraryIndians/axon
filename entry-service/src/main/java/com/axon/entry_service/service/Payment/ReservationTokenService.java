package com.axon.entry_service.service.Payment;

import com.axon.entry_service.dto.Payment.ReservationTokenPayload;
import com.axon.messaging.CampaignActivityType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class ReservationTokenService {
    private final RedisTemplate<String, Object> redisTemplate;
    private static final String TOKEN_PREFIX = "RESERVATION_TOKEN:";
    //TODO: TTL 시간 상의 현재 5분
    private static final long TOKEN_TTL_MINUTES = 5;

    public String issueToken(ReservationTokenPayload payload) {
        String token = UUID.randomUUID().toString();
        String redisKey = TOKEN_PREFIX + token;

        redisTemplate.opsForValue().set(redisKey, payload, TOKEN_TTL_MINUTES, TimeUnit.MINUTES);
        return token;
    }

    // 토큰을 통해 redis에 결제상황이 유효한지 확인함, NULL이거나 empty면 타임 아웃
    public Optional<ReservationTokenPayload> getPayloadFromToken(String token) {
        String redisKey = TOKEN_PREFIX + token;
        Object payload = redisTemplate.opsForValue().get(redisKey);
        return Optional.ofNullable((ReservationTokenPayload) payload);
    }

    // 토큰 삭제
    public void removeToken(String token) {
        String redisKey = TOKEN_PREFIX + token;
        redisTemplate.delete(redisKey);
    }
}
