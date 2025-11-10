package com.axon.entry_service.domain;

import java.util.Optional;

public record ReservationResult(ReservationStatus status, Long order) {

    public static ReservationResult success(Long order) {
        return new ReservationResult(ReservationStatus.SUCCESS, order);
    }

    public static ReservationResult duplicated() {
        return new ReservationResult(ReservationStatus.DUPLICATED, null);
    }

    public static ReservationResult soldOut() {
        return new ReservationResult(ReservationStatus.SOLD_OUT, null);
    }

    public static ReservationResult closed() {
        return new ReservationResult(ReservationStatus.CLOSED, null);
    }

    public static ReservationResult error() {
        return new ReservationResult(ReservationStatus.ERROR, null);
    }

    public boolean isSuccess() {
        return status == ReservationStatus.SUCCESS;
    }

    public boolean isDuplicated() {
        return status == ReservationStatus.DUPLICATED;
    }

    public boolean isSoldOut() {
        return status == ReservationStatus.SOLD_OUT;
    }

    public boolean isClosed() {
        return status == ReservationStatus.CLOSED;
    }

    public Long order() {
        return order;
    }
}
