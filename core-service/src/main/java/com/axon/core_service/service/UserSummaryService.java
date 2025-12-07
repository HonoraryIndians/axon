package com.axon.core_service.service;

import com.axon.core_service.domain.user.User;
import com.axon.core_service.domain.user.UserSummary;
import com.axon.core_service.repository.UserRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.axon.core_service.repository.UserSummaryRepository;
import com.axon.core_service.service.purchase.PurchaseHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserSummaryService {

    private final UserRepository userRepository;
    private final UserSummaryRepository userSummaryRepository;
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
     * Bulk 구매 기록 (마지막 구매 시간만 업데이트)
     *
     * @param userSummaries userId -> PurchaseSummary 맵
     */
    @Transactional
    public void recordPurchaseBatch(Map<Long, PurchaseHandler.PurchaseSummary> userSummaries) {
        if (userSummaries.isEmpty()) {
            return;
        }

        log.info("Recording purchase for {} users", userSummaries.size());

        // 1. User 조회 (UserSummary는 User와 1:1 관계)
        List<User> users = userRepository.findAllById(userSummaries.keySet());

        // 2. 각 User의 마지막 구매 시간 업데이트
        for (User user : users) {
            PurchaseHandler.PurchaseSummary summary = userSummaries.get(user.getId());
            if (summary != null) {
                user.recordPurchase(summary.lastPurchaseTime());
            }
        }

        // 3. Dirty Checking으로 자동 UPDATE
        log.info("Updated purchase time for {} users", users.size());
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