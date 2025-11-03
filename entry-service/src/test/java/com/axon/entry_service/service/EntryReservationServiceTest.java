package com.axon.entry_service.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.axon.entry_service.domain.CampaignActivityMeta;
import com.axon.entry_service.domain.CampaignActivityStatus;
import com.axon.entry_service.domain.ReservationResult;
import com.axon.entry_service.domain.ReservationStatus;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

@org.junit.jupiter.api.extension.ExtendWith(MockitoExtension.class)
class EntryReservationServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @InjectMocks
    private EntryReservationService reservationService;

    private CampaignActivityMeta activeMeta;

    @BeforeEach
    void setUp() {
        activeMeta = new CampaignActivityMeta(1L, 3, CampaignActivityStatus.ACTIVE, null, null);
    }

    @Test
    void reserveSuccess() {
        SetOperations<String, String> setOps = mock(SetOperations.class);
        ValueOperations<String, String> valueOps = mock(ValueOperations.class);
        when(redisTemplate.opsForSet()).thenReturn(setOps);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(setOps.add(any(), any())).thenReturn(1L);
        when(valueOps.increment(any())).thenReturn(1L);

        ReservationResult result = reservationService.reserve(1L, 100L, activeMeta, Instant.now());

        assertThat(result.status()).isEqualTo(ReservationStatus.SUCCESS);
        assertThat(result.order()).isEqualTo(1L);
    }

    @Test
    void reserveDuplicatedWhenUserAlreadyExists() {
        SetOperations<String, String> setOps = mock(SetOperations.class);
        when(redisTemplate.opsForSet()).thenReturn(setOps);
        when(setOps.add(any(), any())).thenReturn(0L);

        ReservationResult result = reservationService.reserve(1L, 100L, activeMeta, Instant.now());

        assertThat(result.status()).isEqualTo(ReservationStatus.DUPLICATED);
    }

    @Test
    void reserveSoldOutWhenLimitExceeded() {
        SetOperations<String, String> setOps = mock(SetOperations.class);
        ValueOperations<String, String> valueOps = mock(ValueOperations.class);
        when(redisTemplate.opsForSet()).thenReturn(setOps);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(setOps.add(any(), any())).thenReturn(1L);
        when(valueOps.increment(any())).thenReturn(5L);

        ReservationResult result = reservationService.reserve(1L, 100L, activeMeta, Instant.now());

        assertThat(result.status()).isEqualTo(ReservationStatus.SOLD_OUT);
        verify(setOps).remove(any(), eq(String.valueOf(100L)));
    }

    @Test
    void reserveClosedWhenActivityInactive() {
        CampaignActivityMeta meta = new CampaignActivityMeta(1L, 3, CampaignActivityStatus.PAUSED, null, null);

        ReservationResult result = reservationService.reserve(1L, 100L, meta, Instant.now());

        assertThat(result.status()).isEqualTo(ReservationStatus.CLOSED);
    }

    @Test
    void reserveClosedWhenOutsideSchedule() {
        CampaignActivityMeta meta = new CampaignActivityMeta(
                1L,
                3,
                CampaignActivityStatus.ACTIVE,
                Instant.now().plusSeconds(3600).atZone(java.time.ZoneId.systemDefault()).toLocalDateTime(),
                null
        );

        ReservationResult result = reservationService.reserve(1L, 100L, meta, Instant.now());

        assertThat(result.status()).isEqualTo(ReservationStatus.CLOSED);
    }

    @Test
    void reserveErrorWhenMetaNull() {
        ReservationResult result = reservationService.reserve(1L, 100L, null, Instant.now());

        assertThat(result.status()).isEqualTo(ReservationStatus.ERROR);
    }
}
