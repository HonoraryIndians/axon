package com.axon.core_service.service;


import static org.mockito.Mockito.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.assertj.core.api.Assertions.assertThat;


@ExtendWith(MockitoExtension.class)
@DisplayName("RealtimeMetricsService 테스트")
public class RealtimeMetricsServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private SetOperations<String, String> setOperations;

    @InjectMocks
    private RealtimeMetricsService realtimeMetricsService;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForSet()).thenReturn(setOperations);
    }

    @Test
    @DisplayName("참여자 수 조회 테스트")
    void getParticipantCountTest() {
        //given
        Long activityId = 1L;
        String expectedKey = "campaignActivity:1:participants";
        when(redisTemplate.opsForSet()).thenReturn(setOperations);
        when(setOperations.size(expectedKey)).thenReturn(10L);
        //when
        Long count = realtimeMetricsService.getParticipantCount(activityId);
        //then
        assertThat(count).isEqualTo(10L);
        verify(setOperations).size(expectedKey);
    }
}
