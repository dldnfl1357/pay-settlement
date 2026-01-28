package com.example.payment.domain.wallet;

import com.example.payment.domain.shared.Money;
import com.example.payment.domain.wallet.exception.InsufficientBalanceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Wallet Aggregate 테스트")
class WalletTest {

    private Wallet wallet;

    @BeforeEach
    void setUp() {
        wallet = Wallet.create(1L, "테스트유저", Money.of(100000));
    }

    @Nested
    @DisplayName("생성")
    class Creation {

        @Test
        @DisplayName("초기 잔액으로 생성")
        void createWithInitialBalance() {
            Wallet newWallet = Wallet.create(2L, "유저2", Money.of(50000));

            assertThat(newWallet.getBalance().getAmount()).isEqualByComparingTo("50000");
        }

        @Test
        @DisplayName("null 잔액은 0으로 초기화")
        void nullBalanceBecomesZero() {
            Wallet newWallet = Wallet.create(3L, "유저3", null);

            assertThat(newWallet.getBalance().getAmount()).isEqualByComparingTo("0");
        }
    }

    @Nested
    @DisplayName("차감")
    class Deduction {

        @Test
        @DisplayName("정상 차감")
        void deductNormally() {
            wallet.deduct(Money.of(30000));

            assertThat(wallet.getBalance().getAmount()).isEqualByComparingTo("70000");
        }

        @Test
        @DisplayName("잔액 전액 차감")
        void deductFullBalance() {
            wallet.deduct(Money.of(100000));

            assertThat(wallet.getBalance().getAmount()).isEqualByComparingTo("0");
        }

        @Test
        @DisplayName("잔액 부족 시 예외")
        void insufficientBalance() {
            assertThatThrownBy(() -> wallet.deduct(Money.of(150000)))
                .isInstanceOf(InsufficientBalanceException.class)
                .hasMessageContaining("Insufficient");
        }

        @Test
        @DisplayName("0 이하 금액 차감 시 예외")
        void deductZeroOrNegative() {
            assertThatThrownBy(() -> wallet.deduct(Money.of(0)))
                .isInstanceOf(IllegalArgumentException.class);

            assertThatThrownBy(() -> wallet.deduct(Money.of(-1000)))
                .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("복구")
    class Restoration {

        @Test
        @DisplayName("정상 복구")
        void restoreNormally() {
            wallet.deduct(Money.of(30000));
            wallet.restore(Money.of(30000));

            assertThat(wallet.getBalance().getAmount()).isEqualByComparingTo("100000");
        }

        @Test
        @DisplayName("0 이하 금액 복구 시 예외")
        void restoreZeroOrNegative() {
            assertThatThrownBy(() -> wallet.restore(Money.of(0)))
                .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("잔액 확인")
    class BalanceCheck {

        @Test
        @DisplayName("잔액 충분")
        void hasEnoughBalance() {
            assertThat(wallet.hasEnoughBalance(Money.of(100000))).isTrue();
            assertThat(wallet.hasEnoughBalance(Money.of(50000))).isTrue();
        }

        @Test
        @DisplayName("잔액 부족")
        void notEnoughBalance() {
            assertThat(wallet.hasEnoughBalance(Money.of(150000))).isFalse();
        }
    }
}
