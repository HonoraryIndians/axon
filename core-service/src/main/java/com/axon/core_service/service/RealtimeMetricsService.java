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
        //TODO: Redis Set 크기 조회
        String key = "campaignActivity:" + activityId + ":participants";
        return redisTemplate.opsForSet().size(key);
    }

    public Long getRemainingStock(Long activityId) {
        //TODO: limit 조회 (MySQL)
        Long limit = Long.valueOf(campaignActivityRepository.findById(activityId)
                .map(CampaignActivity::getLimitCount)
                .orElse(0));
        //TOOD: 참여자 수 조회
        Long participantCount = getParticipantCount(activityId);
        //TODO: limit - 참여자 수 계산
        return Math.max(0, limit - participantCount);
    }

}
