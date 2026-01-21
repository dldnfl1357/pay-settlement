package com.example.payment.infrastructure.pg;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Mock PG 클라이언트
 * 카드번호 끝 4자리에 따라 결과가 다름:
 * - 1111: 승인 성공
 * - 2222: 잔액 부족
 * - 3333: 카드 정지
 * - 9999: 타임아웃 (3초 후 실패)
 */
@Component
@Slf4j
public class MockPgClient {

    @Value("${payment.pg.timeout-seconds:5}")
    private int timeoutSeconds;

    public PgResponse approve(String cardToken, BigDecimal amount) {
        log.info("PG approval request: cardToken={}, amount={}", maskToken(cardToken), amount);

        String lastFour = extractLastFour(cardToken);

        try {
            return switch (lastFour) {
                case "1111" -> {
                    String txId = "PG-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
                    log.info("PG approval success: transactionId={}", txId);
                    yield PgResponse.success(txId);
                }
                case "2222" -> {
                    log.warn("PG approval failed: insufficient funds");
                    yield PgResponse.failure("INSUFFICIENT_FUNDS", "카드 잔액이 부족합니다");
                }
                case "3333" -> {
                    log.warn("PG approval failed: card suspended");
                    yield PgResponse.failure("CARD_SUSPENDED", "정지된 카드입니다");
                }
                case "9999" -> {
                    log.warn("PG approval timeout simulation");
                    TimeUnit.SECONDS.sleep(3);
                    yield PgResponse.failure("TIMEOUT", "PG 응답 시간 초과");
                }
                default -> {
                    String txId = "PG-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
                    log.info("PG approval success (default): transactionId={}", txId);
                    yield PgResponse.success(txId);
                }
            };
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("PG request interrupted");
            return PgResponse.failure("INTERRUPTED", "요청이 중단되었습니다");
        }
    }

    public PgResponse cancel(String pgTransactionId, BigDecimal amount) {
        log.info("PG cancel request: transactionId={}, amount={}", pgTransactionId, amount);

        // Mock: 항상 취소 성공
        String cancelTxId = "PGC-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        log.info("PG cancel success: cancelTransactionId={}", cancelTxId);

        return PgResponse.success(cancelTxId);
    }

    private String extractLastFour(String cardToken) {
        if (cardToken == null || cardToken.length() < 4) {
            return "0000";
        }
        // tok_test_1111 형식에서 마지막 4자리 추출
        return cardToken.substring(cardToken.length() - 4);
    }

    private String maskToken(String token) {
        if (token == null || token.length() <= 4) {
            return "****";
        }
        return "****" + token.substring(token.length() - 4);
    }
}
