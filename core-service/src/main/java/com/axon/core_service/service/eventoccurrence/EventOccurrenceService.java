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

    /**
     * Constructs an EventOccurrenceService and registers the provided strategies keyed by each strategy's trigger type.
     *
     * @param strategies list of EventOccurrenceStrategy instances to register; each strategy's {@code getTriggerType()}
     *                   value is used as the key for lookup during processing
     */
    public EventOccurrenceService(List<EventOccurrenceStrategy> strategies) {
        this.strategy = strategies.stream()
                .collect(Collectors.toMap(EventOccurrenceStrategy::getTriggerType, Function.identity()));
    }

    /**
     * Routes an event occurrence request to the strategy associated with the given trigger type.
     *
     * If a matching strategy exists, delegates creation to that strategy; otherwise logs a warning and does nothing.
     *
     * @param triggerType the trigger type key used to select the corresponding EventOccurrenceStrategy
     * @param request the event occurrence request data to be processed by the selected strategy
     */
    public void process(String triggerType, EventOccurrenceRequest request) {
        EventOccurrenceStrategy strategy = this.strategy.get(triggerType);
        if(strategy != null) {
            strategy.createEventOccurrence(request);
        } else {
            log.warn("지원하지 않는 트리거입니다. {}", triggerType);
        }
    }
}