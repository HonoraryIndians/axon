package com.axon.core_service.controller;

import com.axon.core_service.domain.dashboard.DashboardPeriod;
import com.axon.core_service.domain.dto.dashboard.DashboardResponse;
import com.axon.core_service.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.apache.coyote.Response;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/v1/dashboard")
@RequiredArgsConstructor
public class DashboardController {
    private final DashboardService dashboardService;


    @GetMapping("/activity/{activityId}")
    public ResponseEntity<DashboardResponse> getDashboardByActivity(
            @PathVariable Long activityId,
            @RequestParam(defaultValue = "7d") String period,
            @RequestParam(required = false) LocalDateTime startDate,
            @RequestParam(required = false) LocalDateTime endDate
    )
    {
        DashboardPeriod dashboardPeriod = DashboardPeriod.fromCode(period);
        DashboardResponse response = dashboardService.getDashboardByActivity(
                activityId,
                dashboardPeriod,
                startDate,
                endDate
        );
        return ResponseEntity.ok(response);
    }

}
