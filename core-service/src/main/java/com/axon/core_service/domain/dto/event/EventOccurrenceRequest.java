package com.axon.core_service.domain.dto.event;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;
import java.util.Map;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class EventOccurrenceRequest {

    @NotNull
    private Long eventId;

    private LocalDateTime occurredAt;

    private String userId;

    private String pageUrl;

    private Map<String, Object> context;
}
