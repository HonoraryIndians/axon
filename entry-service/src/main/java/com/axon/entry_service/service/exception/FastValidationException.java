package com.axon.entry_service.service.exception;

import lombok.Getter;

@Getter
public class FastValidationException extends RuntimeException {
    private final String type;
    private final String message;

    public FastValidationException(String type, String message) {
        this.type = type;
        this.message = message;
    }
}
