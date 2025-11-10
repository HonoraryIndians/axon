package com.axon.core_service.service;

import com.axon.core_service.domain.dto.event.EventOccurrenceRequest;
import com.axon.core_service.event.CampaignActivityApprovedEvent;
import com.axon.core_service.service.eventoccurrence.EventOccurrenceService;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class PurchaseEventHandler {

    private final ProductService productService;
    private final UserSummaryService userSummaryService;
    private final EventOccurrenceService eventOccurrenceService;

    /**
     * Handle a CampaignActivityApprovedEvent by applying purchase side effects and recording an occurrence.
     *
     * Processes the event by decreasing the product stock, recording the user's purchase activity, and creating
     * an EventOccurrenceRequest with the event's timestamp, userId, and a context containing campaignActivityId
     * and productId, then forwards it to the EventOccurrenceService with type "Purchase".
     *
     * @param event the campaign activity approval event containing userId, productId, campaignActivityId, and occurredAt
     */
    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    @Transactional
    public void handle(CampaignActivityApprovedEvent event) {
        productService.decreaseStock(event.productId());
        userSummaryService.recordPurchase(event.userId(), event.occurredAt());

        EventOccurrenceRequest occurrenceRequest = EventOccurrenceRequest.builder()
                .occurredAt(LocalDateTime.ofInstant(event.occurredAt(), ZoneId.systemDefault()))
                .userId(event.userId())
                .context(Map.of(
                        "campaignActivityId", event.campaignActivityId(),
                        "productId", event.productId()
                ))
                .build();
        eventOccurrenceService.process("Purchase", occurrenceRequest);
    }
}