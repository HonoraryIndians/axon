package com.axon.core_service.domain.dto.dashboard;

import com.axon.core_service.domain.dashboard.FunnelStep;
import lombok.Builder;
import org.springframework.security.core.parameters.P;


public record FunnelStepData (
        FunnelStep step,
        Long count
){}
