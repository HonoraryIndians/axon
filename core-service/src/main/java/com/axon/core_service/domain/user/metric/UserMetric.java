package com.axon.core_service.domain.user.metric;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@NoArgsConstructor
@Entity
@Builder
@AllArgsConstructor
@Table(name="user_metrics")
@IdClass(UserMetricId.class) // 복합 키 구조 사용을 명시
public class UserMetric {
    @Id
    @Column(nullable = false)
    private Long userId;

    @Id
    @Column(nullable = false)
    private String metricName;

    @Id
    @Column(nullable = false)
    private String metricWindow;

    @Column(nullable = false)
    private Long metricValue;

    private LocalDateTime last_calculated_at;
}
