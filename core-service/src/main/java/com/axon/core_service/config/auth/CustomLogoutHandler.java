package com.axon.core_service.config.auth;

import com.axon.core_service.domain.user.CustomOAuth2User;
import com.axon.core_service.event.UserLogoutEvent;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.logout.LogoutHandler;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Custom logout handler for JWT-based authentication.
 * Handles cleanup tasks during logout:
 * - Deletes user cache from Redis
 * - Publishes logout event for analytics
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CustomLogoutHandler implements LogoutHandler {

    private final RedisTemplate<String, Object> redisTemplate;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * Performs logout cleanup operations when a user logs out.
     *
     * @param request        the HTTP request
     * @param response       the HTTP response
     * @param authentication the current authentication object (may be null if not authenticated)
     */
    @Override
    public void logout(HttpServletRequest request, HttpServletResponse response, Authentication authentication) {
        if (authentication == null) {
            log.debug("Logout called with null authentication, skipping cleanup");
            return;
        }

        if (!(authentication.getPrincipal() instanceof CustomOAuth2User)) {
            log.debug("Logout called with non-CustomOAuth2User principal, skipping cleanup");
            return;
        }

        CustomOAuth2User customUser = (CustomOAuth2User) authentication.getPrincipal();
        Long userId = customUser.getUserId();

        try {
            // Delete user cache from Redis
            String cacheKey = "userCache:" + userId;
            Boolean deleted = redisTemplate.delete(cacheKey);
            log.info("Logout: userId={}, userCache deleted={}", userId, deleted);

            // Publish logout event for analytics
            eventPublisher.publishEvent(new UserLogoutEvent(userId, Instant.now()));
            log.info("Logout: userId={}, logout event published", userId);

        } catch (Exception e) {
            log.error("Error during logout cleanup for userId={}", userId, e);
            // Don't throw exception - allow logout to proceed even if cleanup fails
        }
    }
}
