package com.axon.core_service.domain.event;

import com.axon.core_service.domain.common.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "event_occurrences")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EventOccurrence extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "event_id", nullable = false)
    private Event event;

    @Column(name = "occurred_at", nullable = false)
    private LocalDateTime occurredAt;

    @Column(name = "user_id", length = 255)
    private String userId;

    @Column(name = "page_url", length = 2048)
    private String pageUrl;

    @Convert(converter = TriggerConditionPayloadConverter.class)
    @Column(name = "event_context", columnDefinition = "TEXT", nullable = false)
    private Map<String, Object> context = Collections.emptyMap();

    @Builder
    private EventOccurrence(Event event,
                            LocalDateTime occurredAt,
                            String userId,
                            String pageUrl,
                            Map<String, Object> context) {
        this.event = Objects.requireNonNull(event, "event must not be null");
        this.occurredAt = occurredAt != null ? occurredAt : LocalDateTime.now();
        this.userId = userId;
        this.pageUrl = pageUrl;
        this.context = context == null || context.isEmpty()
                ? Collections.emptyMap()
                : Collections.unmodifiableMap(new LinkedHashMap<>(context));
    }
}
