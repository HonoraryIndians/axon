package com.axon.core_service.service;

import com.axon.core_service.domain.campaignactivity.CampaignActivity;
import com.axon.core_service.repository.CampaignActivityRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RealtimeMetricsService {
    private final StringRedisTemplate redisTemplate;
    private final CampaignActivityRepository campaignActivityRepository;

    public Long getParticipantCount(Long activityId) {
        String key = "campaign:" + activityId + ":counter";
        String value = redisTemplate.opsForValue().get(key);
        return value !=  null ? Long.parseLong(value) : 0L;
    }

    public Long getRemainingStock(Long participantCount, Long totalStock) {
        return Math.max(0, totalStock - participantCount);
    }

}
