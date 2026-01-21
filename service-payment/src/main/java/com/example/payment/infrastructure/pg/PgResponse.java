package com.example.payment.infrastructure.pg;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PgResponse {

    private boolean success;
    private String transactionId;
    private String errorCode;
    private String errorMessage;

    public static PgResponse success(String transactionId) {
        return PgResponse.builder()
            .success(true)
            .transactionId(transactionId)
            .build();
    }

    public static PgResponse failure(String errorCode, String errorMessage) {
        return PgResponse.builder()
            .success(false)
            .errorCode(errorCode)
            .errorMessage(errorMessage)
            .build();
    }
}
