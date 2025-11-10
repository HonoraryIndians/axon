package com.axon.entry_service.service;

import com.axon.entry_service.domain.CampaignActivityMeta;
import com.axon.entry_service.service.exception.FastValidationException;
import com.axon.messaging.dto.validation.Grade;
import com.axon.messaging.dto.validation.UserCacheDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class FastValidationService {
    private final RedisTemplate<String, Object> redisTemplate;

    /**
     * Validates a user's eligibility against FAST-phase campaign filters and throws on the first failed condition.
     *
     * <p>Fetches user data from Redis and evaluates each filter in {@code meta.filters()} whose {@code phase} equals
     * "FAST". Supported filter types are "AGE" and "GRADE"; unsupported types cause a validation failure.</p>
     *
     * @param userId the identifier of the user to validate
     * @param meta campaign activity metadata containing the list of filters to evaluate
     * @throws FastValidationException if user data is missing, filters are absent, a filter type is unsupported,
     *         or the user fails any AGE or GRADE filter condition
     */
    public void fastValidation(Long userId, CampaignActivityMeta meta) throws FastValidationException {
        UserCacheDto userCache = (UserCacheDto) redisTemplate.opsForValue().get("userCache:"+userId);
        if (userCache == null) {throw new FastValidationException("NULLUserData", "사용자 정보를 조회할 수 없습니다.");}

        List<Map<String, Object>> filters = meta.filters();
        log.info("필터 정보 : {}", filters);
        if(filters==null||filters.isEmpty()){throw new FastValidationException("NULL FILTER", "응모 참여 조건 조회에 실패했습니다.");}

        for(Map<String, Object> filter : filters){
            if(!"FAST".equals(filter.get("phase"))){continue;}
            String type = (String)filter.get("type");
            String operator = (String)filter.get("operator");
            List<String> values = (List<String>)filter.get("values");

            switch (type){
                case "AGE":
                    if(!validateAge(userCache.getAge(), operator, values)) {throw new FastValidationException("AGE", "나이가 응모 조건에 맞지 않습니다!");}
                    break;
                case "GRADE":
                    if(!validateGrade(userCache.getGrade(), operator, values)) {throw new FastValidationException("GRADE", "등급이 응모 조건에 맞지 않습니다!");}
                    break;
                default:
                    throw new FastValidationException("NULL TYPE","응모조건 조회에 오류가 발생했습니다.");
            }
        }
    }

    /**
     * Evaluates whether the given age satisfies the condition described by `operator` and `values`.
     *
     * @param age the user's age to evaluate; if null the method returns `false`
     * @param operator one of: `"GTE"`, `"LTE"`, `"BETWEEN"`, `"NOT_GTE"`, `"NOT_LTE"`, `"NOT_BETWEEN"`
     * @param values list of string numeric bounds: for single-bound operators (`GTE`, `LTE`, `NOT_GTE`, `NOT_LTE`) the first element is used;
     *               for range operators (`BETWEEN`, `NOT_BETWEEN`) exactly two elements are required (upper then lower)
     * @return `true` if `age` meets the specified operator condition using the numeric values parsed from `values`, `false` otherwise
     */
    private boolean validateAge(Integer age, String operator, List<String> values) {
        if(age == null || values == null || values.isEmpty()){return false;}

        int firstFilterAge = Integer.parseInt(values.getFirst());
        switch (operator){
            case "GTE":
                return age >= firstFilterAge;
            case "LTE":
                return age <= firstFilterAge;
            case "BETWEEN":
                if(values.size() != 2) {return false;}
                int secondFilterAge = Integer.parseInt(values.get(1));
                return secondFilterAge <= age && age <= firstFilterAge;
            case "NOT_GTE":
                return !(age >= firstFilterAge);
            case "NOT_LTE":
                return !(age <= firstFilterAge);
            case "NOT_BETWEEN":
                if(values.size() != 2) {return false;}
                int thirdFilterAge = Integer.parseInt(values.get(1));
                return !(thirdFilterAge <= age && age <= firstFilterAge);
            default:
                return false;
        }
    }

    /**
     * Determines whether the given Grade satisfies the constraint defined by the operator and value list.
     *
     * @param grade    the Grade to evaluate
     * @param operator the comparison operator; supported values are "IN" and "NOT_IN"
     * @param values   the list of grade names to compare against
     * @return         `true` if the grade meets the operator condition with the provided values, `false` otherwise
     */
    private boolean validateGrade(Grade grade, String operator, List<String> values) {
        if(grade == null || values == null || values.isEmpty()){return false;}
        switch (operator){
            case "IN":
                return values.contains(grade.toString());
            case "NOT_IN":
                return !values.contains(grade.toString());
            default:
                return false;
        }
    }
}