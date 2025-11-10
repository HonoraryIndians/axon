package com.axon.entry_service.service.behavior;

import com.axon.entry_service.domain.behavior.UserBehaviorEvent;

public interface BehaviorEventPublisher {

    void publish(UserBehaviorEvent event);
}
