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
    private TriggerCondition triggerCondition; /**
     * Create a new Event with the specified name, description, status, and trigger condition.
     *
     * @param name             the event name; must not be null
     * @param description      the event description; must not be null
     * @param status           the initial event status; if null, defaults to {@code EventStatus.ACTIVE}
     * @param triggerCondition the trigger condition for the event; must not be null
     * @throws NullPointerException if {@code name}, {@code description}, or {@code triggerCondition} is null
     */

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

    /**
     * Update the event's status.
     *
     * @param status the new status to assign; must not be null
     * @throws NullPointerException if {@code status} is null
     */
    public void changeStatus(EventStatus status) {
        this.status = Objects.requireNonNull(status, "status must not be null");
    }

    /**
     * Replace this Event's trigger condition with the provided one.
     *
     * @param triggerCondition the new TriggerCondition to set; must not be null
     * @throws NullPointerException if {@code triggerCondition} is null
     */
    public void updateTriggerCondition(TriggerCondition triggerCondition) {
        this.triggerCondition = Objects.requireNonNull(triggerCondition, "triggerCondition must not be null");
    }

    /**
     * Replaces the event's trigger condition with a new condition defined by the given type and payload.
     *
     * @param triggerType the trigger type to set
     * @param payload     a map of values to include in the trigger condition's payload
     */
    public void updateTriggerCondition(TriggerType triggerType, Map<String, Object> payload) {
        this.triggerCondition = TriggerCondition.of(triggerType, payload);
    }

    /**
     * Update the event's name and description.
     *
     * @param name the new non-null name of the event
     * @param description the new non-null description of the event
     * @throws NullPointerException if {@code name} or {@code description} is null
     */
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

        /**
         * Creates a TriggerCondition with the specified trigger type and payload.
         *
         * @param triggerType the non-null trigger type for this condition
         * @param payload     the payload map; if `null` or empty an empty map is stored,
         *                    otherwise an unmodifiable copy preserving insertion order is stored
         * @throws NullPointerException if {@code triggerType} is {@code null}
         */
        private TriggerCondition(TriggerType triggerType, Map<String, Object> payload) {
            this.triggerType = Objects.requireNonNull(triggerType, "triggerType must not be null");
            this.payload = payload == null || payload.isEmpty()
                    ? Collections.emptyMap()
                    : Collections.unmodifiableMap(new LinkedHashMap<>(payload));
        }

        /**
         * Create a new TriggerCondition with the specified trigger type and payload.
         *
         * @param triggerType the trigger type for the condition; must not be null
         * @param payload     a map of payload properties (may be null or empty); if null or empty the resulting payload will be an empty map, otherwise it will be stored as an unmodifiable insertion-ordered map
         * @return            a new TriggerCondition instance with the given type and payload
         */
        public static TriggerCondition of(TriggerType triggerType, Map<String, Object> payload) {
            return new TriggerCondition(triggerType, payload);
        }

        /**
         * Retrieves the trigger condition's payload map.
         *
         * @return the payload map as an unmodifiable insertion-order Map of payload entries; may be empty
         */
        public Map<String, Object> getPayload() {
            return payload;
        }
    }
}