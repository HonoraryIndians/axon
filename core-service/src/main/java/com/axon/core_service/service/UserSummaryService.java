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

    /**
     * Updates the user's purchase activity by recording a purchase that occurred at the given instant.
     *
     * @param userId     the identifier of the user whose purchase summary will be updated
     * @param occurredAt the timestamp when the purchase occurred
     * @throws IllegalArgumentException if no user exists with the given id
     */
    @Transactional
    public void recordPurchase(Long userId, Instant occurredAt) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));
        user.recordPurchase(occurredAt);
    }

    /**
     * Records a login event for the specified user at the given timestamp, updating the user's activity summary.
     *
     * @param userId  the identifier of the user whose login should be recorded
     * @param loggedAt the instant when the login occurred
     * @throws IllegalArgumentException if no user exists with the given `userId`
     */
    @Transactional
    public void recordLogin(Long userId, Instant loggedAt) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));
        user.recordLogin(loggedAt);
    }
}