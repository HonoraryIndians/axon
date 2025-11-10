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

    /**
     * Determine whether another object is equal to this UserMetricId based on all key fields.
     *
     * @param o the object to compare with this instance
     * @return {@code true} if {@code o} is a {@code UserMetricId} and its {@code userId}, {@code metricName},
     *         and {@code metricWindow} are equal to this instance's corresponding fields, {@code false} otherwise
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        UserMetricId that = (UserMetricId) o;
        return Objects.equals(userId, that.userId) && Objects.equals(metricName, that.metricName) && Objects.equals(metricWindow, that.metricWindow);
    }

    /**
     * Computes a hash code based on the composite key fields.
     *
     * @return the hash code derived from {@code userId}, {@code metricName}, and {@code metricWindow}
     */
    @Override
    public int hashCode() {
        return Objects.hash(userId, metricName, metricWindow);
    }
}