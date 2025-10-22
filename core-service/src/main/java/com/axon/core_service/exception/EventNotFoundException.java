package com.axon.core_service.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public class EventNotFoundException extends ResponseStatusException {
    private final Long eventId;

    public EventNotFoundException(Long eventId) {
        super(HttpStatus.NOT_FOUND, "존재하지 않는 이벤트입니다.");
        this.eventId = eventId;
    }

    public Long getEventId() {
        return eventId;
    }
}
