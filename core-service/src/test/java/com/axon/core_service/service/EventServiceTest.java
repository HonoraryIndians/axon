package com.axon.core_service.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.axon.core_service.domain.dto.event.EventDefinitionResponse;
import com.axon.core_service.domain.event.Event;
import com.axon.core_service.domain.event.EventStatus;
import com.axon.core_service.domain.event.TriggerType;
import com.axon.core_service.repository.EventRepository;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

@DataJpaTest
@Import(EventService.class)
class EventServiceTest {

    @Autowired
    private EventService eventService;

    @Autowired
    private EventRepository eventRepository;

    @BeforeEach
    void setUp() {
        eventRepository.deleteAll();

        eventRepository.save(Event.builder()
                .name("Active Page View")
                .description("page view event")
                .status(EventStatus.ACTIVE)
                .triggerCondition(Event.TriggerCondition.of(TriggerType.PAGE_VIEW, Map.of("urlPattern", "/products/*")))
                .build());

        eventRepository.save(Event.builder()
                .name("Inactive Click")
                .description("click event")
                .status(EventStatus.INACTIVE)
                .triggerCondition(Event.TriggerCondition.of(TriggerType.CLICK, Map.of("selector", "#cta")))
                .build());

        eventRepository.save(Event.builder()
                .name("Active Click")
                .description("active click event")
                .status(EventStatus.ACTIVE)
                .triggerCondition(Event.TriggerCondition.of(TriggerType.CLICK, Map.of("selector", "#submit")))
                .build());
    }

    @Test
    @DisplayName("트리거 타입이 주어지면 해당 ACTIVE 이벤트만 반환한다")
    void getActiveEventDefinitions_filterByTriggerType() {
        List<EventDefinitionResponse> responses = eventService.getActiveEventDefinitions(TriggerType.CLICK);

        assertThat(responses)
                .hasSize(1)
                .first()
                .extracting(EventDefinitionResponse::getName)
                .isEqualTo("Active Click");
    }

    @Test
    @DisplayName("트리거 타입이 없으면 모든 ACTIVE 이벤트를 반환한다")
    void getActiveEventDefinitions_allActive() {
        List<EventDefinitionResponse> responses = eventService.getActiveEventDefinitions(null);

        assertThat(responses)
                .hasSize(2)
                .extracting(EventDefinitionResponse::getName)
                .containsExactly("Active Page View", "Active Click");
    }
}
