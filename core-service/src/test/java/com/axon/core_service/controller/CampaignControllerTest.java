package com.axon.core_service.controller;

import com.axon.core_service.domain.campaign.CampaignStatus;
import com.axon.core_service.domain.dto.campaign.CampaignRequest;

import com.axon.core_service.domain.dto.campaign.CampaignResponse;
import com.axon.core_service.service.CampaignService;
import com.axon.messaging.CampaignType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.mockito.Mock;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CampaignController.class)
class CampaignControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Mock
    private CampaignService campaignService;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void createCampaign_returnsCreatedCampaign() throws Exception {
        CampaignRequest request = CampaignRequest.builder()
                .name("Black Friday")
                .type(CampaignType.FIRST_COME_FIRST_SERVE)
                .targetSegmentId(42L)
                .rewardType("COUPON")
                .rewardPayload("{\"amount\":1000}")
                .startAt(LocalDateTime.now().plusDays(1))
                .endAt(LocalDateTime.now().plusDays(7))
                .status(CampaignStatus.DRAFT)
                .build();

        CampaignResponse response = CampaignResponse.builder()
                .id(1L)
                .name("Black Friday")
                .type(CampaignType.FIRST_COME_FIRST_SERVE)
                .targetSegmentId(42L)
                .rewardType("COUPON")
                .rewardPayload("{\"amount\":1000}")
                .startAt(request.getStartAt())
                .endAt(request.getEndAt())
                .status(CampaignStatus.DRAFT)
                .build();

        Mockito.when(campaignService.createCampaign(any()))
                .thenReturn(response);

        mockMvc.perform(post("/api/v1/campaign")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Black Friday"))
                .andExpect(jsonPath("$.rewardType").value("COUPON"));
    }

    @Test
    void getCampaigns_returnsList() throws Exception {
        CampaignResponse response = CampaignResponse.builder()
                .id(1L)
                .name("Black Friday")
                .type(CampaignType.FIRST_COME_FIRST_SERVE)
                .targetSegmentId(42L)
                .rewardType("COUPON")
                .rewardPayload("{\"amount\":1000}")
                .status(CampaignStatus.DRAFT)
                .build();

        Mockito.when(campaignService.getCampaigns())
                .thenReturn(List.of(response));

        mockMvc.perform(get("/api/v1/campaign"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1L));
    }
}
