package com.axon.core_service.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

import java.time.Instant;

/**
 * Domain event published when a user logs out.
 * Used for analytics and auditing purposes.
 */
@Getter
public class UserLogoutEvent extends ApplicationEvent {
    private final Long userId;
    private final Instant logoutAt;

    public UserLogoutEvent(Long userId, Instant logoutAt) {
        super(userId);
        this.userId = userId;
        this.logoutAt = logoutAt;
    }
}
