package com.axon.core_service.repository;

import com.axon.core_service.domain.user.UserSummary;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserSummaryRepository extends JpaRepository<UserSummary, Long> {
}
