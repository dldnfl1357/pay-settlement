package com.example.payment.domain.merchant;

import com.example.payment.domain.shared.Money;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("FeeRate VO 테스트")
class FeeRateTest {

    @Test
    @DisplayName("유효한 수수료율 생성")
    void createValidFeeRate() {
        FeeRate feeRate = FeeRate.of("0.025");

        assertThat(feeRate.getValue()).isEqualByComparingTo("0.025");
    }

    @Test
    @DisplayName("기본 수수료율 2.5%")
    void defaultRate() {
        FeeRate feeRate = FeeRate.defaultRate();

        assertThat(feeRate.getValue()).isEqualByComparingTo("0.025");
    }

    @Test
    @DisplayName("음수 수수료율 예외")
    void negativeFeeRate() {
        assertThatThrownBy(() -> FeeRate.of("-0.01"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("100% 이상 수수료율 예외")
    void overHundredPercent() {
        assertThatThrownBy(() -> FeeRate.of("1.0"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("수수료 계산")
    void calculateFee() {
        FeeRate feeRate = FeeRate.of("0.025"); // 2.5%
        Money amount = Money.of(100000);

        Money fee = feeRate.calculateFee(amount);

        assertThat(fee.getAmount()).isEqualByComparingTo("2500");
    }

    @Test
    @DisplayName("수수료 계산 - 소수점 절사")
    void calculateFeeWithTruncation() {
        FeeRate feeRate = FeeRate.of("0.025");
        Money amount = Money.of(10001); // 10001 * 0.025 = 250.025

        Money fee = feeRate.calculateFee(amount);

        assertThat(fee.getAmount()).isEqualByComparingTo("250"); // 절사
    }

    @Test
    @DisplayName("toString 퍼센트 표시")
    void toStringShowsPercent() {
        FeeRate feeRate = FeeRate.of("0.025");

        assertThat(feeRate.toString()).isEqualTo("2.5%");
    }
}
