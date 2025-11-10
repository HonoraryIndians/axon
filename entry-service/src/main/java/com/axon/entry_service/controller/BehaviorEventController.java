package com.axon.entry_service.controller;

import com.axon.entry_service.domain.behavior.UserBehaviorEvent;
import com.axon.entry_service.dto.BehaviorEventRequest;
import com.axon.entry_service.service.behavior.BehaviorEventPublisher;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/v1/behavior/events")
@RequiredArgsConstructor
public class BehaviorEventController {

    private final BehaviorEventPublisher publisher;

    @PostMapping
    public ResponseEntity<Void> recordBehaviorEvent(@Valid @RequestBody BehaviorEventRequest request,
                                                    @AuthenticationPrincipal UserDetails userDetails,
                                                    HttpServletRequest servletRequest) {
        log.info("recordBehaviorEvent request={}", request);
        Long userId = resolveUserId(request, userDetails);
        UserBehaviorEvent event = UserBehaviorEvent.builder()
                .eventId(request.getEventId())
                .eventName(request.getEventName())
                .triggerType(request.getTriggerType())
                .occurredAt(defaultOccurredAt(request))
                .userId(userId)
                .sessionId(request.getSessionId())
                .pageUrl(request.getPageUrl())
                .referrer(request.getReferrer())
                .userAgent(extractUserAgent(servletRequest))
                .properties(request.getProperties())
                .build();

        publisher.publish(event);
        return ResponseEntity.accepted().build();
    }

    private Long resolveUserId(BehaviorEventRequest request, UserDetails userDetails) {
        if (userDetails != null && StringUtils.hasText(userDetails.getUsername())) {
            try {
                return Long.parseLong(userDetails.getUsername());
            } catch (NumberFormatException e) {
                log.debug("Authenticated principal username is not numeric. username={}", userDetails.getUsername());
            }
        }
        return request.getUserId();
    }

    private Instant defaultOccurredAt(BehaviorEventRequest request) {
        return request.getOccurredAt() != null ? request.getOccurredAt() : Instant.now();
    }

    private String extractUserAgent(HttpServletRequest request) {
        String userAgent = request.getHeader("User-Agent");
        return StringUtils.hasText(userAgent) ? userAgent : null;
    }
}
