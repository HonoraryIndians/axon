package com.axon.entry_service.service.behavior;

import com.axon.entry_service.domain.behavior.UserBehaviorEvent;

public interface BehaviorEventPublisher {

    /**
 * Publish a user behavior event to downstream consumers or handlers.
 *
 * @param event the user behavior event to be dispatched
 */
void publish(UserBehaviorEvent event);
}