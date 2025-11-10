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
