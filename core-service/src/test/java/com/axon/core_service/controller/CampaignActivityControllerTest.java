package com.axon.core_service.controller;

import com.axon.core_service.domain.dto.campaignactivity.CampaignActivityRequest;
import com.axon.core_service.domain.dto.campaignactivity.CampaignActivityResponse;
import com.axon.core_service.domain.dto.campaignactivity.CampaignActivityStatus;
import com.axon.messaging.CampaignActivityType;
import com.axon.core_service.service.CampaignActivityService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CampaignActivityControllerTest {

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private MockMvc mockMvc;
    private CampaignActivityService campaignActivityService;

    @BeforeEach
    void setUp() {
        campaignActivityService = Mockito.mock(CampaignActivityService.class);
        CampaignActivityController controller = new CampaignActivityController(campaignActivityService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .build();
    }

    @Test
    void createCampaignActivity_returnsCreatedActivity() throws Exception {
        LocalDateTime startDate = LocalDateTime.now().plusDays(1).withNano(0);
        LocalDateTime endDate = startDate.plusDays(3);

        CampaignActivityRequest request = CampaignActivityRequest.builder()
                .name("선착순")
                .limitCount(100)
                .status(CampaignActivityStatus.DRAFT)
                .startDate(startDate)
                .endDate(endDate)
                .activityType(CampaignActivityType.FIRST_COME_FIRST_SERVE)
                .build();

        CampaignActivityResponse response = CampaignActivityResponse.builder()
                .id(10L)
                .campaignId(1L)
                .name(request.getName())
                .limitCount(request.getLimitCount())
                .status(request.getStatus())
                .startDate(startDate)
                .endDate(endDate)
                .activityType(request.getActivityType())
                .participantCount(0L)
                .build();

        Mockito.when(campaignActivityService.createCampaignActivity(Mockito.eq(1L), Mockito.any()))
                .thenReturn(response);

        mockMvc.perform(
                        post("/api/v1/campaign/{campaignId}/activities", 1L)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(response.getId()))
                .andExpect(jsonPath("$.name").value(request.getName()))
                .andExpect(jsonPath("$.status").value(request.getStatus().name()));

        ArgumentCaptor<CampaignActivityRequest> captor = ArgumentCaptor.forClass(CampaignActivityRequest.class);
        verify(campaignActivityService).createCampaignActivity(Mockito.eq(1L), captor.capture());
        CampaignActivityRequest captured = captor.getValue();
        assertEquals(request.getName(), captured.getName());
        assertEquals(request.getLimitCount(), captured.getLimitCount());
        assertEquals(request.getActivityType(), captured.getActivityType());
    }

    @Test
    void getCampaignActivities_returnsList() throws Exception {
        CampaignActivityResponse activity = CampaignActivityResponse.builder()
                .id(11L)
                .campaignId(3L)
                .name("웰컴")
                .limitCount(50)
                .status(CampaignActivityStatus.ACTIVE)
                .activityType(CampaignActivityType.FIRST_COME_FIRST_SERVE)
                .participantCount(20L)
                .build();

        Mockito.when(campaignActivityService.getCampaignActivities(3L)).thenReturn(List.of(activity));

        mockMvc.perform(get("/api/v1/campaign/{campaignId}/activities", 3L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(activity.getId()))
                .andExpect(jsonPath("$[0].participantCount").value(activity.getParticipantCount()));

        verify(campaignActivityService).getCampaignActivities(3L);
    }
}
