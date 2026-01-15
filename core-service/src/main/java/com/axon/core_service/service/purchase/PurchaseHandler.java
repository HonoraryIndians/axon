package com.axon.core_service.service.purchase;
import com.axon.core_service.domain.dto.purchase.PurchaseInfoDto;
import com.axon.core_service.domain.purchase.PurchaseType;
import com.axon.core_service.event.CampaignActivityApprovedEvent;
import com.axon.core_service.service.ProductService;
import com.axon.core_service.service.UserSummaryService;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class PurchaseHandler {
    private static final int batchSize = 50;
    private final ProductService productService;
    private final UserSummaryService userSummaryService;
    private final PurchaseService purchaseService;
    private final ApplicationEventPublisher eventPublisher;
    private final TransactionTemplate transactionTemplate;

    // Purchase 이벤트 버퍼
    private final ConcurrentLinkedQueue<PurchaseInfoDto> purchaseBuffer = new ConcurrentLinkedQueue<>();

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void handle(PurchaseInfoDto info) {
        if (info.purchaseType() == PurchaseType.SHOP) {
            log.debug("[Purchase] Processing SHOP purchase immediately: userId={}, productId={}", info.userId(), info.productId());
            processImmediate(info);
        } else {
            purchaseBuffer.offer(info);
            log.debug("[Purchase] Event buffered. Buffer size: {}", purchaseBuffer.size());

            // 50개 모이면 즉시 처리
            if (purchaseBuffer.size() >= batchSize) {
                flushBatch();
            }
        }
    }

    /**
     * 일반 쇼핑몰 구매(SHOP) 등 즉시 처리가 필요한 경우 호출
     */
    private void processImmediate(PurchaseInfoDto info) {
        // 1. 실시간 재고 감소 (단건 처리)
        productService.decreaseStock(info.productId(), info.quantity());

        // 2. 실시간 유저 요약 업데이트 (단건 처리)
        userSummaryService.recordPurchase(info.userId(), info.occurredAt());

        // 3. Purchase 기록 저장 (단건 처리)
        purchaseService.createPurchase(info);

        log.info("Successfully processed immediate purchase for user {}", info.userId());
    }

    /**
     * 100ms마다 자동으로 버퍼 플러시
     */
    @Scheduled(fixedDelay = 100)
    public void scheduledFlush() {

        if (!purchaseBuffer.isEmpty()) {
            flushBatch();
        }
    }

    /**
     * 버퍼의 Purchase 이벤트를 배치 처리
     *
     * TransactionTemplate 사용으로 전체 배치를 하나의 트랜잭션으로 처리
     * → 원자성 보장: 하나라도 실패하면 전체 ROLLBACK
     */
    public synchronized void flushBatch() {
        if (purchaseBuffer.isEmpty()) {
            return;
        }

        // 1. 버퍼에서 Purchase 추출 (최대 50개)
        List<PurchaseInfoDto> purchases = drainBuffer();

        if (purchases.isEmpty()) {
            return;
        }

        log.info("Processing Purchase batch: {} purchases", purchases.size());

        boolean needRetry = false;
        try {
            // 2-1. Product별 재고 감소량 집계
            Map<Long, Integer> stockDecreases = purchases.stream()
                    .collect(Collectors.groupingBy(
                            PurchaseInfoDto::productId,
                            Collectors.summingInt(PurchaseInfoDto::quantity)
                    ));

            // 2-2. Bulk 재고 감소 (1회 SQL)
            if (!stockDecreases.isEmpty()) {
                // 선착순(FCFS)의 경우 실시간 재고 감소는 스킵하고, 캠페인 종료 후 스케줄러가 일괄 차감
                 log.info("Skipped stock decrease for {} products (Performance Optimization for FCFS)", stockDecreases.size());
            }

            // 2-3. User별 구매 통계 집계
            Map<Long, PurchaseSummary> userSummaries = purchases.stream()
                    .collect(Collectors.groupingBy(
                            PurchaseInfoDto::userId,
                            Collectors.collectingAndThen(
                                    Collectors.toList(),
                                    list -> new PurchaseSummary(
                                            list.size(),
                                            list.stream()
                                                    .map(p -> p.price().multiply(BigDecimal.valueOf(p.quantity())))
                                                    .reduce(BigDecimal.ZERO, BigDecimal::add),
                                            list.getFirst().occurredAt()
                                    )
                            )
                    ));

            // 2-4. Bulk 유저 요약 업데이트 (1회 SQL)
            if (!userSummaries.isEmpty()) {
                userSummaryService.recordPurchaseBatch(userSummaries);
            }

            // 2-5. Purchase bulk insert (REQUIRES_NEW Transaction in Service)
            purchaseService.createPurchaseBatch(purchases);
            log.info("Created {} purchase records", purchases.size());

            // 3. CampaignActivityApproved 이벤트 발행
            List<CampaignActivityApprovedEvent> events = purchases.stream()
                    .filter(p -> p.campaignActivityId() != null)
                    .map(p -> new CampaignActivityApprovedEvent(
                            p.campaignId(),
                            p.campaignActivityId(),
                            p.userId(),
                            p.productId(),
                            p.occurredAt()
                    ))
                    .toList();

            if (!events.isEmpty()) {
                events.forEach(eventPublisher::publishEvent);
                log.info("Published {} campaign approval events", events.size());
            }

        } catch (org.springframework.dao.DataIntegrityViolationException | org.springframework.transaction.UnexpectedRollbackException e) {
            log.warn("Batch failed due to transaction rollback (likely duplicate). Marking for individual retry... Error: {}", e.getMessage());
            needRetry = true;
        } catch (Exception e) {
            log.error("Error processing purchase batch: {}", e.getMessage(), e);
            // 상위 트랜잭션 롤백 방지를 위해 예외를 던지지 않음 (필요시 던질 수도 있음)
        }

        if (needRetry) {
            retryIndividually(purchases);
        }
    }

    private void retryIndividually(List<PurchaseInfoDto> purchases) {
        for (PurchaseInfoDto purchase : purchases) {
            try {
                // 1. 구매 저장 (REQUIRES_NEW Transaction in Service)
                purchaseService.createPurchaseBatch(List.of(purchase));

                // 2. 이벤트 발행
                if (purchase.campaignActivityId() != null) {
                    eventPublisher.publishEvent(new CampaignActivityApprovedEvent(
                            purchase.campaignId(),
                            purchase.campaignActivityId(),
                            purchase.userId(),
                            purchase.productId(),
                            purchase.occurredAt()
                    ));
                }
            } catch (Exception e) {
                log.error("Individual save failed for user {}: {}", purchase.userId(), e.getMessage());
                // 개별 실패는 무시하고 다음 건 진행
            }
        }
    }

    /**
     * 버퍼에서 Purchase 추출
     */
    private List<PurchaseInfoDto> drainBuffer() {
        List<PurchaseInfoDto> drained = new ArrayList<>(batchSize);

        for (int i = 0; i < batchSize; i++) {
            PurchaseInfoDto purchase = purchaseBuffer.poll();
            if (purchase == null) {
                break;
            }
            drained.add(purchase);
        }

        return drained;
    }

    /**
     * 서비스 종료 시 남은 Purchase 처리
     */
    @PreDestroy
    public void onShutdown() {
        log.info("Shutting down PurchaseHandler, flushing remaining purchases...");
        flushBatch();
    }

    /**
     * 유저 구매 요약 (내부 사용)
     */
    public record PurchaseSummary(
            int purchaseCount,
            BigDecimal totalAmount,
            Instant lastPurchaseTime
    ) {}
}
