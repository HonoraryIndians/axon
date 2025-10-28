//package com.axon.core_service.controller;
//
//import com.axon.core_service.domain.dto.campaign.CampaignRequest;
//import com.axon.core_service.domain.dto.campaign.CampaignResponse;
//import com.axon.core_service.service.CampaignService;
//import com.axon.core_service.config.auth.JwtTokenProvider;
//import org.junit.jupiter.api.Test;
//import org.mockito.ArgumentCaptor;
//import org.mockito.Mockito;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
//import org.springframework.boot.test.mock.mockito.MockBean;
//import org.springframework.test.web.servlet.MockMvc;
//
//import java.time.LocalDateTime;
//
//import static org.junit.jupiter.api.Assertions.assertEquals;
//import static org.mockito.Mockito.verify;
//
//@WebMvcTest(CampaignController.class)
//class CampaignControllerTest {
//
//    @Autowired
//    private MockMvc mockMvc;
//
//    @MockBean // @Mock 대신 @MockBean 사용
//    private CampaignService campaignService;
//
//    @MockBean // JwtTokenProvider Mocking 추가
//    private JwtTokenProvider jwtTokenProvider;
//
//    @Test
//    void createCampaign_returnsCreatedCampaign() throws Exception {
//        LocalDateTime startAt = LocalDateTime.now().plusDays(1).withNano(0);
//        LocalDateTime endAt = startAt.plusDays(7);
//
//        CampaignRequest request = CampaignRequest.builder()
//                .name("Black Friday")
//                .targetSegmentId(42L)
//                .rewardType("COUPON")
//                .rewardPayload("{\"amount\":1000}")
//                .startAt(startAt)
//                .endAt(endAt)
//                .build();
//
//        CampaignResponse response = CampaignResponse.builder()
//                .id(1L)
//                .name(request.getName())
//                .targetSegmentId(request.getTargetSegmentId())
//                .rewardType(request.getRewardType())
//                .rewardPayload(request.getRewardPayload())
//                .startAt(startAt)
//                .endAt(endAt)
//                .build();
//
//        Mockito.when(campaignService.createCampaign(Mockito.any(CampaignRequest.class))).thenReturn(response);
//
//        mockMvc.perform(
//                        post("/api/v1/campaign")
//                                .contentType(MediaType.APPLICATION_JSON)
//                                .content(objectMapper.writeValueAsString(request))
//                )
//                .andExpect(status().isOk())
//                .andExpect(jsonPath("$.id").value(response.getId()))
//                .andExpect(jsonPath("$.name").value(request.getName()))
//                .andExpect(jsonPath("$.rewardType").value(request.getRewardType()));
//
//        ArgumentCaptor<CampaignRequest> captor = ArgumentCaptor.forClass(CampaignRequest.class);
//        verify(campaignService).createCampaign(captor.capture());
//        CampaignRequest capturedRequest = captor.getValue();
//        assertEquals(request.getName(), capturedRequest.getName());
//        assertEquals(request.getTargetSegmentId(), capturedRequest.getTargetSegmentId());
//        assertEquals(request.getRewardPayload(), capturedRequest.getRewardPayload());
//    }
//
//    @Test
//    void getCampaigns_returnsList() throws Exception {
//        CampaignResponse response = CampaignResponse.builder()
//                .id(1L)
//                .name("Black Friday")
//                .targetSegmentId(42L)
//                .rewardType("COUPON")
//                .rewardPayload("{\"amount\":1000}")
//                .build();
//
//        Mockito.when(campaignService.getCampaigns()).thenReturn(List.of(response));
//
//        mockMvc.perform(get("/api/v1/campaign"))
//                .andExpect(status().isOk())
//                .andExpect(jsonPath("$[0].id").value(response.getId()))
//                .andExpect(jsonPath("$[0].name").value(response.getName()));
//
//        verify(campaignService).getCampaigns();
//    }
//}
