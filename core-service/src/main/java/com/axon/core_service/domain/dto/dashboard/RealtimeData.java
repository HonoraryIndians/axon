package com.axon.core_service.domain.dto.dashboard;

import java.time.LocalDateTime;

public record RealtimeData(
        ActivityRealtime activity,
        LocalDateTime lastUpdated
) {
}
