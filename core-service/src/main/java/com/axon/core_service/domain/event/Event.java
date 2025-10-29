package com.axon.core_service.domain.event;

import com.axon.core_service.domain.common.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

@Entity
@Getter
@Table(name = "events")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Event extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name; // 수집 이벤트 이름

    @Column(length = 2000, nullable = false)
    private String description; // 수집 이벤트 설명

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EventStatus status = EventStatus.ACTIVE; // 기본값 ACTIVE

    @Embedded
    private TriggerCondition triggerCondition; // 수집 이벤트 발동 조건

    @Builder
    private Event(String name,
                  String description,
                  EventStatus status,
                  TriggerCondition triggerCondition) {
        this.name = Objects.requireNonNull(name, "name must not be null");
        this.description = Objects.requireNonNull(description, "description must not be null");
        this.status = status != null ? status : EventStatus.ACTIVE;
        this.triggerCondition = Objects.requireNonNull(triggerCondition, "triggerCondition must not be null");
    }

    public void changeStatus(EventStatus status) {
        this.status = Objects.requireNonNull(status, "status must not be null");
    }

    public void updateTriggerCondition(TriggerCondition triggerCondition) {
        this.triggerCondition = Objects.requireNonNull(triggerCondition, "triggerCondition must not be null");
    }

    public void updateTriggerCondition(TriggerType triggerType, Map<String, Object> payload) {
        this.triggerCondition = TriggerCondition.of(triggerType, payload);
    }

    public void updateDetails(String name, String description) {
        this.name = Objects.requireNonNull(name, "name must not be null");
        this.description = Objects.requireNonNull(description, "description must not be null");
    }

    @Embeddable
    @Getter
    @NoArgsConstructor(access = AccessLevel.PROTECTED)
    public static class TriggerCondition {

        @Enumerated(EnumType.STRING)
        @Column(name = "trigger_type", nullable = false)
        private TriggerType triggerType;

        @Convert(converter = TriggerConditionPayloadConverter.class)
        @Column(name = "trigger_payload", nullable = false, columnDefinition = "TEXT")
        private Map<String, Object> payload = Collections.emptyMap();

        private TriggerCondition(TriggerType triggerType, Map<String, Object> payload) {
            this.triggerType = Objects.requireNonNull(triggerType, "triggerType must not be null");
            this.payload = payload == null || payload.isEmpty()
                    ? Collections.emptyMap()
                    : Collections.unmodifiableMap(new LinkedHashMap<>(payload));
        }

        public static TriggerCondition of(TriggerType triggerType, Map<String, Object> payload) {
            return new TriggerCondition(triggerType, payload);
        }

        public Map<String, Object> getPayload() {
            return payload;
        }
    }
}
