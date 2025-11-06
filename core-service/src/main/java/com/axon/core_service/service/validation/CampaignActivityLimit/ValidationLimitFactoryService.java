package com.axon.core_service.service.validation.CampaignActivityLimit;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class ValidationLimitFactoryService {
    private final Map<String, ValidationLimitStrategy> strategyMap;

    // Spring이 시작될 때 ValidationLimitStrategy 타입의 모든 빈을 리스트로 주입받음
    public ValidationLimitFactoryService(List<ValidationLimitStrategy> strategies) {
        this.strategyMap = strategies.stream()
                .collect(Collectors.toMap(ValidationLimitStrategy::getLimitName, Function.identity()));
    }

    public ValidationLimitStrategy getStrategy(String limitName) {
        return strategyMap.get(limitName);
    }
}
