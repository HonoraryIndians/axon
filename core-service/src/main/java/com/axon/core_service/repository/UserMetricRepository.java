package com.axon.core_service.repository;

import com.axon.core_service.domain.user.metric.UserMetric;
import com.axon.core_service.domain.user.metric.UserMetricId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserMetricRepository extends JpaRepository<UserMetric, UserMetricId> {

}
