package com.axon.core_service.service.eventoccurrence;

import com.axon.core_service.domain.dto.event.EventOccurrenceRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Slf4j
public class EventOccurrenceService {
    private final Map<String, EventOccurrenceStrategy> strategy;

    public EventOccurrenceService(List<EventOccurrenceStrategy> strategies) {
        this.strategy = strategies.stream()
                .collect(Collectors.toMap(EventOccurrenceStrategy::getTriggerType, Function.identity()));
    }

    public void process(String triggerType, EventOccurrenceRequest request) {
        EventOccurrenceStrategy strategy = this.strategy.get(triggerType);
        if(strategy != null) {
            strategy.createEventOccurrence(request);
        } else {
            log.warn("지원하지 않는 트리거입니다. {}", triggerType);
        }
    }
}
