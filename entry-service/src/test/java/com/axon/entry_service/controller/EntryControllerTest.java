package com.axon.entry_service.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.axon.entry_service.domain.CampaignActivityMeta;
import com.axon.entry_service.domain.CampaignActivityStatus;
import com.axon.entry_service.domain.ReservationResult;
import com.axon.entry_service.domain.ReservationStatus;
import com.axon.entry_service.dto.EntryRequestDto;
import com.axon.entry_service.service.CampaignActivityMetaService;
import com.axon.entry_service.service.CampaignActivityProducerService;
import com.axon.entry_service.service.EntryReservationService;
import com.axon.messaging.topic.KafkaTopics;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.userdetails.User;

@org.junit.jupiter.api.extension.ExtendWith(MockitoExtension.class)
class EntryControllerTest {

    @Mock
    private CampaignActivityProducerService producer;

    @Mock
    private EntryReservationService reservationService;

    @Mock
    private CampaignActivityMetaService campaignActivityMetaService;

    @InjectMocks
    private EntryController entryController;

    private EntryRequestDto requestDto;
    private User userDetails;
    private CampaignActivityMeta activeMeta;

    @BeforeEach
    void setUp() {
        requestDto = new EntryRequestDto();
        requestDto.setCampaignActivityId(1L);
        requestDto.setProductId(10L);

        userDetails = (User) User.withUsername("42")
                .password("dummy")
                .roles("USER")
                .build();

        activeMeta = new CampaignActivityMeta(1L, 100, CampaignActivityStatus.ACTIVE, null, null, null, false, false);
    }

    @Test
    void createEntry_successReturnsAcceptedAndPublishesEvent() {
        when(campaignActivityMetaService.getMeta(1L)).thenReturn(activeMeta);
        when(reservationService.reserve(eq(1L), eq(42L), eq(activeMeta), any(Instant.class)))
                .thenReturn(ReservationResult.success(1L));

        ResponseEntity<?> response = entryController.createEntry(requestDto,"FAKETOKEN",userDetails);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        ArgumentCaptor<com.axon.messaging.dto.CampaignActivityKafkaProducerDto> captor = ArgumentCaptor.forClass(com.axon.messaging.dto.CampaignActivityKafkaProducerDto.class);
        verify(producer).send(eq(KafkaTopics.CAMPAIGN_ACTIVITY_COMMAND), captor.capture());
        assertThat(captor.getValue().getUserId()).isEqualTo(42L);
    }

    @Test
    void createEntry_duplicateReturnsConflict() {
        when(campaignActivityMetaService.getMeta(1L)).thenReturn(activeMeta);
        when(reservationService.reserve(eq(1L), eq(42L), eq(activeMeta), any(Instant.class)))
                .thenReturn(ReservationResult.duplicated());

        ResponseEntity<?> response = entryController.createEntry(requestDto, "FAKETOKEN",userDetails);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        verify(producer, never()).send(any(), any());
    }

    @Test
    void createEntry_soldOutReturnsGone() {
        when(campaignActivityMetaService.getMeta(1L)).thenReturn(activeMeta);
        when(reservationService.reserve(eq(1L), eq(42L), eq(activeMeta), any(Instant.class)))
                .thenReturn(ReservationResult.soldOut());

        ResponseEntity<?> response = entryController.createEntry(requestDto, "FAKETOKEN",userDetails);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.GONE);
        verify(producer, never()).send(any(), any());
    }

    @Test
    void createEntry_closedReturnsBadRequest() {
        when(campaignActivityMetaService.getMeta(1L)).thenReturn(activeMeta);
        when(reservationService.reserve(eq(1L), eq(42L), eq(activeMeta), any(Instant.class)))
                .thenReturn(ReservationResult.closed());

        ResponseEntity<?> response = entryController.createEntry(requestDto, "FAKETOKEN", userDetails);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        verify(producer, never()).send(any(), any());
    }

    @Test
    void createEntry_metaNotFoundReturnsNotFound() {
        when(campaignActivityMetaService.getMeta(1L)).thenReturn(null);

        ResponseEntity<?> response = entryController.createEntry(requestDto, "FAKETOKEN", userDetails);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        verify(producer, never()).send(any(), any());
    }

    @Test
    void createEntry_errorReturnsInternalServerError() {
        when(campaignActivityMetaService.getMeta(1L)).thenReturn(activeMeta);
        when(reservationService.reserve(eq(1L), eq(42L), eq(activeMeta), any(Instant.class)))
                .thenReturn(ReservationResult.error());

        ResponseEntity<?> response = entryController.createEntry(requestDto, "FAKETOKEN", userDetails);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        verify(producer, never()).send(any(), any());
    }
}
