package com.example.payment.domain.ledger;

import com.example.payment.domain.shared.Money;

import java.util.List;

public class LedgerDomainService {

    public List<LedgerEntry> createPaymentEntries(TransactionId txId, Long paymentId,
                                                   Long walletId, Long merchantId,
                                                   Money amount, Money walletBalanceAfter,
                                                   String orderId) {
        LedgerEntry debit = LedgerEntry.create(
            txId, paymentId,
            AccountType.WALLET, walletId,
            EntryType.DEBIT, amount,
            walletBalanceAfter, "결제: " + orderId
        );

        LedgerEntry credit = LedgerEntry.create(
            txId, paymentId,
            AccountType.MERCHANT, merchantId,
            EntryType.CREDIT, amount,
            null, "매출: " + orderId
        );

        return List.of(debit, credit);
    }

    public List<LedgerEntry> createCancellationEntries(TransactionId txId, Long paymentId,
                                                        Long walletId, Long merchantId,
                                                        Money amount, Money walletBalanceAfter,
                                                        String orderId) {
        LedgerEntry debit = LedgerEntry.create(
            txId, paymentId,
            AccountType.MERCHANT, merchantId,
            EntryType.DEBIT, amount,
            null, "취소: " + orderId
        );

        LedgerEntry credit = LedgerEntry.create(
            txId, paymentId,
            AccountType.WALLET, walletId,
            EntryType.CREDIT, amount,
            walletBalanceAfter, "환불: " + orderId
        );

        return List.of(debit, credit);
    }

    public LedgerEntry createCompensationEntry(TransactionId txId, Long paymentId,
                                                Long walletId, Money amount,
                                                Money walletBalanceAfter, String orderId) {
        return LedgerEntry.create(
            txId, paymentId,
            AccountType.WALLET, walletId,
            EntryType.CREDIT, amount,
            walletBalanceAfter, "보상: " + orderId
        );
    }
}
