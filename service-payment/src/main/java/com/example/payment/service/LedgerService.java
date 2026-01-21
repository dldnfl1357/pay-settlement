package com.example.payment.service;

import com.example.payment.domain.*;
import com.example.payment.repository.LedgerEntryRepository;
import com.example.payment.repository.WalletRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class LedgerService {

    private final LedgerEntryRepository ledgerRepository;
    private final WalletRepository walletRepository;

    /**
     * 결제 원장 기록 (복식부기)
     * - 차변(DEBIT): 사용자 월렛에서 차감
     * - 대변(CREDIT): 가맹점에 적립
     */
    @Transactional
    public void recordPayment(Payment payment) {
        String txId = UUID.randomUUID().toString();
        LocalDateTime now = LocalDateTime.now();

        BigDecimal walletBalanceAfter = getWalletBalance(payment.getWalletId());

        // 차변: 사용자 월렛에서 차감
        LedgerEntry debit = LedgerEntry.builder()
            .transactionId(txId)
            .paymentId(payment.getId())
            .accountType(AccountType.WALLET)
            .accountId(payment.getWalletId())
            .entryType(EntryType.DEBIT)
            .amount(payment.getAmount())
            .balanceAfter(walletBalanceAfter)
            .description("결제: " + payment.getOrderId())
            .build();

        // 대변: 가맹점에 적립
        LedgerEntry credit = LedgerEntry.builder()
            .transactionId(txId)
            .paymentId(payment.getId())
            .accountType(AccountType.MERCHANT)
            .accountId(payment.getMerchantId())
            .entryType(EntryType.CREDIT)
            .amount(payment.getAmount())
            .description("매출: " + payment.getOrderId())
            .build();

        ledgerRepository.saveAll(List.of(debit, credit));

        log.info("Ledger recorded: txId={}, paymentId={}, amount={}",
            txId, payment.getId(), payment.getAmount());
    }

    /**
     * 결제 취소 원장 기록 (역분개)
     * - 차변(DEBIT): 가맹점에서 차감
     * - 대변(CREDIT): 사용자 월렛에 복구
     */
    @Transactional
    public void recordCancellation(Payment payment) {
        String txId = UUID.randomUUID().toString();

        BigDecimal walletBalanceAfter = getWalletBalance(payment.getWalletId());

        // 차변: 가맹점에서 차감
        LedgerEntry debit = LedgerEntry.builder()
            .transactionId(txId)
            .paymentId(payment.getId())
            .accountType(AccountType.MERCHANT)
            .accountId(payment.getMerchantId())
            .entryType(EntryType.DEBIT)
            .amount(payment.getAmount())
            .description("취소: " + payment.getOrderId())
            .build();

        // 대변: 사용자 월렛에 복구
        LedgerEntry credit = LedgerEntry.builder()
            .transactionId(txId)
            .paymentId(payment.getId())
            .accountType(AccountType.WALLET)
            .accountId(payment.getWalletId())
            .entryType(EntryType.CREDIT)
            .amount(payment.getAmount())
            .balanceAfter(walletBalanceAfter)
            .description("환불: " + payment.getOrderId())
            .build();

        ledgerRepository.saveAll(List.of(debit, credit));

        log.info("Cancellation ledger recorded: txId={}, paymentId={}, amount={}",
            txId, payment.getId(), payment.getAmount());
    }

    /**
     * 보상 트랜잭션 원장 기록
     */
    @Transactional
    public void recordCompensation(Payment payment) {
        String txId = UUID.randomUUID().toString();

        BigDecimal walletBalanceAfter = getWalletBalance(payment.getWalletId());

        // 대변: 사용자 월렛에 복구 (보상)
        LedgerEntry credit = LedgerEntry.builder()
            .transactionId(txId)
            .paymentId(payment.getId())
            .accountType(AccountType.WALLET)
            .accountId(payment.getWalletId())
            .entryType(EntryType.CREDIT)
            .amount(payment.getAmount())
            .balanceAfter(walletBalanceAfter)
            .description("보상: " + payment.getOrderId())
            .build();

        ledgerRepository.save(credit);

        log.info("Compensation ledger recorded: txId={}, paymentId={}, amount={}",
            txId, payment.getId(), payment.getAmount());
    }

    /**
     * 잔액 검증: 원장 합계와 월렛 잔액 일치 확인
     */
    @Transactional(readOnly = true)
    public boolean verifyBalance(Long walletId) {
        BigDecimal ledgerBalance = ledgerRepository.calculateBalance(AccountType.WALLET, walletId);
        BigDecimal walletBalance = getWalletBalance(walletId);

        // 원장의 계산 방식: CREDIT은 +, DEBIT은 -
        // 따라서 초기 잔액 - 결제 + 환불 = 현재 잔액
        boolean matches = ledgerBalance.compareTo(walletBalance) == 0;

        if (!matches) {
            log.warn("Balance mismatch! walletId={}, ledgerBalance={}, walletBalance={}",
                walletId, ledgerBalance, walletBalance);
        }

        return matches;
    }

    /**
     * 원장 조회
     */
    @Transactional(readOnly = true)
    public List<LedgerEntry> getEntriesByPayment(Long paymentId) {
        return ledgerRepository.findByPaymentId(paymentId);
    }

    @Transactional(readOnly = true)
    public List<LedgerEntry> getEntriesByAccount(AccountType accountType, Long accountId) {
        return ledgerRepository.findByAccountTypeAndAccountId(accountType, accountId);
    }

    private BigDecimal getWalletBalance(Long walletId) {
        return walletRepository.findById(walletId)
            .map(Wallet::getBalance)
            .orElse(BigDecimal.ZERO);
    }
}
