package com.example.settlement.batch;

import com.example.settlement.application.dto.MerchantDailySummary;
import com.example.settlement.domain.settlement.FeeRate;
import com.example.settlement.domain.settlement.Settlement;
import com.example.settlement.domain.settlement.SettlementStatus;
import com.example.settlement.domain.shared.Money;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Settlement Batch 테스트")
class SettlementBatchTest {

    @Test
    @DisplayName("ItemProcessor - 수수료/순정산액 계산")
    void processorCalculatesCorrectly() {
        // given
        SettlementItemProcessor processor = new SettlementItemProcessor();

        MerchantDailySummary summary = MerchantDailySummary.builder()
            .merchantId(1L)
            .date(LocalDate.of(2024, 1, 15))
            .totalSales(Money.of(150000))
            .totalCancel(Money.of(30000))
            .transactionCount(10)
            .feeRate(new BigDecimal("0.025"))
            .build();

        // when
        Settlement settlement = processor.process(summary);

        // then
        assertThat(settlement).isNotNull();
        assertThat(settlement.getMerchantId()).isEqualTo(1L);
        assertThat(settlement.getTotalSales().getAmount()).isEqualByComparingTo("150000");
        assertThat(settlement.getTotalCancel().getAmount()).isEqualByComparingTo("30000");

        // 수수료: 150000 * 0.025 = 3750
        assertThat(settlement.getTotalFee().getAmount()).isEqualByComparingTo("3750");

        // 순정산액: 150000 - 30000 - 3750 = 116250
        assertThat(settlement.getNetAmount().getAmount()).isEqualByComparingTo("116250");

        assertThat(settlement.getTransactionCount()).isEqualTo(10);
        assertThat(settlement.getStatus()).isEqualTo(SettlementStatus.PENDING);
    }

    @Test
    @DisplayName("정산 시나리오 - D+2 배치")
    void d2SettlementScenario() {
        /*
         * 시나리오:
         * - 전일 결제: 3건 (총 150,000원)
         * - 전일 취소: 1건 (50,000원)
         * - 수수료율: 2.5%
         *
         * 예상:
         * - 총 결제액: 150,000원
         * - 총 취소액: 50,000원
         * - 수수료: 150,000 * 0.025 = 3,750원
         * - 순정산액: 150,000 - 50,000 - 3,750 = 96,250원
         */

        MerchantDailySummary summary = MerchantDailySummary.builder()
            .merchantId(1L)
            .date(LocalDate.now().minusDays(1))
            .totalSales(Money.of(150000))
            .totalCancel(Money.of(50000))
            .transactionCount(3)
            .feeRate(new BigDecimal("0.025"))
            .build();

        SettlementItemProcessor processor = new SettlementItemProcessor();
        Settlement settlement = processor.process(summary);

        assertThat(settlement.getTotalFee().getAmount()).isEqualByComparingTo("3750");
        assertThat(settlement.getNetAmount().getAmount()).isEqualByComparingTo("96250");
    }

    @Test
    @DisplayName("다양한 수수료율 테스트")
    void variousFeeRates() {
        SettlementItemProcessor processor = new SettlementItemProcessor();

        // 2.0% 수수료
        MerchantDailySummary summary1 = MerchantDailySummary.builder()
            .merchantId(1L)
            .date(LocalDate.now())
            .totalSales(Money.of(100000))
            .totalCancel(Money.zero())
            .transactionCount(1)
            .feeRate(new BigDecimal("0.020"))
            .build();

        Settlement s1 = processor.process(summary1);
        assertThat(s1.getTotalFee().getAmount()).isEqualByComparingTo("2000");
        assertThat(s1.getNetAmount().getAmount()).isEqualByComparingTo("98000");

        // 3.0% 수수료
        MerchantDailySummary summary2 = MerchantDailySummary.builder()
            .merchantId(2L)
            .date(LocalDate.now())
            .totalSales(Money.of(100000))
            .totalCancel(Money.zero())
            .transactionCount(1)
            .feeRate(new BigDecimal("0.030"))
            .build();

        Settlement s2 = processor.process(summary2);
        assertThat(s2.getTotalFee().getAmount()).isEqualByComparingTo("3000");
        assertThat(s2.getNetAmount().getAmount()).isEqualByComparingTo("97000");
    }
}
