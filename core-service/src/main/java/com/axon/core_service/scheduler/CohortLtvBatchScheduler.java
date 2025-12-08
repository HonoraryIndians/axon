package com.axon.core_service.scheduler;

import com.axon.core_service.service.batch.CohortLtvBatchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class CohortLtvBatchScheduler {

    private final CohortLtvBatchService cohortLtvBatchService;

    /**
     * 매월 1일 새벽 3시에 코호트 LTV 배치 실행
     * Cron: "초 분 시 일 월 요일"
     * "0 0 3 1 * ?" = 매월 1일 03:00:00
     *
     * 수집 기간: 전달 1일 00:00:00 ~ 이번달 1일 00:00:00.000 이전
     */
    @Scheduled(cron = "0 0 3 1 * ?")
    public void runMonthlyCohortLtvBatch() {
        log.info("========== Monthly Cohort LTV Batch Started ==========");
        try {
            cohortLtvBatchService.processMonthlyCohortStats();
            log.info("========== Monthly Cohort LTV Batch Completed ==========");
        } catch (Exception e) {
            log.error("========== Monthly Cohort LTV Batch Failed ==========", e);
        }
    }
}
