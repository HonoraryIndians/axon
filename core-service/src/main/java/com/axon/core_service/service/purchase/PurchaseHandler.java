package com.axon.core_service.service.purchase;
import com.axon.core_service.domain.dto.purchase.PurchaseInfoDto;
import com.axon.core_service.service.ProductService;
import com.axon.core_service.service.UserSummaryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class PurchaseHandler {
    private final ProductService productService;
    private final UserSummaryService userSummaryService;
    private final PurchaseService purchaseService;

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    @Transactional
    public void handle(PurchaseInfoDto info) {
        // 재고 감소
        productService.decreaseStock(info.productId(), info.quantity()); // quantity 추가

        // summary 갱신
        userSummaryService.recordPurchase(info.userId(), info.occurredAt());

        purchaseService.createPurchase(info);
    }

}
