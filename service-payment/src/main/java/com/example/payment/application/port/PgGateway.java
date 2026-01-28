package com.example.payment.application.port;

import com.example.payment.infrastructure.pg.PgResponse;

import java.math.BigDecimal;

public interface PgGateway {

    PgResponse approve(String cardToken, BigDecimal amount);

    PgResponse cancel(String pgTransactionId, BigDecimal amount);
}
