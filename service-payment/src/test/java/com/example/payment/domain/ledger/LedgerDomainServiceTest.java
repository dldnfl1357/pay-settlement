package com.example.payment.domain.ledger;

import com.example.payment.domain.shared.Money;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("LedgerDomainService 테스트")
class LedgerDomainServiceTest {

    private LedgerDomainService ledgerDomainService;

    @BeforeEach
    void setUp() {
        ledgerDomainService = new LedgerDomainService();
    }

    @Test
    @DisplayName("결제 원장 생성 - 복식부기 (DEBIT + CREDIT)")
    void createPaymentEntries() {
        TransactionId txId = TransactionId.generate();
        Long paymentId = 1L;
        Long walletId = 1L;
        Long merchantId = 1L;
        Money amount = Money.of(50000);
        Money walletBalanceAfter = Money.of(50000);
        String orderId = "ORDER-001";

        List<LedgerEntry> entries = ledgerDomainService.createPaymentEntries(
            txId, paymentId, walletId, merchantId, amount, walletBalanceAfter, orderId
        );

        assertThat(entries).hasSize(2);

        // DEBIT: 월렛에서 차감
        LedgerEntry debit = entries.stream()
            .filter(LedgerEntry::isDebit)
            .findFirst()
            .orElseThrow();
        assertThat(debit.getAccountType()).isEqualTo(AccountType.WALLET);
        assertThat(debit.getAccountId()).isEqualTo(walletId);
        assertThat(debit.getAmount().getAmount()).isEqualByComparingTo("50000");
        assertThat(debit.getDescription()).contains("결제");

        // CREDIT: 가맹점에 적립
        LedgerEntry credit = entries.stream()
            .filter(LedgerEntry::isCredit)
            .findFirst()
            .orElseThrow();
        assertThat(credit.getAccountType()).isEqualTo(AccountType.MERCHANT);
        assertThat(credit.getAccountId()).isEqualTo(merchantId);
        assertThat(credit.getAmount().getAmount()).isEqualByComparingTo("50000");
        assertThat(credit.getDescription()).contains("매출");

        // 같은 트랜잭션 ID
        assertThat(debit.getTransactionId()).isEqualTo(credit.getTransactionId());
    }

    @Test
    @DisplayName("취소 원장 생성 - 역분개")
    void createCancellationEntries() {
        TransactionId txId = TransactionId.generate();
        Long paymentId = 1L;
        Long walletId = 1L;
        Long merchantId = 1L;
        Money amount = Money.of(50000);
        Money walletBalanceAfter = Money.of(100000);
        String orderId = "ORDER-001";

        List<LedgerEntry> entries = ledgerDomainService.createCancellationEntries(
            txId, paymentId, walletId, merchantId, amount, walletBalanceAfter, orderId
        );

        assertThat(entries).hasSize(2);

        // DEBIT: 가맹점에서 차감
        LedgerEntry debit = entries.stream()
            .filter(LedgerEntry::isDebit)
            .findFirst()
            .orElseThrow();
        assertThat(debit.getAccountType()).isEqualTo(AccountType.MERCHANT);
        assertThat(debit.getDescription()).contains("취소");

        // CREDIT: 월렛에 복구
        LedgerEntry credit = entries.stream()
            .filter(LedgerEntry::isCredit)
            .findFirst()
            .orElseThrow();
        assertThat(credit.getAccountType()).isEqualTo(AccountType.WALLET);
        assertThat(credit.getDescription()).contains("환불");
    }

    @Test
    @DisplayName("보상 원장 생성")
    void createCompensationEntry() {
        TransactionId txId = TransactionId.generate();
        Long paymentId = 1L;
        Long walletId = 1L;
        Money amount = Money.of(50000);
        Money walletBalanceAfter = Money.of(100000);
        String orderId = "ORDER-001";

        LedgerEntry entry = ledgerDomainService.createCompensationEntry(
            txId, paymentId, walletId, amount, walletBalanceAfter, orderId
        );

        assertThat(entry.isCredit()).isTrue();
        assertThat(entry.getAccountType()).isEqualTo(AccountType.WALLET);
        assertThat(entry.getAccountId()).isEqualTo(walletId);
        assertThat(entry.getDescription()).contains("보상");
    }
}
