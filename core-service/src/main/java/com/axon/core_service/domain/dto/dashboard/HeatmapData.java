package com.axon.core_service.domain.dto.dashboard;

import java.util.Map;

public record HeatmapData(
        Map<Integer, Long> hourlyTraffic
) {}