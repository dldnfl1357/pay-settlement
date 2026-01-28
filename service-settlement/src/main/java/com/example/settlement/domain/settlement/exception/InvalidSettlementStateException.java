package com.example.settlement.domain.settlement.exception;

public class InvalidSettlementStateException extends RuntimeException {

    public InvalidSettlementStateException(String message) {
        super(message);
    }
}
