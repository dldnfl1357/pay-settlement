package com.example.settlement.domain.settlement;

import com.example.settlement.domain.settlement.exception.InvalidSettlementStateException;
import com.example.settlement.domain.shared.Money;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Settlement Aggregate 테스트")
class SettlementTest {

    @Nested
    @DisplayName("createFromSummary 팩토리")
    class CreateFromSummary {

        @Test
        @DisplayName("정산 생성 - 수수료/순정산액 계산")
        void createSettlement() {
            // given
            Long merchantId = 1L;
            LocalDate date = LocalDate.of(2024, 1, 15);
            Money totalSales = Money.of(100000);
            Money totalCancel = Money.of(20000);
            FeeRate feeRate = FeeRate.of("0.025"); // 2.5%
            int txCount = 5;

            // when
            Settlement settlement = Settlement.createFromSummary(
                merchantId, date, totalSales, totalCancel, feeRate, txCount
            );

            // then
            assertThat(settlement.getMerchantId()).isEqualTo(merchantId);
            assertThat(settlement.getSettlementDate()).isEqualTo(date);
            assertThat(settlement.getTotalSales().getAmount()).isEqualByComparingTo("100000");
            assertThat(settlement.getTotalCancel().getAmount()).isEqualByComparingTo("20000");

            // 수수료: 100000 * 0.025 = 2500
            assertThat(settlement.getTotalFee().getAmount()).isEqualByComparingTo("2500");

            // 순정산액: 100000 - 20000 - 2500 = 77500
            assertThat(settlement.getNetAmount().getAmount()).isEqualByComparingTo("77500");

            assertThat(settlement.getTransactionCount()).isEqualTo(5);
            assertThat(settlement.getStatus()).isEqualTo(SettlementStatus.PENDING);
        }

        @Test
        @DisplayName("취소 없는 정산")
        void createSettlementWithoutCancel() {
            Settlement settlement = Settlement.createFromSummary(
                1L, LocalDate.now(),
                Money.of(50000), Money.zero(),
                FeeRate.of("0.025"), 3
            );

            // 수수료: 50000 * 0.025 = 1250
            assertThat(settlement.getTotalFee().getAmount()).isEqualByComparingTo("1250");
            // 순정산액: 50000 - 0 - 1250 = 48750
            assertThat(settlement.getNetAmount().getAmount()).isEqualByComparingTo("48750");
        }

        @Test
        @DisplayName("수수료율에 따른 순정산액 차이")
        void differentFeeRates() {
            Money totalSales = Money.of(100000);
            Money totalCancel = Money.zero();

            // 2.5% 수수료
            Settlement s1 = Settlement.createFromSummary(
                1L, LocalDate.now(), totalSales, totalCancel,
                FeeRate.of("0.025"), 1
            );
            assertThat(s1.getNetAmount().getAmount()).isEqualByComparingTo("97500");

            // 2.0% 수수료
            Settlement s2 = Settlement.createFromSummary(
                1L, LocalDate.now(), totalSales, totalCancel,
                FeeRate.of("0.020"), 1
            );
            assertThat(s2.getNetAmount().getAmount()).isEqualByComparingTo("98000");
        }
    }

    @Nested
    @DisplayName("상태 전이")
    class StateTransition {

        private Settlement settlement;

        @BeforeEach
        void setUp() {
            settlement = Settlement.createFromSummary(
                1L, LocalDate.now(),
                Money.of(100000), Money.zero(),
                FeeRate.of("0.025"), 5
            );
        }

        @Test
        @DisplayName("PENDING → CONFIRMED")
        void confirmFromPending() {
            settlement.confirm();

            assertThat(settlement.getStatus()).isEqualTo(SettlementStatus.CONFIRMED);
            assertThat(settlement.getConfirmedAt()).isNotNull();
        }

        @Test
        @DisplayName("CONFIRMED → PAID")
        void markAsPaidFromConfirmed() {
            settlement.confirm();
            settlement.markAsPaid();

            assertThat(settlement.getStatus()).isEqualTo(SettlementStatus.PAID);
            assertThat(settlement.getPaidAt()).isNotNull();
        }

        @Test
        @DisplayName("CONFIRMED 상태가 아니면 confirm 불가")
        void cannotConfirmNonPending() {
            settlement.confirm();

            assertThatThrownBy(() -> settlement.confirm())
                .isInstanceOf(InvalidSettlementStateException.class);
        }

        @Test
        @DisplayName("CONFIRMED가 아니면 markAsPaid 불가")
        void cannotMarkAsPaidNonConfirmed() {
            assertThatThrownBy(() -> settlement.markAsPaid())
                .isInstanceOf(InvalidSettlementStateException.class);
        }

        @Test
        @DisplayName("전체 흐름: PENDING → CONFIRMED → PAID")
        void fullStateTransition() {
            assertThat(settlement.getStatus()).isEqualTo(SettlementStatus.PENDING);

            settlement.confirm();
            assertThat(settlement.getStatus()).isEqualTo(SettlementStatus.CONFIRMED);

            settlement.markAsPaid();
            assertThat(settlement.getStatus()).isEqualTo(SettlementStatus.PAID);
        }
    }
}
