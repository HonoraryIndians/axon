package com.axon.core_service.domain.dto.dashboard;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class HeatmapData {
    // Key: Hour (0-23), Value: Count
    private Map<Integer, Long> hourlyTraffic;
}
