package com.example.payment.domain.payment;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("CardToken VO 테스트")
class CardTokenTest {

    @Test
    @DisplayName("유효한 토큰 생성")
    void createValidToken() {
        CardToken token = CardToken.of("tok_test_1111");

        assertThat(token.getValue()).isEqualTo("tok_test_1111");
    }

    @Test
    @DisplayName("tok_ prefix 없으면 예외")
    void invalidPrefix() {
        assertThatThrownBy(() -> CardToken.of("invalid_token"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("tok_");
    }

    @Test
    @DisplayName("null이면 예외")
    void nullToken() {
        assertThatThrownBy(() -> CardToken.of(null))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("빈 문자열이면 예외")
    void blankToken() {
        assertThatThrownBy(() -> CardToken.of("  "))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("마스킹 처리")
    void masked() {
        CardToken token = CardToken.of("tok_test_1111");

        assertThat(token.masked()).isEqualTo("****1111");
    }

    @Test
    @DisplayName("마지막 4자리 추출")
    void lastFour() {
        CardToken token = CardToken.of("tok_test_1111");

        assertThat(token.lastFour()).isEqualTo("1111");
    }

    @Test
    @DisplayName("toString은 마스킹된 값 반환")
    void toStringIsMasked() {
        CardToken token = CardToken.of("tok_test_1111");

        assertThat(token.toString()).isEqualTo("****1111");
    }
}
