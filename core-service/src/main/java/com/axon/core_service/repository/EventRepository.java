package com.axon.core_service.repository;

import com.axon.core_service.domain.event.Event;
import com.axon.core_service.domain.event.EventStatus;
import com.axon.core_service.domain.event.TriggerType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EventRepository extends JpaRepository<Event, Long> {

    /**
                                                     * Finds the first Event that matches the given trigger type and status, ordered by id ascending.
                                                     *
                                                     * @param triggerType the trigger type to match
                                                     * @param status the event status to match
                                                     * @return an Optional containing the first matching Event, or empty if no match is found
                                                     */
                                                    @Query("select e from Event e where e.triggerCondition.triggerType = :triggerType and e.status = :status order by e.id asc")
    Optional<Event> findFirstByTriggerTypeAndStatus(@Param("triggerType") TriggerType triggerType,
                                                    @Param("status") EventStatus status);

    /**
 * Retrieve all Event entities with the given status ordered by id ascending.
 *
 * @param status the EventStatus to match
 * @return a list of matching Event entities ordered by id ascending; empty list if no matches
 */
List<Event> findAllByStatusOrderByIdAsc(EventStatus status);

    /**
 * Retrieve all Event entities whose triggerCondition.triggerType matches the given triggerType and whose status matches the given status, ordered by id in ascending order.
 *
 * @param triggerType the trigger type to match against the event's trigger condition
 * @param status the event status to match
 * @return a list of matching Event entities ordered by id ascending; empty list if no matches
 */
List<Event> findAllByTriggerCondition_TriggerTypeAndStatusOrderByIdAsc(TriggerType triggerType, EventStatus status);
}