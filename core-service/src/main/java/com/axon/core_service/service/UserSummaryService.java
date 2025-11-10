package com.axon.core_service.service;

import com.axon.core_service.domain.user.User;
import com.axon.core_service.repository.UserRepository;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserSummaryService {

    private final UserRepository userRepository;

    @Transactional
    public void recordPurchase(Long userId, Instant occurredAt) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));
        user.recordPurchase(occurredAt);
    }

    @Transactional
    public void recordLogin(Long userId, Instant loggedAt) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));
        user.recordLogin(loggedAt);
    }
}
