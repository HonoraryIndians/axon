package com.axon.core_service.repository;

import com.axon.core_service.domain.event.Event;
import com.axon.core_service.domain.event.EventStatus;
import com.axon.core_service.domain.event.TriggerType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EventRepository extends JpaRepository<Event, Long> {

    @Query("select e from Event e where e.triggerCondition.triggerType = :triggerType and e.status = :status order by e.id asc")
    Optional<Event> findFirstByTriggerTypeAndStatus(@Param("triggerType") TriggerType triggerType,
                                                    @Param("status") EventStatus status);
}
