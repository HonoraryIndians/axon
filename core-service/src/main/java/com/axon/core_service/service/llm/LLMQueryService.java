package com.axon.core_service.service.llm;

import com.axon.core_service.domain.dto.dashboard.CampaignDashboardResponse;
import com.axon.core_service.domain.dto.dashboard.OverviewData;
import com.axon.core_service.domain.dto.llm.DashboardQueryResponse;
import com.axon.core_service.service.DashboardService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.text.NumberFormat;
import java.util.Locale;

@Service
@RequiredArgsConstructor
@Slf4j
public class LLMQueryService {

    private final DashboardService dashboardService;

    public DashboardQueryResponse processQuery(Long campaignId, String query) {
        String lowerQuery = query.toLowerCase();
        log.info("Processing query: '{}' for campaign: {}", query, campaignId);

        // 1. Parse Intent (Mock/Keyword based)
        String intent = determineIntent(lowerQuery);

        // 2. Fetch Data
        // For now, we fetch the full campaign dashboard data and extract what we need
        // In a real LLM scenario, we might fetch specific data based on intent
        CampaignDashboardResponse dashboardData = dashboardService.getDashboardByCampaign(campaignId);
        OverviewData overview = dashboardData.getOverview();

        // 3. Generate Answer
        String answer = generateAnswer(intent, overview, lowerQuery);

        return new DashboardQueryResponse(answer, overview, intent);
    }

    private String determineIntent(String query) {
        if (query.contains("visit") || query.contains("traffic") || query.contains("방문")) {
            return "METRIC_VISITS";
        } else if (query.contains("purchase") || query.contains("sale") || query.contains("buy") || query.contains("구매")
                || query.contains("결제")) {
            return "METRIC_PURCHASES";
        } else if (query.contains("gmv") || query.contains("revenue") || query.contains("sales") || query.contains("매출")
                || query.contains("거래액")) {
            return "METRIC_GMV";
        } else if (query.contains("roas") || query.contains("return") || query.contains("효율")) {
            return "METRIC_ROAS";
        } else if (query.contains("conversion") || query.contains("rate") || query.contains("전환")) {
            return "METRIC_CONVERSION";
        }
        return "UNKNOWN";
    }

    private String generateAnswer(String intent, OverviewData data, String query) {
        NumberFormat numberFormat = NumberFormat.getInstance(Locale.US);
        NumberFormat currencyFormat = NumberFormat.getCurrencyInstance(Locale.KOREA); // Assuming KRW for now

        switch (intent) {
            case "METRIC_VISITS":
                return String.format("Total visits for this campaign are %s.", numberFormat.format(data.totalVisits()));
            case "METRIC_PURCHASES":
                return String.format("There have been %s purchases so far.", numberFormat.format(data.purchaseCount()));
            case "METRIC_GMV":
                return String.format("The total GMV (Gross Merchandise Value) is %s.",
                        currencyFormat.format(data.gmv()));
            case "METRIC_ROAS":
                return String.format("The ROAS (Return on Ad Spend) is %.2f%%.", data.roas());
            case "METRIC_CONVERSION":
                return String.format("The conversion rate is %.2f%%.", data.getConversionRate());
            default:
                return "I'm sorry, I didn't understand your question. Try asking about visits, purchases, GMV, or ROAS.";
        }
    }
}
