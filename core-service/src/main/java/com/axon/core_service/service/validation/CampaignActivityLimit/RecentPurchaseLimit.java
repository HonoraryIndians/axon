package com.axon.core_service.service.validation.CampaignActivityLimit;

import com.axon.core_service.domain.user.UserSummary;
import com.axon.core_service.repository.UserSummaryRepository;
import com.axon.messaging.dto.validation.ValidationResponse;
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

    /**
     * Identifier for the "recent purchase" validation limit.
     *
     * @return the limit name "RECENT_PURCHASE"
     */
    @Override
    public String getLimitName() {
        return "RECENT_PURCHASE";
    }

    /**
     * Validates whether the user's last purchase timestamp satisfies the provided temporal constraint.
     *
     * @param userId  the identifier of the user whose last purchase timestamp will be checked
     * @param operator  one of "BETWEEN", "NOT_BETWEEN", "GTE", or "LTE" determining the comparison semantics
     * @param values  list of ISO-8601 instant strings representing bounds: the first entry is the start bound;
     *                the second entry (optional) is the end bound required for BETWEEN/NOT_BETWEEN
     * @return a ValidationResponse indicating eligibility: `eligible` is `true` when the user's lastPurchaseAt
     *         meets the operator constraint; otherwise `eligible` is `false` and an explanatory errorMessage is set.
     *         If the user record is missing or lastPurchaseAt is null, returns `eligible = false` with a localized
     *         recent-purchase error message. If input values are missing or malformed for the operator, returns
     *         a generic page error response.
     */
    @Override
    public ValidationResponse validateCampaignActivityLimit (Long userId, String operator, List<String> values) {
        String errorMSG = "최근 구매날짜가 응모 참여 조건을 만족하지 못하여 응모가 취소되었습니다.";

        Optional<UserSummary> userSummaryOpt = userSummaryRepository.findById(userId);
        if(userSummaryOpt.isEmpty() || userSummaryOpt.get().getLastPurchaseAt() == null) {return ValidationResponse.builder().eligible(false).errorMessage(errorMSG).build();}
        Instant lastPurchaseAt = userSummaryOpt.get().getLastPurchaseAt();

        if(values==null || values.isEmpty()) {return valueErrMsg();}
        log.info("oper {} || values: {}", operator, values);
        Instant startDateTime = Instant.parse(values.getFirst());
        Instant endDateTime = values.size() == 2 ? Instant.parse(values.get(1)) : null;

        switch(operator) {
            case "BETWEEN":
                if(endDateTime == null) return valueErrMsg();
                return resultReturn(!lastPurchaseAt.isBefore(startDateTime) && !lastPurchaseAt.isAfter(endDateTime),errorMSG);
            case "NOT_BETWEEN":
                if(endDateTime == null) return valueErrMsg();
                return resultReturn(lastPurchaseAt.isBefore(startDateTime) || lastPurchaseAt.isAfter(endDateTime), errorMSG);
            case "GTE": //이상
                if (values.size() != 1) return valueErrMsg();
                return resultReturn(!lastPurchaseAt.isBefore(startDateTime),errorMSG);
            case "LTE": // 이하
                if (values.size() != 1) return valueErrMsg();
                log.info("last: {} ||start: {}", lastPurchaseAt, startDateTime);
                return resultReturn(!lastPurchaseAt.isAfter(startDateTime), errorMSG);
            default:
                break;
        }
        return valueErrMsg();
    }

    /**
     * Produce a ValidationResponse indicating a generic page/value error for campaign validation.
     *
     * @return a ValidationResponse with eligible set to false and the error message "이벤트 응모 페이지에 문제가 발생했습니다."
     */
    private ValidationResponse valueErrMsg() {
        String valueErrMsg = "이벤트 응모 페이지에 문제가 발생했습니다.";
        return ValidationResponse.builder().eligible(false).errorMessage(valueErrMsg).build();
    }

    /**
     * Builds a ValidationResponse indicating eligibility based on the provided flag.
     *
     * @param day if `true`, the response is eligible; if `false`, the response is ineligible
     * @param msg the error message to include when the response is ineligible
     * @return a ValidationResponse with `eligible` set according to `day`; when `day` is `false` the response contains `msg` as the error message
     */
    private ValidationResponse resultReturn(boolean day, String msg) {
        if(day) {return ValidationResponse.builder().eligible(true).build();}
        else {return ValidationResponse.builder().eligible(false).errorMessage(msg).build();}
    }
}