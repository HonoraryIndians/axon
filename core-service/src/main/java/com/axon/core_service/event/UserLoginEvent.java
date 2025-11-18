package com.axon.core_service.event;

import java.time.Instant;

/**
 * Domain event published when a user successfully logs in via OAuth2.
 */
public record UserLoginEvent(
        Long userId,
        Instant loggedAt
) {
}
