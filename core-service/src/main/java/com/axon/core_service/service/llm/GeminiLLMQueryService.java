package com.axon.core_service.service.llm;

import com.axon.core_service.domain.dashboard.DashboardPeriod;
import com.axon.core_service.domain.dto.dashboard.CampaignDashboardResponse;
import com.axon.core_service.service.CohortAnalysisService;
import com.axon.core_service.service.DashboardService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import com.axon.core_service.domain.dto.llm.DashboardQueryResponse;
import com.axon.core_service.domain.dto.dashboard.DashboardResponse;
import com.axon.core_service.domain.dto.dashboard.GlobalDashboardResponse;
import com.axon.core_service.domain.dto.dashboard.OverviewData;


import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@Primary
@Profile("gemini | prod")
@RequiredArgsConstructor
public class GeminiLLMQueryService implements LLMQueryService {

    private final DashboardService dashboardService;
    private final CohortAnalysisService cohortAnalysisService;
    private final RestClient.Builder restClientBuilder;
    private final ObjectMapper objectMapper;

    @Value("${gemini.api.key}")
    private String apiKey;

    private static final String GEMINI_URL = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent";

    @PostConstruct
    public void init() {
        log.info("Gemini API Key loaded: {}", (apiKey != null && !apiKey.isBlank()) ? "YES (" + apiKey.substring(0, Math.min(apiKey.length(), 4)) + "***)" : "NO");
    }

    @Override
    public DashboardQueryResponse processQuery(Long campaignId, String query) {
        log.info("Gemini LLM processing query for campaign: {}", campaignId);

        CampaignDashboardResponse dashboardData = dashboardService.getDashboardByCampaign(campaignId);
        Object cohortData = null;
        if (shouldFetchCohort(query) && !dashboardData.activities().isEmpty()) {
            Long representativeActivityId = dashboardData.activities().get(0).activityId();
            log.info("Fetching cohort data for representative activity: {}", representativeActivityId);
            cohortData = cohortAnalysisService.analyzeCohortByActivity(representativeActivityId, null, null);
        }

        return processQueryInternal(query, dashboardData, cohortData, dashboardData.overview());
    }

    @Override
    public DashboardQueryResponse processQueryByActivity(Long activityId, String query) {
        log.info("Gemini LLM processing query for activity: {}", activityId);

        DashboardResponse dashboardData = dashboardService.getDashboardByActivity(activityId, DashboardPeriod.SEVEN_DAYS, null, null);
        Object cohortData = null;
        if (shouldFetchCohort(query)) {
            log.info("Fetching cohort data for activity: {}", activityId);
            cohortData = cohortAnalysisService.analyzeCohortByActivity(activityId, null, null);
        }

        return processQueryInternal(query, dashboardData, cohortData, dashboardData.overview());
    }

    @Override
    public DashboardQueryResponse processGlobalQuery(String query) {
        log.info("Gemini LLM processing global query.");

        GlobalDashboardResponse dashboardData = dashboardService.getGlobalDashboard();
        // Global level does not have specific cohort for now, so cohortData is null
        return processQueryInternal(query, dashboardData, null, dashboardData.overview());
    }

    private DashboardQueryResponse processQueryInternal(String query, Object dashboardData, Object cohortData, OverviewData overview) {
        Map<String, Object> contextMap = new HashMap<>();
        contextMap.put("dashboard", dashboardData);
        if (cohortData != null) {
            contextMap.put("cohortAnalysis", cohortData);
        }

        String contextJson = serializeData(contextMap);
        String prompt = buildPrompt(query, contextJson);
        String geminiResponse = callGeminiApi(prompt);

        return new DashboardQueryResponse(geminiResponse, overview, "GEMINI_GENERATED");
    }

    private boolean shouldFetchCohort(String query) {
        String lower = query.toLowerCase();
        return lower.contains("ltv") || lower.contains("cohort") || lower.contains("retention") 
            || lower.contains("재구매") || lower.contains("코호트") || lower.contains("생애");
    }

    private String buildPrompt(String userQuery, String dataContext) {
        return """
            You are an expert Data Analyst for an E-commerce platform.
            Answer the user's question based ONLY on the provided JSON data.
            
            Context Data:
            %s
            
            User Question: "%s"
            
            Instructions:
            - Be concise and professional.
            - If the data is not available in the context, say "없는 정보에 관한 질문입니다."
            - Format numbers nicely (e.g., 1,000,000).
            - Answer in Korean.
            """.formatted(dataContext, userQuery);
    }

    private String callGeminiApi(String prompt) {
        try {
            var requestBody = Map.of(
                "contents", List.of(
                    Map.of("parts", List.of(
                        Map.of("text", prompt)
                    ))
                )
            );

            JsonNode response = restClientBuilder.build()
                .post()
                .uri(GEMINI_URL + "?key=" + apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(requestBody)
                .retrieve()
                .body(JsonNode.class);

            return response.path("candidates").get(0)
                .path("content").path("parts").get(0)
                .path("text").asText();

        } catch (Exception e) {
            log.error("Gemini API call failed", e);
            return "죄송합니다. AI 분석 서버와 연결할 수 없습니다. (Error: " + e.getMessage() + ")";
        }
    }

    private String serializeData(Object data) {
        try {
            return objectMapper.writeValueAsString(data);
        } catch (Exception e) {
            return "{}";
        }
    }
}
