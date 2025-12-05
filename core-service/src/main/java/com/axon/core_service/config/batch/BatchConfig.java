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

    /**
     * Defines the batch job that computes and persists user purchase count metrics.
     *
     * @return the configured Job named "UserPurchaseCountJob" which starts with the UserPurchaseCountStep
     */
    @Bean
    public Job UserPurchaseCountJob() {
        return new JobBuilder("UserPurchaseCountJob", jobRepository)
                .start(UserPurchaseCountStep()).build();
    }


    /**
     * Builds a step-scoped JpaPagingItemReader that reads aggregated purchase counts per user for the specified time window.
     *
     * @param startDateTimeStr the start of the time window, formatted as "yyyy-MM-dd HH:mm:ss" (provided via jobParameters)
     * @param endDateTimeStr   the end of the time window, formatted as "yyyy-MM-dd HH:mm:ss" (provided via jobParameters)
     * @return                 a JpaPagingItemReader yielding UserPurchaseCountDto instances aggregated by user between the given start and end datetimes
     */
    @Bean
    @StepScope
    public JpaPagingItemReader<UserPurchaseCountDto> UserPurchaseCountReader(
            @Value("#{jobParameters['startDateTime']}") String startDateTimeStr,
            @Value("#{jobParameters['endDateTime']}") String endDateTimeStr) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        LocalDateTime startDateTime = LocalDateTime.parse(startDateTimeStr, formatter);
        LocalDateTime endDateTime = LocalDateTime.parse(endDateTimeStr, formatter);

        String query = "SELECT new com.axon.core_service.domain.dto.Metric.UserPurchaseCountDto(p.userId, COUNT(p.userId)) "
                + "FROM Purchase p "
                + "WHERE p.purchaseAt BETWEEN :startDateTime AND :endDateTime "
                + "GROUP BY p.userId";

        return new JpaPagingItemReaderBuilder<UserPurchaseCountDto>()
                .name("UserPurchaseCountReader")
                .entityManagerFactory(entityManagerFactory)
                .pageSize(100)
                .queryString(query)
                .parameterValues(Map.of("startDateTime", startDateTime, "endDateTime", endDateTime))
                .build();
    }

    /**
     * Converts a UserPurchaseCountDto into a UserMetric populated with the user's purchase count and metadata.
     *
     * @param metricWindow identifier for the metric's aggregation window (provided via job parameters)
     * @return an ItemProcessor that maps each UserPurchaseCountDto to a UserMetric with metricName "USER_PURCHASE_COUNT", metricValue set to the purchase count, metricWindow set to the provided value, and last_calculated_at set to the current timestamp
     */
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

    /**
     * Creates a JPA item writer configured to persist UserMetric entities using the injected EntityManagerFactory.
     *
     * @return a JpaItemWriter that writes and persists UserMetric entities
     */
    @Bean
    public JpaItemWriter<UserMetric> UserMetricWriter() {
        return new JpaItemWriterBuilder<UserMetric>()
                .entityManagerFactory(entityManagerFactory).build();
    }

    /**
     * Configures the step that reads aggregated user purchase counts, converts them to UserMetric, and persists them.
     *
     * @return the Step named "UserPurchaseCountStep" that processes UserPurchaseCountDto to UserMetric in chunks of 100
     */
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