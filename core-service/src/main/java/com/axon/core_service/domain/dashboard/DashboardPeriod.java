package com.axon.core_service.domain.dashboard;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.time.LocalDateTime;


@Getter
@RequiredArgsConstructor
public enum DashboardPeriod {
    ONE_DAY("1d", 1),
    SEVEN_DAYS("7d", 7),
    THIRTY_DAYS("30d", 30),
    CUSTOM("custom", null);

    private final String code;
    private final Integer days;

    public static DashboardPeriod fromCode(String code) {
        //TODO: implement
        for (DashboardPeriod period : DashboardPeriod.values()) {
            if (period.getCode().equals(code)) {
                return period;
            }
        }
        throw new IllegalArgumentException("Invalid DashboardPeriod code: " + code);
    }

// TODO :  implement
//    public LocalDateTime getStartDate() {}
    public LocalDateTime getStartDateTime() {
        if (this.days == null) {
            throw new UnsupportedOperationException("Custom period does not have a predefined start date.");
        }
        return LocalDateTime.now().minusDays(this.days);
    }
}
