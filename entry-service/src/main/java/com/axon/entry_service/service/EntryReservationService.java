package com.axon.entry_service.service;

import com.axon.entry_service.domain.CampaignActivityMeta;
import com.axon.entry_service.domain.ReservationResult;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class EntryReservationService {

    private final StringRedisTemplate redisTemplate;

    public ReservationResult reserve(long campaignActivityId,
                                     long userId,
                                     CampaignActivityMeta meta,
                                     Instant requestedAt) {

        if (meta == null) {
            return ReservationResult.error();
        }

        if (!meta.isParticipatable(requestedAt)) {
            return ReservationResult.closed();
        }

        String userKey = String.valueOf(userId);
        String userSetKey = participantsKey(campaignActivityId);
        String counterKey = counterKey(campaignActivityId);

        Long added = redisTemplate.opsForSet().add(userSetKey, userKey);
        if (added == null) {
            return ReservationResult.error();
        }
        if (added == 0L) {
            return ReservationResult.duplicated();
        }

        Long order = redisTemplate.opsForValue().increment(counterKey);
        Integer limitCount = meta.limitCount();
        if (order == null || (limitCount != null && order > limitCount)) {
            redisTemplate.opsForSet().remove(userSetKey, userKey);
            return ReservationResult.soldOut();
        }

        return ReservationResult.success(order);
    }

    public void rollbackReservation(long campaignActivityId, long userId) {
        String userKey = String.valueOf(userId);
        redisTemplate.opsForSet().remove(participantsKey(campaignActivityId), userKey);
    }

    private String participantsKey(long campaignActivityId) {
        return "campaign:%d:users".formatted(campaignActivityId);
    }

    private String counterKey(long campaignActivityId) {
        return "campaign:%d:counter".formatted(campaignActivityId);
    }
}
