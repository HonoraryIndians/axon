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

import com.axon.entry_service.domain.CampaignActivityMeta;
import com.axon.entry_service.service.CampaignActivityMetaService;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/behavior/events")
@RequiredArgsConstructor
public class BehaviorEventController {

    private final BehaviorEventPublisher publisher;
    private final CampaignActivityMetaService metaService;

    /**
     * Accepts a behavior event request, builds a UserBehaviorEvent enriched with user and HTTP metadata, and publishes it for processing.
     *
     * @param request       the validated incoming behavior event payload
     * @param userDetails   the authenticated principal (may be null); when present a numeric username is preferred as the event's userId
     * @param servletRequest the servlet request used to extract HTTP metadata (for example, the User-Agent header)
     * @return              an HTTP 202 Accepted response with an empty body
     */
    @PostMapping
    public ResponseEntity<Void> recordBehaviorEvent(@Valid @RequestBody BehaviorEventRequest request,
                                                    @AuthenticationPrincipal UserDetails userDetails,
                                                    HttpServletRequest servletRequest) {
        log.info("recordBehaviorEvent request={}", request);
        Long userId = resolveUserId(request, userDetails);
        
        Map<String, Object> enrichedProperties = new HashMap<>(request.getProperties());
        enrichWithCampaignId(enrichedProperties);

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
                .properties(enrichedProperties)
                .build();

        publisher.publish(event);
        return ResponseEntity.accepted().build();
    }

    private void enrichWithCampaignId(Map<String, Object> properties) {
        if (properties != null && properties.containsKey("activityId")) {
            try {
                Object activityIdObj = properties.get("activityId");
                Long activityId = null;
                if (activityIdObj instanceof Number) {
                    activityId = ((Number) activityIdObj).longValue();
                } else if (activityIdObj instanceof String) {
                    activityId = Long.parseLong((String) activityIdObj);
                }

                if (activityId != null) {
                    CampaignActivityMeta meta = metaService.getMeta(activityId);
                    if (meta != null && meta.campaignId() != null) {
                        properties.put("campaignId", meta.campaignId());
                    }
                }
            } catch (Exception e) {
                log.warn("Failed to enrich event with campaignId", e);
            }
        }
    }

    /**
     * Resolves the user ID to associate with the event, preferring a numeric authenticated principal username.
     *
     * @param request     the incoming behavior event request whose `userId` is used as a fallback
     * @param userDetails the authenticated principal; if its username is numeric that value is used
     * @return the resolved user ID: the numeric value of `userDetails.getUsername()` when present and parseable as a long, otherwise `request.getUserId()` (may be null)
     */
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

    /**
     * Resolve the event timestamp, preferring the request's occurredAt when provided.
     *
     * @param request the incoming behavior event request
     * @return the event timestamp: request.getOccurredAt() if non-null, otherwise the current Instant
     */
    private Instant defaultOccurredAt(BehaviorEventRequest request) {
        return request.getOccurredAt() != null ? request.getOccurredAt() : Instant.now();
    }

    /**
     * Extracts the User-Agent header value from the HTTP request.
     *
     * @param request the HTTP servlet request to read the header from
     * @return the value of the `User-Agent` header, or `null` if the header is missing or empty
     */
    private String extractUserAgent(HttpServletRequest request) {
        String userAgent = request.getHeader("User-Agent");
        return StringUtils.hasText(userAgent) ? userAgent : null;
    }
}