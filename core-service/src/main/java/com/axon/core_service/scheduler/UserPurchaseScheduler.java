package com.axon.core_service.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Slf4j
@Component
@RequiredArgsConstructor
public class UserPurchaseScheduler {
    private final JobLauncher jobLauncher;
    private final Job UserPurchaseJob;

    @Scheduled(fixedRate = 6000000)
    public void RunUserPurchaseJob() {
        try {
            LocalDateTime now = LocalDateTime.now();
            //테스트 용
            LocalDateTime oneMinuteAgo = now.minusMinutes(1);
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

            JobParameters jobParameters = new JobParametersBuilder()
                    .addString("startDateTime", oneMinuteAgo.format(formatter))
                    .addString("endDateTime", now.format(formatter))
                    .addString("metricWindow", "TEST-" + now.format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss")))
                    // 베치는 동일한 내용의 파라미터를 다시 실행시키지 않는 속성이 있으므로 테스트 단계에서 더미데이터를 넣어 해결
                    .addString("runId", String.valueOf(System.currentTimeMillis()))
                    .toJobParameters();
            jobLauncher.run(UserPurchaseJob,  jobParameters);
            log.info("월간 구매 횟수 집계 배치를 실행했습니다. Parameters: {}", jobParameters);
        } catch (Exception e){
            log.error("사용자 구매 스케쥴러에 문제가 발생했습니다. ",e);
        }
    }
}
