package com.example.payment.integration;

import com.example.payment.application.PaymentApplicationService;
import com.example.payment.application.dto.CreatePaymentCommand;
import com.example.payment.application.dto.PaymentResult;
import com.example.payment.domain.payment.PaymentStatus;
import com.example.payment.domain.shared.Money;
import com.example.payment.domain.wallet.Wallet;
import com.example.payment.domain.wallet.WalletRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
@DisplayName("Payment 통합 테스트")
class PaymentIntegrationTest {

    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>(DockerImageName.parse("mysql:8.0"))
        .withDatabaseName("payment_test")
        .withUsername("test")
        .withPassword("test");

    @Container
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
        .withExposedPorts(6379);

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");

        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
        registry.add("redisson.address", () ->
            "redis://" + redis.getHost() + ":" + redis.getMappedPort(6379));

        // Kafka 비활성화 (통합 테스트에서는 이벤트 발행 생략)
        registry.add("spring.kafka.bootstrap-servers", () -> "localhost:9092");
        registry.add("spring.autoconfigure.exclude", () ->
            "org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration");
    }

    @Autowired
    private PaymentApplicationService paymentService;

    @Autowired
    private WalletRepository walletRepository;

    private Wallet testWallet;

    @BeforeEach
    void setUp() {
        // 테스트 월렛 생성
        testWallet = Wallet.create(100L, "통합테스트유저", Money.of(1000000));
        testWallet = walletRepository.save(testWallet);
    }

    @Test
    @DisplayName("결제 생성 → 승인 → 취소 전체 흐름")
    void fullPaymentFlow() {
        // 1. 결제 생성
        String idempotencyKey = UUID.randomUUID().toString();
        CreatePaymentCommand createCommand = CreatePaymentCommand.builder()
            .walletId(testWallet.getId())
            .merchantId(1L)
            .orderId("INT-ORDER-001")
            .amount(new BigDecimal("50000"))
            .cardToken("tok_test_1111")
            .productName("통합테스트 상품")
            .idempotencyKey(idempotencyKey)
            .build();

        PaymentResult created = paymentService.createPayment(createCommand);

        assertThat(created.getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(created.getOrderId()).isEqualTo("INT-ORDER-001");

        // 2. 결제 승인
        PaymentResult approved = paymentService.approvePayment(created.getId());

        assertThat(approved.getStatus()).isEqualTo(PaymentStatus.APPROVED);
        assertThat(approved.getPgTransactionId()).startsWith("PG-");

        // 잔액 차감 확인
        Wallet updatedWallet = walletRepository.findById(testWallet.getId()).orElseThrow();
        assertThat(updatedWallet.getBalance().getAmount())
            .isEqualByComparingTo("950000"); // 1000000 - 50000

        // 3. 결제 취소
        PaymentResult cancelled = paymentService.cancelPayment(approved.getId());

        assertThat(cancelled.getStatus()).isEqualTo(PaymentStatus.CANCELLED);

        // 잔액 복구 확인
        Wallet restoredWallet = walletRepository.findById(testWallet.getId()).orElseThrow();
        assertThat(restoredWallet.getBalance().getAmount())
            .isEqualByComparingTo("1000000"); // 복구됨
    }

    @Test
    @DisplayName("멱등성 키 중복 요청 - 동일 결과 반환")
    void idempotencyTest() {
        String idempotencyKey = UUID.randomUUID().toString();
        CreatePaymentCommand command = CreatePaymentCommand.builder()
            .walletId(testWallet.getId())
            .merchantId(1L)
            .orderId("IDEM-ORDER-001")
            .amount(new BigDecimal("30000"))
            .cardToken("tok_test_1111")
            .productName("멱등성 테스트")
            .idempotencyKey(idempotencyKey)
            .build();

        // 첫 번째 요청
        PaymentResult first = paymentService.createPayment(command);

        // 두 번째 요청 (동일 멱등성 키)
        PaymentResult second = paymentService.createPayment(command);

        // 같은 결과
        assertThat(first.getOrderId()).isEqualTo(second.getOrderId());
        assertThat(first.getAmount()).isEqualByComparingTo(second.getAmount());
    }

    @Test
    @DisplayName("PG 실패 시 잔액 복구 (Saga 보상)")
    void sagaCompensationOnPgFailure() {
        // tok_test_2222 → PG 잔액 부족 응답
        String idempotencyKey = UUID.randomUUID().toString();
        CreatePaymentCommand command = CreatePaymentCommand.builder()
            .walletId(testWallet.getId())
            .merchantId(1L)
            .orderId("SAGA-ORDER-001")
            .amount(new BigDecimal("50000"))
            .cardToken("tok_test_2222") // PG 실패 유도
            .productName("Saga 테스트")
            .idempotencyKey(idempotencyKey)
            .build();

        PaymentResult created = paymentService.createPayment(command);
        Money balanceBefore = walletRepository.findById(testWallet.getId())
            .orElseThrow().getBalance();

        // 승인 시도 - PG 실패 예상
        try {
            paymentService.approvePayment(created.getId());
        } catch (Exception e) {
            // PG 실패 예외 발생
        }

        // 잔액 복구 확인 (보상 트랜잭션)
        Money balanceAfter = walletRepository.findById(testWallet.getId())
            .orElseThrow().getBalance();

        assertThat(balanceAfter.getAmount()).isEqualByComparingTo(balanceBefore.getAmount());
    }
}
