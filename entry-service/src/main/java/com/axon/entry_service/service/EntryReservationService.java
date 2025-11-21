package com.axon.entry_service.service;

import com.axon.entry_service.domain.CampaignActivityMeta;
import com.axon.entry_service.domain.ReservationResult;
import com.axon.entry_service.event.ReservationApprovedEvent;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class EntryReservationService {

    private final StringRedisTemplate redisTemplate;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * Attempt to reserve a participation slot for a user in a campaign activity.
     *
     * @param campaignActivityId the campaign activity identifier
     * @param userId             the user identifier attempting the reservation
     * @param meta               campaign activity metadata used to determine
     *                           participation eligibility and limit
     * @param requestedAt        the timestamp of the reservation request (used to
     *                           check participatability)
     * @return a {@code ReservationResult} indicating the outcome:
     *         {@code success(order)} with the allocated order number on success;
     *         {@code duplicated} if the user already reserved;
     *         {@code soldOut} if the activity's limit was reached;
     *         {@code closed} if the activity is not open at {@code requestedAt};
     *         {@code error} on invalid input or unexpected failures.
     */
    public ReservationResult reserve(long campaignActivityId,
            long userId,
            CampaignActivityMeta meta,
            Instant requestedAt) {

        if (meta == null) {
            return ReservationResult.error();
        }

        if (!meta.isParticipatable(requestedAt)) {
            return ReservationResult.closed();
        }

        String userKey = String.valueOf(userId);
        String userSetKey = participantsKey(campaignActivityId);
        String counterKey = counterKey(campaignActivityId);

        Long added = redisTemplate.opsForSet().add(userSetKey, userKey);
        if (added == null) {
            return ReservationResult.error();
        }
        if (added == 0L) {
            return ReservationResult.duplicated();
        }

        Long order = redisTemplate.opsForValue().increment(counterKey);
        Integer limitCount = meta.limitCount();
        if (order == null || (limitCount != null && order > limitCount)) {
            redisTemplate.opsForSet().remove(userSetKey, userKey);
            return ReservationResult.soldOut();
        }

        // Publish APPROVED event for dashboard tracking
        eventPublisher.publishEvent(new ReservationApprovedEvent(
                campaignActivityId,
                userId,
                order,
                requestedAt));

        return ReservationResult.success(order);
    }

    /**
     * Removes a user's reservation from the participant set for the specified
     * campaign activity in Redis.
     *
     * @param campaignActivityId the campaign activity identifier whose participant
     *                           set will be modified
     * @param userId             the user identifier to remove from the participant
     *                           set
     */
    public void rollbackReservation(long campaignActivityId, long userId) {
        String userKey = String.valueOf(userId);
        redisTemplate.opsForSet().remove(participantsKey(campaignActivityId), userKey);
    }

    /**
     * Builds the Redis key for the participants set of a campaign activity.
     *
     * @return the Redis key in the form
     *         {@code "campaign:<campaignActivityId>:users"}
     */
    private String participantsKey(long campaignActivityId) {
        return "campaign:%d:users".formatted(campaignActivityId);
    }

    /**
     * Builds the Redis counter key for the given campaign activity.
     *
     * @return the Redis key in the form "campaign:{id}:counter"
     */
    private String counterKey(long campaignActivityId) {
        return "campaign:%d:counter".formatted(campaignActivityId);
    }
}