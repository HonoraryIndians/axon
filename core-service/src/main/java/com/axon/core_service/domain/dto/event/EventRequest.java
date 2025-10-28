package com.axon.core_service.domain.dto.event;

import com.axon.core_service.domain.dto.filter.FilterDetail;
import com.axon.messaging.EventType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import jakarta.validation.constraints.NotBlank;

import java.time.LocalDateTime;
import java.util.List;


@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EventRequest {
    @NotBlank
    private String name;

    @PositiveOrZero
    private Integer limitCount;

    @NotNull
    private EventStatus status;

    @NotNull
    private LocalDateTime startDate;

    @NotNull
    private LocalDateTime endDate;

    @NotNull
    private EventType eventType;

    private List<FilterDetail> filters;
}
