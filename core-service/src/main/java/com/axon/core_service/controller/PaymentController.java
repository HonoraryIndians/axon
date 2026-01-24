package com.axon.core_service.controller;

import com.axon.core_service.domain.user.CustomOAuth2User;
import com.axon.core_service.dto.payment.PaymentRequest;
import com.axon.core_service.service.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/core/api/v1/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping("/process")
    public ResponseEntity<String> processPayment(
            @AuthenticationPrincipal CustomOAuth2User user,
            @RequestBody PaymentRequest request) {

        if (user == null) {
            return ResponseEntity.status(401).body("로그인이 필요합니다.");
        }
        
        // JWT에서 UserId 추출 (CustomOAuth2User에서 직접 획득)
        Long userId = user.getUserId();

        try {
            paymentService.processPayment(request.getToken(), userId);
            return ResponseEntity.ok("결제가 성공적으로 완료되었습니다.");
        } catch (IllegalArgumentException e) {
            log.warn("결제 요청 거부: {}", e.getMessage());
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            log.error("결제 처리 중 시스템 오류", e);
            return ResponseEntity.internalServerError().body("결제 처리 중 오류가 발생했습니다.");
        }
    }
}
