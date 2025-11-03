package com.axon.core_service.domain.user.metric;


import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Objects;

@Embeddable // 다른타입의 복합 키임을 알림
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class UserMetricId implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long userId;
    private String metricName;
    private String metricWindow;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        UserMetricId that = (UserMetricId) o;
        return Objects.equals(userId, that.userId) && Objects.equals(metricName, that.metricName) && Objects.equals(metricWindow, that.metricWindow);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId, metricName, metricWindow);
    }
}
