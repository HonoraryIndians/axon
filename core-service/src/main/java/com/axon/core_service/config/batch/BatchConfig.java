package com.axon.core_service.config.batch;

import com.axon.core_service.domain.dto.Metric.UserPurchaseCountDto;
import com.axon.core_service.domain.user.metric.UserMetric;
import jakarta.persistence.EntityManagerFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.EnableBatchProcessing;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.database.JpaItemWriter;
import org.springframework.batch.item.database.JpaPagingItemReader;
import org.springframework.batch.item.database.builder.JpaItemWriterBuilder;
import org.springframework.batch.item.database.builder.JpaPagingItemReaderBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

@Configuration
@EnableBatchProcessing
@RequiredArgsConstructor
public class BatchConfig {
    private final EntityManagerFactory entityManagerFactory;
    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;

    @Bean
    public Job UserPurchaseCountJob() {
        return new JobBuilder("UserPurchaseCountJob", jobRepository)
                .start(UserPurchaseCountStep()).build();
    }


    @Bean
    @StepScope
    public JpaPagingItemReader<UserPurchaseCountDto> UserPurchaseCountReader(
            @Value("#{jobParameters['startDateTime']}") String startDateTimeStr,
            @Value("#{jobParameters['endDateTime']}") String endDateTimeStr) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        LocalDateTime startDateTime = LocalDateTime.parse(startDateTimeStr, formatter);
        LocalDateTime endDateTime = LocalDateTime.parse(endDateTimeStr, formatter);

        String query = "SELECT new com.axon.core_service.domain.dto.Metric.UserPurchaseCountDto(e.userId, COUNT(e.userId)) "
                + "FROM EventOccurrence e "
                + "WHERE e.occurredAt BETWEEN :startDateTime AND :endDateTime "
                + "GROUP BY e.userId";

        return new JpaPagingItemReaderBuilder<UserPurchaseCountDto>()
                .name("UserPurchaseCountReader")
                .entityManagerFactory(entityManagerFactory)
                .pageSize(100)
                .queryString(query)
                .parameterValues(Map.of("startDateTime", startDateTime, "endDateTime", endDateTime))
                .build();
    }

    @Bean
    @StepScope
    public ItemProcessor<UserPurchaseCountDto, UserMetric> UserPurchaseCountProcessor(@Value("#{jobParameters['metricWindow']}")  String metricWindow) {
        return dto -> UserMetric.builder()
                    .userId(dto.getUserId())
                    .metricValue(dto.getPurchaseCount())
                    .metricName("USER_PURCHASE_COUNT")
                    .metricWindow(metricWindow)
                    .last_calculated_at(LocalDateTime.now())
                    .build();

    }

    @Bean
    public JpaItemWriter<UserMetric> UserMetricWriter() {
        return new JpaItemWriterBuilder<UserMetric>()
                .entityManagerFactory(entityManagerFactory).build();
    }

    @Bean
    public Step UserPurchaseCountStep() {
        return new StepBuilder("UserPurchaseCountStep", jobRepository)
                .<UserPurchaseCountDto, UserMetric>chunk(100, transactionManager)
                .reader(UserPurchaseCountReader(null, null))
                .processor(UserPurchaseCountProcessor(null))
                .writer(UserMetricWriter())
                .build();
    }

}
