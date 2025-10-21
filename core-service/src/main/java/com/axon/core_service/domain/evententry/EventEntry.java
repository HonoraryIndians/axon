package com.axon.core_service.domain.evententry;

import com.axon.core_service.domain.common.BaseTimeEntity;
import com.axon.core_service.domain.event.Event;
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
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "event_entries")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EventEntry extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "event_id")
    private Event event;

    @Column(name = "product_id")
    private Long productId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private EventEntryStatus status;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "requested_at", nullable = false)
    private Instant requestedAt;

    @Column(name = "processed_at")
    private Instant processedAt;

    @Column(name = "additional_data")
    private String info;

    private EventEntry(Event event,
                       Long userId,
                       Long productId,
                       Instant requestedAt) {
        this.event = event;
        this.userId = userId;
        this.productId = productId;
        this.requestedAt = requestedAt;
        this.status = EventEntryStatus.PENDING;
    }

    public static EventEntry create(Event event,
                                    Long userId,
                                    Long productId,
                                    Instant requestedAt) {
        return new EventEntry(event, userId, productId, requestedAt);
    }

    public void updateStatus(EventEntryStatus status) {
        this.status = status;
    }

    public void markProcessedAt(Instant processedAt) {
        this.processedAt = processedAt;
    }

    public void markProcessedNow() {
        markProcessedAt(Instant.now());
    }

    public void updateProduct(Long productId) {
        if (productId != null) {
            this.productId = productId;
        }
    }

    public void updateInfo(String info) {
        this.info = info;
    }
}
