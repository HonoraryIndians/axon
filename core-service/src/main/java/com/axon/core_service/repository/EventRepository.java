package com.axon.core_service.repository;

import com.axon.core_service.domain.event.Event;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EventRepository extends JpaRepository<Event, Integer> {
    List<Event> findAllByCampaign_Id(Long campaignId);
}
