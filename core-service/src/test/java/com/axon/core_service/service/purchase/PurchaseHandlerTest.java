package com.axon.core_service.service.purchase;

import com.axon.core_service.domain.dto.purchase.PurchaseInfoDto;
import com.axon.core_service.domain.purchase.PurchaseType;
import com.axon.core_service.service.ProductService;
import com.axon.core_service.service.UserSummaryService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.concurrent.ConcurrentLinkedQueue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PurchaseHandlerTest {

    @Mock
    private ProductService productService;

    @Mock
    private UserSummaryService userSummaryService;

    @Mock
    private PurchaseService purchaseService;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private TransactionTemplate transactionTemplate;

    @InjectMocks
    private PurchaseHandler purchaseHandler;

    @Test
    @DisplayName("일반 구매(SHOP) 이벤트는 즉시 처리되어야 한다")
    void handle_ShopPurchase_ProcessedImmediately() {
        // 1. Given: SHOP 타입의 PurchaseInfoDto 생성
        PurchaseInfoDto purchaseInfo = new PurchaseInfoDto(
                1L,
                1L,
                1L,
                1L,
                Instant.now(),
                PurchaseType.SHOP,
                BigDecimal.valueOf(10000),
                1,
                Instant.now()
        );
        // 2. When: purchaseHandler.handle() 호출
        purchaseHandler.handle(purchaseInfo);
        // 3. Then: Mock 객체들의 행위 검증 (verify)
        // - productService.decreaseStock() 호출 여부
        verify(productService, times(1)).decreaseStock(purchaseInfo.productId(), purchaseInfo.quantity());
        // - userSummaryService.recordPurchase() 호출 여부
        verify(userSummaryService, times(1)).recordPurchase(purchaseInfo.userId(), purchaseInfo.occurredAt());
        // - purchaseService.createPurchase() 호출 여부
        verify(purchaseService, times(1)).createPurchase(purchaseInfo);
    }

    @Test
    @DisplayName("선착순 구매(CAMPAIGNACTIVITY) 이벤트는 버퍼에 쌓여야 한다")
    void handle_CampaignPurchase_Buffered() {
        // 1. Given: CAMPAIGNACTIVITY 타입의 PurchaseInfoDto 생성
        PurchaseInfoDto purchaseInfo = new PurchaseInfoDto(
                1L,
                1L,
                1L,
                1L,
                Instant.now(),
                PurchaseType.CAMPAIGNACTIVITY,
                BigDecimal.valueOf(5000),
                1,
                Instant.now()
        );
        // 2. When: purchaseHandler.handle() 호출
        purchaseHandler.handle(purchaseInfo);
        // 3. Then: Mock 객체들의 행위 검증 (never) 및 버퍼 상태 확인
        // - 서비스 메서드들이 호출되지 않았는지 확인 (never)
        verify(productService, never()).decreaseStock(anyLong(), anyInt());
        verify(userSummaryService, never()).recordPurchase(anyLong(), any(Instant.class));
        verify(purchaseService, never()).createPurchase(any(PurchaseInfoDto.class));
        // - purchaseBuffer에 데이터가 들어갔는지 확인 (ReflectionTestUtils 사용 가능)
        ConcurrentLinkedQueue<?> purchaseBuffer = (ConcurrentLinkedQueue<?>) ReflectionTestUtils.getField(purchaseHandler, "purchaseBuffer");
        assertThat(purchaseBuffer).isNotEmpty();
    }
}
