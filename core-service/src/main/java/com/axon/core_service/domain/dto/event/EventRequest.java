package com.axon.core_service.domain.dto.event;

import com.axon.core_service.domain.event.EventStatus;
import com.axon.core_service.domain.event.TriggerType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.Map;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class EventRequest {

    @NotBlank
    private String name;

    @NotBlank
    private String description;

    @NotNull
    private TriggerType triggerType;

    @NotNull
    private Map<String, Object> triggerPayload;

    private EventStatus status;
}
