package com.axon.entry_service.domain;

import java.util.Optional;

public record ReservationResult(ReservationStatus status, Long order) {

    /**
     * Create a ReservationResult representing a successful reservation.
     *
     * @param order the assigned order number for the successful reservation, or null if not available
     * @return a ReservationResult with status SUCCESS and the provided order
     */
    public static ReservationResult success(Long order) {
        return new ReservationResult(ReservationStatus.SUCCESS, order);
    }

    /**
     * Create a reservation result indicating the request was identified as a duplicate.
     *
     * @return a ReservationResult whose status is {@link ReservationStatus#DUPLICATED} and whose order is {@code null}
     */
    public static ReservationResult duplicated() {
        return new ReservationResult(ReservationStatus.DUPLICATED, null);
    }

    /**
     * Create a reservation result representing a sold-out state.
     *
     * @return a ReservationResult with status `SOLD_OUT` and a null order
     */
    public static ReservationResult soldOut() {
        return new ReservationResult(ReservationStatus.SOLD_OUT, null);
    }

    /**
     * Create a ReservationResult representing a closed reservation.
     *
     * @return a ReservationResult with status CLOSED and a null order
     */
    public static ReservationResult closed() {
        return new ReservationResult(ReservationStatus.CLOSED, null);
    }

    /**
     * Creates a ReservationResult representing a failed reservation due to an error.
     *
     * @return a ReservationResult with status {@code ERROR} and a null order
     */
    public static ReservationResult error() {
        return new ReservationResult(ReservationStatus.ERROR, null);
    }

    /**
     * Indicates whether this reservation result represents a successful reservation.
     *
     * @return true if the reservation status equals {@code ReservationStatus.SUCCESS}, false otherwise.
     */
    public boolean isSuccess() {
        return status == ReservationStatus.SUCCESS;
    }

    /**
     * Indicates whether this reservation result represents a duplicated reservation.
     *
     * @return {@code true} if the status is {@link ReservationStatus#DUPLICATED}, {@code false} otherwise.
     */
    public boolean isDuplicated() {
        return status == ReservationStatus.DUPLICATED;
    }

    /**
     * Determines whether the reservation status is sold out.
     *
     * @return {@code true} if the status equals {@code ReservationStatus.SOLD_OUT}, {@code false} otherwise.
     */
    public boolean isSoldOut() {
        return status == ReservationStatus.SOLD_OUT;
    }

    /**
     * Determines whether the reservation status is CLOSED.
     *
     * @return `true` if the reservation status is CLOSED, `false` otherwise.
     */
    public boolean isClosed() {
        return status == ReservationStatus.CLOSED;
    }

    /**
     * The order identifier associated with this reservation.
     *
     * @return the order identifier, or `null` if no order was assigned
     */
    public Long order() {
        return order;
    }
}