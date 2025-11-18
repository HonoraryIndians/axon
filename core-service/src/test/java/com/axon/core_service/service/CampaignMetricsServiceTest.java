package com.axon.core_service.service;

import com.axon.core_service.domain.campaignactivityentry.CampaignActivityEntryStatus;
import com.axon.core_service.repository.CampaignActivityEntryRepository;
import com.axon.core_service.repository.EventOccurrenceRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("CampaignMetricsService 테스트")
public class CampaignMetricsServiceTest {

    @Mock
    private CampaignActivityEntryRepository entryRepository;
    @Mock
    private EventOccurrenceRepository eventOccurrenceRepository;

    @InjectMocks
    private CampaignMetricsService service;

    @Test
    @DisplayName("승인카운트_성공")
    void getApprovedCountTest() {
        //given
        Long activityId = 1L;
        LocalDateTime startTime = LocalDateTime.of(2024, 1, 1, 0, 0);
        LocalDateTime endTime = LocalDateTime.of(2024, 1, 31, 23, 59);
        when(entryRepository.countByCampaignActivity_IdAndStatusAndCreatedAtBetween(
                activityId,
                CampaignActivityEntryStatus.APPROVED,
                startTime,
                endTime
        )).thenReturn(100L);
        //when
        Long count = service.getApprovedCount(activityId, startTime, endTime);
        //then
        assertThat(count).isEqualTo(100L);
        verify(entryRepository).countByCampaignActivity_IdAndStatusAndCreatedAtBetween(
                activityId,
                CampaignActivityEntryStatus.APPROVED,
                startTime,
                endTime
        );
    }

    @Test
    @DisplayName("없을떄 0반환")
    void getApprovedCount_NoData() {
        //given
        Long activityId = 1L;
        LocalDateTime startTime = LocalDateTime.of(2024, 2, 1, 0, 0);
        LocalDateTime endTime = LocalDateTime.of(2024, 2, 28, 23, 59);
        when(entryRepository.countByCampaignActivity_IdAndStatusAndCreatedAtBetween(
                activityId,
                CampaignActivityEntryStatus.APPROVED,
                startTime,
                endTime
        )).thenReturn(0L);

        //when
        Long count = service.getApprovedCount(activityId, startTime, endTime);

        //then
        assertThat(count).isEqualTo(0L);
        verify(entryRepository).countByCampaignActivity_IdAndStatusAndCreatedAtBetween(
                activityId,
                CampaignActivityEntryStatus.APPROVED,
                startTime,
                endTime
        );
    }
}
