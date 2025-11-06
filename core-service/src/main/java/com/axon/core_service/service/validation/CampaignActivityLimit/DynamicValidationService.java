package com.axon.core_service.service.validation.CampaignActivityLimit;

import com.axon.core_service.domain.campaignactivity.CampaignActivity;
import com.axon.core_service.domain.dto.campaignactivity.filter.FilterDetail;
import com.axon.core_service.repository.CampaignActivityRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class DynamicValidationService {
    private final CampaignActivityRepository campaignActivityRepository;
    private final ValidationLimitFactoryService validationLimitFactoryService;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public boolean validate(Long userId, Long campaignActivityId) {
        CampaignActivity campaignActivity = campaignActivityRepository.findById(campaignActivityId)
                .orElseThrow(() -> new IllegalArgumentException("campaignActivityId not found" +  campaignActivityId));
        List<FilterDetail> limitFilter = campaignActivity.getFilters();

        log.info("리미트 필터 확인 limitFilter : {}", limitFilter);
        //filter가 없으면 바로 통과시키기
        if(limitFilter == null || limitFilter.isEmpty()) {return true;}
        try {
            for(FilterDetail filter : limitFilter) {
                String filterName = filter.getType();
                String operator = filter.getOperator() != null ? filter.getOperator() : "BETWEEN";
                List<String> filterValues = filter.getValues();

                //필터 이름과 매핑되는 함수를 호출 함
                ValidationLimitStrategy strategy = validationLimitFactoryService.getStrategy(filterName);
                if(strategy == null) {
                    log.warn("{} 의 전략함수는 존재하지 않습니다.", filterName);
                    continue;
                }

                if(!strategy.validateCampaignActivityLimit(userId, operator , filterValues)) {
                    log.info("{}번 사용자가 {} 조건을 만족하지 못했습니다.", userId, filterName);
                    return false;
                }
            }
        } catch (Exception err) {
            log.error("Dynamic Validation Error (userId: {})", userId, err);
            return false;
        }
        return true;
    }
}
