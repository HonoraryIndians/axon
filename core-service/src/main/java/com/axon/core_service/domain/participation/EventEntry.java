package com.axon.core_service.domain.participation;

import com.axon.core_service.domain.Event;
import com.axon.core_service.domain.common.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(
        name = "event_entries",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_event_entry_event_user",
                columnNames = {"event_id", "user_id"}
        )
)
public class EventEntry extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_id", nullable = false)
    private Event event;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "product_id")
    private Integer productId;

    @Column(name = "requested_at", nullable = false)
    private Instant requestedAt;

    @Column(name = "processed_at")
    private Instant processedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private EntryStatus status = EntryStatus.PENDING;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, length = 30)
    private EntrySource source = EntrySource.ENTRY_SERVICE;

    @Column(name = "additional_data", length = 2000)
    private String additionalData;

    // TODO: add builder or factory methods when integrating with Kafka consumer
}
