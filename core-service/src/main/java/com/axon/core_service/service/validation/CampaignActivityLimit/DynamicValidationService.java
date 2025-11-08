package com.axon.core_service.service.validation.CampaignActivityLimit;

import com.axon.core_service.domain.campaignactivity.CampaignActivity;
import com.axon.core_service.domain.dto.campaignactivity.filter.FilterDetail;
import com.axon.core_service.repository.CampaignActivityRepository;
import com.axon.messaging.dto.validation.ValidationResponse;
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

    @Transactional(readOnly = true)
    public ValidationResponse validate(Long userId, Long campaignActivityId) {
        CampaignActivity campaignActivity = campaignActivityRepository.findById(campaignActivityId)
                .orElseThrow(() -> new IllegalArgumentException("campaignActivityId not found" +  campaignActivityId));
        List<FilterDetail> limitFilter = campaignActivity.getFilters();

        log.info("리미트 필터 확인 limitFilter : {}", limitFilter);
        //filter가 없으면 바로 통과시키기
        if(limitFilter == null || limitFilter.isEmpty()) {return ValidationResponse.builder().eligible(true).build();}
        try {
            for(FilterDetail filter : limitFilter) {
                String filterName = filter.getType();
                String operator = filter.getOperator() != null ? filter.getOperator() : "BETWEEN";
                List<String> filterValues = filter.getValues();

                //필터 이름과 매핑되는 함수를 호출 함
                ValidationLimitStrategy strategy = validationLimitFactoryService.getStrategy(filterName);
                if(strategy == null) {
                    log.warn("{} 의 전략함수는 존재하지 않습니다.", filterName);
                    return ValidationResponse.builder().eligible(false).errorMessage("응모 요청 페이지에 오류가 발생했습니다.").build();
                }
                return strategy.validateCampaignActivityLimit(userId, operator, filterValues);
            }
        } catch (Exception err) {
            log.error("Dynamic Validation Error (userId: {})", userId, err);
            return ValidationResponse.builder().eligible(false).errorMessage("해당 페이지의 참여조건을 알 수 없습니다.").build();
        }
        return ValidationResponse.builder().eligible(false).errorMessage("UNKNOWN_ERROR").build();
    }
}
