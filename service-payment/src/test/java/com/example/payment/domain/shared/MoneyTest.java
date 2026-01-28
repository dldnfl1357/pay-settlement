package com.example.payment.domain.shared;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Money VO 테스트")
class MoneyTest {

    @Nested
    @DisplayName("생성")
    class Creation {

        @Test
        @DisplayName("BigDecimal로 생성")
        void createFromBigDecimal() {
            Money money = Money.of(new BigDecimal("10000"));
            assertThat(money.getAmount()).isEqualByComparingTo("10000");
        }

        @Test
        @DisplayName("long으로 생성")
        void createFromLong() {
            Money money = Money.of(10000L);
            assertThat(money.getAmount()).isEqualByComparingTo("10000");
        }

        @Test
        @DisplayName("String으로 생성")
        void createFromString() {
            Money money = Money.of("10000.50");
            assertThat(money.getAmount()).isEqualByComparingTo("10000.50");
        }

        @Test
        @DisplayName("null로 생성 시 예외")
        void createFromNull() {
            assertThatThrownBy(() -> Money.of((BigDecimal) null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("null");
        }

        @Test
        @DisplayName("zero 생성")
        void createZero() {
            Money money = Money.zero();
            assertThat(money.getAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        }
    }

    @Nested
    @DisplayName("연산")
    class Operations {

        @Test
        @DisplayName("덧셈")
        void add() {
            Money a = Money.of(10000);
            Money b = Money.of(5000);

            Money result = a.add(b);

            assertThat(result.getAmount()).isEqualByComparingTo("15000");
            // 불변성 검증
            assertThat(a.getAmount()).isEqualByComparingTo("10000");
        }

        @Test
        @DisplayName("뺄셈")
        void subtract() {
            Money a = Money.of(10000);
            Money b = Money.of(3000);

            Money result = a.subtract(b);

            assertThat(result.getAmount()).isEqualByComparingTo("7000");
        }

        @Test
        @DisplayName("수수료 계산 (소수점 이하 절사)")
        void multiplyAndFloor() {
            Money amount = Money.of(10000);
            BigDecimal feeRate = new BigDecimal("0.025"); // 2.5%

            Money fee = amount.multiplyAndFloor(feeRate);

            assertThat(fee.getAmount()).isEqualByComparingTo("250");
        }

        @Test
        @DisplayName("수수료 계산 - 소수점 절사 확인")
        void multiplyAndFloorTruncates() {
            Money amount = Money.of(10001);
            BigDecimal feeRate = new BigDecimal("0.025");

            Money fee = amount.multiplyAndFloor(feeRate);

            // 10001 * 0.025 = 250.025 -> 250 (절사)
            assertThat(fee.getAmount()).isEqualByComparingTo("250");
        }
    }

    @Nested
    @DisplayName("비교")
    class Comparison {

        @Test
        @DisplayName("크거나 같음 - true")
        void isGreaterThanOrEqual_true() {
            Money a = Money.of(10000);
            Money b = Money.of(10000);
            Money c = Money.of(5000);

            assertThat(a.isGreaterThanOrEqual(b)).isTrue();
            assertThat(a.isGreaterThanOrEqual(c)).isTrue();
        }

        @Test
        @DisplayName("크거나 같음 - false")
        void isGreaterThanOrEqual_false() {
            Money a = Money.of(5000);
            Money b = Money.of(10000);

            assertThat(a.isGreaterThanOrEqual(b)).isFalse();
        }

        @Test
        @DisplayName("양수 확인")
        void isPositive() {
            assertThat(Money.of(100).isPositive()).isTrue();
            assertThat(Money.of(0).isPositive()).isFalse();
            assertThat(Money.of(-100).isPositive()).isFalse();
        }

        @Test
        @DisplayName("0 확인")
        void isZero() {
            assertThat(Money.zero().isZero()).isTrue();
            assertThat(Money.of(100).isZero()).isFalse();
        }
    }

    @Nested
    @DisplayName("동등성")
    class Equality {

        @Test
        @DisplayName("같은 금액은 동등")
        void equalMoney() {
            Money a = Money.of(10000);
            Money b = Money.of(10000);

            assertThat(a).isEqualTo(b);
            assertThat(a.hashCode()).isEqualTo(b.hashCode());
        }

        @Test
        @DisplayName("다른 금액은 동등하지 않음")
        void notEqualMoney() {
            Money a = Money.of(10000);
            Money b = Money.of(20000);

            assertThat(a).isNotEqualTo(b);
        }
    }
}
