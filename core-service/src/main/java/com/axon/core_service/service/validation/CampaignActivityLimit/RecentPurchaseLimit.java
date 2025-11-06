package com.axon.core_service.service.validation.CampaignActivityLimit;

import com.axon.core_service.domain.user.UserSummary;
import com.axon.core_service.repository.UserSummaryRepository;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class RecentPurchaseLimit implements ValidationLimitStrategy {
    private final UserSummaryRepository userSummaryRepository;

    @Override
    public String getLimitName() {
        return "RECENT_PURCHASE";
    }

    @Override
    public boolean validateCampaignActivityLimit (Long userId, String operator, List<String> values) {
        Optional<UserSummary> userSummaryOpt = userSummaryRepository.findById(userId);
        if(userSummaryOpt.isEmpty() || userSummaryOpt.get().getLastPurchaseAt() == null) {return false;}
        Instant lastPurchaseAt = userSummaryOpt.get().getLastPurchaseAt();

        if(values==null || values.isEmpty()) {return false;}
        log.info("oper {} || values: {}", operator, values);
        Instant startDateTime = Instant.parse(values.getFirst());
        Instant endDateTime = values.size() == 2 ? Instant.parse(values.get(1)) : null;

        switch(operator) {
            case "BETWEEN":
                if(endDateTime == null) return false;
                return !lastPurchaseAt.isBefore(startDateTime) && !lastPurchaseAt.isAfter(endDateTime);
            case "NOT_BETWEEN":
                if(endDateTime == null) return false;
                return lastPurchaseAt.isBefore(startDateTime) || lastPurchaseAt.isAfter(endDateTime);
            case "GTE": //이상
                if (values.size() != 1) return false;
                return !lastPurchaseAt.isBefore(startDateTime);
            case "LTE": // 이하
                log.info("LTE 진입 성공, 크기 비교 전");
                if (values.size() != 1) return false;
                log.info("last: {} ||start: {}", lastPurchaseAt, startDateTime);
                log.info("value 값 1개임을 인증, 비교 값 {}", !lastPurchaseAt.isAfter(startDateTime));
                return !lastPurchaseAt.isAfter(startDateTime);
            default:
                break;
        }
        return false;
    }
}
