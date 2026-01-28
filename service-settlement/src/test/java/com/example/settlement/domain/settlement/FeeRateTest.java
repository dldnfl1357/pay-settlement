package com.example.settlement.domain.settlement;

import com.example.settlement.domain.shared.Money;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Settlement FeeRate VO 테스트")
class FeeRateTest {

    @Test
    @DisplayName("기본 수수료율 2.5%")
    void defaultFeeRate() {
        FeeRate feeRate = FeeRate.defaultRate();
        assertThat(feeRate.getValue()).isEqualByComparingTo("0.025");
    }

    @Test
    @DisplayName("수수료 계산")
    void calculateFee() {
        FeeRate feeRate = FeeRate.of("0.025");
        Money amount = Money.of(100000);

        Money fee = feeRate.calculateFee(amount);

        assertThat(fee.getAmount()).isEqualByComparingTo("2500");
    }

    @Test
    @DisplayName("수수료 계산 - 소수점 절사")
    void calculateFeeWithTruncation() {
        FeeRate feeRate = FeeRate.of("0.025");
        Money amount = Money.of(33333); // 33333 * 0.025 = 833.325

        Money fee = feeRate.calculateFee(amount);

        assertThat(fee.getAmount()).isEqualByComparingTo("833"); // 절사
    }

    @Test
    @DisplayName("유효하지 않은 수수료율 - 음수")
    void invalidNegativeFeeRate() {
        assertThatThrownBy(() -> FeeRate.of("-0.01"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("유효하지 않은 수수료율 - 100% 이상")
    void invalidOver100Percent() {
        assertThatThrownBy(() -> FeeRate.of("1.0"))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
