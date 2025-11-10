package com.axon.core_service.service.validation.CampaignActivityLimit;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class ValidationLimitFactoryService {
    private final Map<String, ValidationLimitStrategy> strategyMap;

    /**
     * Create a ValidationLimitFactoryService and register the provided strategies keyed by each strategy's limit name.
     *
     * @param strategies list of ValidationLimitStrategy instances (injected by Spring) to be indexed by their limit names
     */
    public ValidationLimitFactoryService(List<ValidationLimitStrategy> strategies) {
        this.strategyMap = strategies.stream()
                .collect(Collectors.toMap(ValidationLimitStrategy::getLimitName, Function.identity()));
    }

    /**
     * Retrieve the ValidationLimitStrategy registered under the given limit name.
     *
     * @param limitName the strategy's limit name key used to look up the strategy
     * @return the matching ValidationLimitStrategy, or {@code null} if no strategy is registered for the given name
     */
    public ValidationLimitStrategy getStrategy(String limitName) {
        return strategyMap.get(limitName);
    }
}