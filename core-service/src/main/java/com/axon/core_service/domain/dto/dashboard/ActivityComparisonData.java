package com.axon.core_service.domain.dto.dashboard;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class ActivityComparisonData {
    private Long activityId;
    private String activityName;
    private String type; // e.g., FCFS, RAFFLE
    private Long visitCount;
    private Long purchaseCount;
    private BigDecimal gmv;
    private Double conversionRate;
}
