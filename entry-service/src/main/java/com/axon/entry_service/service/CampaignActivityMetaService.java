package com.axon.entry_service.service;

import com.axon.entry_service.config.auth.JwtTokenProvider;
import com.axon.entry_service.domain.CampaignActivityMeta;
import com.axon.entry_service.dto.CampaignActivitySummaryResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

@Service
@RequiredArgsConstructor
@Slf4j
public class CampaignActivityMetaService {

    private static final Duration CACHE_TTL = Duration.ofMinutes(5);

    private final WebClient campaignWebClient;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final JwtTokenProvider jwtTokenProvider;

    public CampaignActivityMeta getMeta(Long campaignActivityId) {
        Objects.requireNonNull(campaignActivityId, "campaignActivityId must not be null");
        String cacheKey = metaCacheKey(campaignActivityId);

        String cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            try {
                return objectMapper.readValue(cached, CampaignActivityMeta.class);
            } catch (JsonProcessingException e) {
                log.warn("Failed to deserialize campaign meta cache. key={}", cacheKey, e);
                redisTemplate.delete(cacheKey);
            }
        }

        CampaignActivitySummaryResponse response = fetchCampaignActivity(campaignActivityId);
        if (response == null) {
            return null;
        }

        List<Map<String, Object>> filters = response.filters();
        boolean hasFastValidation = false;
        boolean hasHeavyValidation = false;
        if(filters != null) {
            for(Map<String, Object> filter : filters) {
                String phase = (String) filter.get("phase");
                if("FAST".equals(phase)) {hasFastValidation = true;}
                else if("HEAVY".equals(phase)) {hasHeavyValidation = true;}
                if(hasFastValidation && hasHeavyValidation) {break;}
            }
        }

        CampaignActivityMeta meta = new CampaignActivityMeta(
                response.id(),
                response.limitCount(),
                response.status(),
                response.startDate(),
                response.endDate(),
                response.filters(),
                hasFastValidation,
                hasHeavyValidation
        );

        try {
            redisTemplate.opsForValue()
                    .set(cacheKey, objectMapper.writeValueAsString(meta), CACHE_TTL);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize campaign meta cache. key={}", cacheKey, e);
        }

        return meta;
    }

    public void evictMeta(Long campaignActivityId) {
        redisTemplate.delete(metaCacheKey(campaignActivityId));
    }

    private CampaignActivitySummaryResponse fetchCampaignActivity(Long campaignActivityId) {
        try {
            String accessToken = jwtTokenProvider.generateAccessToken(0L);// system user
            return campaignWebClient.get()
                    .uri("/api/v1/campaign/activities/{id}", campaignActivityId)
                    .headers(headers -> headers.setBearerAuth(accessToken))
                    .retrieve()
                    .bodyToMono(CampaignActivitySummaryResponse.class)
                    .block();
        } catch (WebClientResponseException.NotFound e) {
            log.warn("Campaign activity not found. id={}", campaignActivityId);
            return null;
        } catch (Exception e) {
            log.error("Failed to fetch campaign activity meta. id={}", campaignActivityId, e);
            throw new IllegalStateException("Failed to fetch campaign activity meta", e);
        }
    }

    private String metaCacheKey(Long campaignActivityId) {
        return "campaign:%s:meta".formatted(campaignActivityId);
    }
}
