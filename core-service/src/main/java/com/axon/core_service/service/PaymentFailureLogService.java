package com.axon.core_service.service;

import com.axon.core_service.domain.payment.PaymentFailureLog;
import com.axon.core_service.repository.PaymentFailureLogRepository;
import com.axon.messaging.dto.payment.ReservationTokenPayload;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentFailureLogService {

    private final PaymentFailureLogRepository paymentFailureLogRepository;
    private final ObjectMapper objectMapper;

    /**
     * 결제 실패 로그 저장 (별도 트랜잭션)
     * 메인 로직이 롤백되어도 이 로그는 남아야 함.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logFailure(ReservationTokenPayload payload, Exception exception) {
        try {
            String jsonPayload = objectMapper.writeValueAsString(payload);
            String errorMessage = exception.getMessage();
            if (errorMessage != null && errorMessage.length() > 1000) {
                errorMessage = errorMessage.substring(0, 1000); // 길이 제한
            }

            PaymentFailureLog failureLog = PaymentFailureLog.builder()
                    .userId(payload.getUserId())
                    .payload(jsonPayload)
                    .errorMessage(errorMessage != null ? errorMessage : "Unknown Error")
                    .build();

            paymentFailureLogRepository.save(failureLog);
            log.info("결제 실패 로그 저장 완료: userId={}, error={}", payload.getUserId(), errorMessage);

        } catch (JsonProcessingException e) {
            log.error("실패 로그 저장 중 JSON 변환 오류 발생", e);
        } catch (Exception e) {
            log.error("실패 로그 저장 실패 (Critical)", e);
        }
    }
}
