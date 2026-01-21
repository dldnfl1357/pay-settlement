package com.example.payment.infrastructure.pg;

import lombok.Getter;

@Getter
public class PgApprovalException extends RuntimeException {

    private final String errorCode;

    public PgApprovalException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }
}
