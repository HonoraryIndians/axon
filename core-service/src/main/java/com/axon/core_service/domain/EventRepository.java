package com.axon.core_service.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EventRepository extends JpaRepository<Event, Integer> {
    List<Event> findAllByCampaign_Id(Long campaignId);
}
