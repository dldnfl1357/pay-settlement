# 간편 결제 시스템 시뮬레이터 - 설계 문서

## 1. 시스템 아키텍처

### 1.1 전체 구조

```
                                   ┌─────────────────┐
                                   │   Client/Test   │
                                   └────────┬────────┘
                                            │
                                            ▼
┌───────────────────────────────────────────────────────────────────────────┐
│                            Kubernetes Cluster                              │
├───────────────────────────────────────────────────────────────────────────┤
│                                                                           │
│   ┌─────────────────────────┐       ┌─────────────────────────┐          │
│   │    payment-service      │       │  settlement-service     │          │
│   │    (Spring Boot)        │       │  (Spring Boot + Batch)  │          │
│   │                         │       │                         │          │
│   │  • 결제 승인/취소        │       │  • 일별 정산 배치        │          │
│   │  • 이중지불 방지         │       │  • 수수료 계산          │          │
│   │  • 원장 기록            │       │  • 정산 리포트          │          │
│   │  • Saga 보상 트랜잭션   │       │                         │          │
│   └───────────┬─────────────┘       └───────────┬─────────────┘          │
│               │                                 │                         │
│               │         ┌───────────────────────┘                         │
│               │         │                                                 │
│               ▼         ▼                                                 │
│   ┌─────────────────────────────────────────────────────────┐            │
│   │                        Kafka                             │            │
│   │  Topics:                                                 │            │
│   │  • payment.requested    • payment.approved               │            │
│   │  • payment.cancelled    • payment.failed                 │            │
│   │  • settlement.completed                                  │            │
│   └─────────────────────────────────────────────────────────┘            │
│               │                                                           │
│               ▼                                                           │
│   ┌─────────────────────┐       ┌─────────────────────┐                  │
│   │       Redis         │       │       MySQL         │                  │
│   │                     │       │                     │                  │
│   │  • 멱등성 키 저장    │       │  • Payment          │                  │
│   │  • 분산 락          │       │  • Settlement       │                  │
│   │  • 잔액 캐시        │       │  • Ledger           │                  │
│   │                     │       │  • Wallet           │                  │
│   └─────────────────────┘       └─────────────────────┘                  │
│                                                                           │
└───────────────────────────────────────────────────────────────────────────┘
```

### 1.2 서비스 구성

| 서비스 | 포트 | 역할 |
|--------|------|------|
| payment-service | 8080 | 결제 처리, 원장 기록 |
| settlement-service | 8081 | 정산 배치, 리포트 |
| kafka | 9092 | 이벤트 브로커 |
| redis | 6379 | 캐시, 분산 락 |
| mysql | 3306 | 영구 저장소 |

---

## 2. 도메인 모델

### 2.1 ERD

```
┌─────────────────┐       ┌─────────────────┐
│     Wallet      │       │    Merchant     │
├─────────────────┤       ├─────────────────┤
│ id (PK)         │       │ id (PK)         │
│ user_id         │       │ name            │
│ balance         │       │ fee_rate        │
│ version         │       │ created_at      │
│ created_at      │       └────────┬────────┘
│ updated_at      │                │
└────────┬────────┘                │
         │                         │
         │    ┌────────────────────┘
         │    │
         ▼    ▼
┌─────────────────────────────────────┐
│              Payment                │
├─────────────────────────────────────┤
│ id (PK)                             │
│ wallet_id (FK)                      │
│ merchant_id (FK)                    │
│ order_id                            │
│ amount                              │
│ status                              │
│ idempotency_key (UNIQUE)            │
│ pg_transaction_id                   │
│ created_at                          │
│ approved_at                         │
│ cancelled_at                        │
└─────────────────┬───────────────────┘
                  │
                  ▼
┌─────────────────────────────────────┐
│           LedgerEntry               │
├─────────────────────────────────────┤
│ id (PK)                             │
│ transaction_id                      │
│ payment_id (FK)                     │
│ account_type (WALLET/MERCHANT)      │
│ account_id                          │
│ entry_type (DEBIT/CREDIT)           │
│ amount                              │
│ balance_after                       │
│ created_at                          │
└─────────────────────────────────────┘

┌─────────────────────────────────────┐
│           Settlement                │
├─────────────────────────────────────┤
│ id (PK)                             │
│ merchant_id (FK)                    │
│ settlement_date                     │
│ total_sales                         │
│ total_cancel                        │
│ total_fee                           │
│ net_amount                          │
│ status (PENDING/CONFIRMED/PAID)     │
│ created_at                          │
│ confirmed_at                        │
│ paid_at                             │
└─────────────────────────────────────┘
```

### 2.2 핵심 엔티티

#### Payment

```java
@Entity
@Table(name = "payments", indexes = {
    @Index(name = "idx_idempotency", columnList = "idempotency_key", unique = true)
})
public class Payment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    private Long walletId;
    private Long merchantId;
    private String orderId;
    
    @Column(precision = 15, scale = 2)
    private BigDecimal amount;
    
    @Enumerated(EnumType.STRING)
    private PaymentStatus status;  // PENDING, APPROVED, CANCELLED, FAILED
    
    @Column(name = "idempotency_key", unique = true)
    private String idempotencyKey;
    
    private String pgTransactionId;
    
    private LocalDateTime createdAt;
    private LocalDateTime approvedAt;
    private LocalDateTime cancelledAt;
}

public enum PaymentStatus {
    PENDING,    // 결제 대기
    APPROVED,   // 승인 완료
    CANCELLED,  // 취소
    FAILED      // 실패
}
```

#### Wallet

```java
@Entity
@Table(name = "wallets")
public class Wallet {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    private Long userId;
    
    @Column(precision = 15, scale = 2)
    private BigDecimal balance;
    
    @Version
    private Long version;  // 낙관적 잠금
    
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    
    public void deduct(BigDecimal amount) {
        if (this.balance.compareTo(amount) < 0) {
            throw new InsufficientBalanceException();
        }
        this.balance = this.balance.subtract(amount);
    }
    
    public void restore(BigDecimal amount) {
        this.balance = this.balance.add(amount);
    }
}
```

#### LedgerEntry

```java
@Entity
@Table(name = "ledger_entries")
@Immutable
public class LedgerEntry {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    private String transactionId;  // UUID
    private Long paymentId;
    
    @Enumerated(EnumType.STRING)
    private AccountType accountType;  // WALLET, MERCHANT, PLATFORM
    
    private Long accountId;
    
    @Enumerated(EnumType.STRING)
    private EntryType entryType;  // DEBIT, CREDIT
    
    @Column(precision = 15, scale = 2)
    private BigDecimal amount;
    
    @Column(precision = 15, scale = 2)
    private BigDecimal balanceAfter;
    
    private String description;
    private LocalDateTime createdAt;
}
```

---

## 3. 핵심 설계

### 3.1 이중 지불 방지

#### 3.1.1 멱등성 키 처리

```java
@Service
@RequiredArgsConstructor
public class IdempotencyService {
    private final StringRedisTemplate redisTemplate;
    private static final String PREFIX = "idempotency:";
    private static final Duration TTL = Duration.ofHours(24);
    
    public Optional<PaymentResponse> check(String key) {
        String cached = redisTemplate.opsForValue().get(PREFIX + key);
        if (cached != null) {
            return Optional.of(deserialize(cached));
        }
        return Optional.empty();
    }
    
    public boolean tryAcquire(String key) {
        return Boolean.TRUE.equals(
            redisTemplate.opsForValue().setIfAbsent(
                PREFIX + key, 
                "PROCESSING", 
                TTL
            )
        );
    }
    
    public void complete(String key, PaymentResponse response) {
        redisTemplate.opsForValue().set(
            PREFIX + key, 
            serialize(response), 
            TTL
        );
    }
}
```

#### 3.1.2 분산 락

```java
@Service
@RequiredArgsConstructor
public class DistributedLockService {
    private final RedissonClient redisson;
    
    public <T> T executeWithLock(String key, Supplier<T> action) {
        RLock lock = redisson.getLock("lock:" + key);
        try {
            if (!lock.tryLock(5, 10, TimeUnit.SECONDS)) {
                throw new LockAcquisitionException();
            }
            return action.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new LockAcquisitionException();
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }
}
```

### 3.2 Saga 패턴 (보상 트랜잭션)

```java
@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentSagaService {
    private final WalletService walletService;
    private final PgClient pgClient;
    private final PaymentRepository paymentRepository;
    private final LedgerService ledgerService;
    private final PaymentEventPublisher eventPublisher;
    
    @Transactional
    public PaymentResponse processPayment(PaymentRequest request) {
        Payment payment = createPayment(request);
        
        try {
            // Step 1: 잔액 차감
            walletService.deduct(request.getWalletId(), request.getAmount());
            
            // Step 2: PG 승인
            PgResponse pgResponse = pgClient.approve(request);
            
            if (!pgResponse.isSuccess()) {
                throw new PgApprovalException(pgResponse.getMessage());
            }
            
            // Step 3: 결제 완료
            payment.approve(pgResponse.getTransactionId());
            paymentRepository.save(payment);
            
            // Step 4: 원장 기록
            ledgerService.record(payment);
            
            // Step 5: 이벤트 발행
            eventPublisher.publish(PaymentApprovedEvent.from(payment));
            
            return PaymentResponse.success(payment);
            
        } catch (Exception e) {
            // 보상 트랜잭션
            compensate(payment, request);
            throw e;
        }
    }
    
    private void compensate(Payment payment, PaymentRequest request) {
        log.warn("Saga 보상 시작: paymentId={}", payment.getId());
        
        // 잔액 복구
        walletService.restore(request.getWalletId(), request.getAmount());
        
        // 결제 실패 처리
        payment.fail();
        paymentRepository.save(payment);
        
        // 보상 원장 기록
        ledgerService.recordCompensation(payment);
        
        // 실패 이벤트 발행
        eventPublisher.publish(PaymentFailedEvent.from(payment));
    }
}
```

### 3.3 복식부기 원장

```java
@Service
@RequiredArgsConstructor
public class LedgerService {
    private final LedgerEntryRepository ledgerRepository;
    
    @Transactional
    public void record(Payment payment) {
        String txId = UUID.randomUUID().toString();
        LocalDateTime now = LocalDateTime.now();
        
        // 차변: 사용자 월렛에서 차감
        LedgerEntry debit = LedgerEntry.builder()
            .transactionId(txId)
            .paymentId(payment.getId())
            .accountType(AccountType.WALLET)
            .accountId(payment.getWalletId())
            .entryType(EntryType.DEBIT)
            .amount(payment.getAmount())
            .description("결제: " + payment.getOrderId())
            .createdAt(now)
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
            .createdAt(now)
            .build();
        
        ledgerRepository.saveAll(List.of(debit, credit));
    }
    
    // 잔액 검증: 원장 합계 = 월렛 잔액
    public boolean verifyBalance(Long walletId, BigDecimal expectedBalance) {
        BigDecimal ledgerBalance = ledgerRepository.calculateBalance(
            AccountType.WALLET, walletId
        );
        return ledgerBalance.compareTo(expectedBalance) == 0;
    }
}
```

### 3.4 정산 배치

```java
@Configuration
@EnableBatchProcessing
@RequiredArgsConstructor
public class SettlementBatchConfig {
    private final JobBuilderFactory jobBuilderFactory;
    private final StepBuilderFactory stepBuilderFactory;
    
    @Bean
    public Job settlementJob() {
        return jobBuilderFactory.get("settlementJob")
            .start(aggregateStep())
            .next(calculateFeeStep())
            .next(createSettlementStep())
            .build();
    }
    
    @Bean
    public Step aggregateStep() {
        return stepBuilderFactory.get("aggregateStep")
            .<Payment, MerchantDailySummary>chunk(1000)
            .reader(paymentReader())
            .processor(aggregateProcessor())
            .writer(summaryWriter())
            .build();
    }
}

@Component
public class SettlementProcessor implements ItemProcessor<MerchantDailySummary, Settlement> {
    
    @Override
    public Settlement process(MerchantDailySummary summary) {
        BigDecimal fee = summary.getTotalSales()
            .multiply(summary.getFeeRate())
            .setScale(0, RoundingMode.DOWN);
        
        BigDecimal netAmount = summary.getTotalSales()
            .subtract(summary.getTotalCancel())
            .subtract(fee);
        
        return Settlement.builder()
            .merchantId(summary.getMerchantId())
            .settlementDate(summary.getDate())
            .totalSales(summary.getTotalSales())
            .totalCancel(summary.getTotalCancel())
            .totalFee(fee)
            .netAmount(netAmount)
            .status(SettlementStatus.PENDING)
            .build();
    }
}
```

---

## 4. PCI-DSS 기초 적용

### 4.1 카드번호 토큰화

```java
@Service
public class TokenizationService {
    // 실제로는 외부 토큰화 서비스 또는 HSM 사용
    // 여기서는 학습 목적으로 단순화
    
    public String tokenize(String cardNumber) {
        // 원본 저장 금지 - 토큰만 생성
        String token = "tok_" + UUID.randomUUID().toString().replace("-", "");
        // 토큰 매핑은 별도 보안 저장소에 (이 프로젝트에서는 Mock)
        return token;
    }
    
    public String getLastFourDigits(String cardNumber) {
        return cardNumber.substring(cardNumber.length() - 4);
    }
}
```

### 4.2 로그 마스킹

```java
@Component
public class SensitiveDataMasker {
    
    public String maskCardNumber(String cardNumber) {
        if (cardNumber == null || cardNumber.length() < 4) {
            return "****";
        }
        return "*".repeat(cardNumber.length() - 4) 
             + cardNumber.substring(cardNumber.length() - 4);
    }
    
    public String maskAmount(BigDecimal amount) {
        // 금액은 로그에서 범위로 표시
        if (amount.compareTo(new BigDecimal("10000")) < 0) {
            return "~1만원";
        } else if (amount.compareTo(new BigDecimal("100000")) < 0) {
            return "1~10만원";
        } else {
            return "10만원~";
        }
    }
}

// Logback 설정에서 패턴 적용
// logback-spring.xml
```

### 4.3 감사 로그

```java
@Aspect
@Component
@Slf4j
public class AuditLogAspect {
    
    @Around("@annotation(Audited)")
    public Object audit(ProceedingJoinPoint joinPoint) throws Throwable {
        String action = joinPoint.getSignature().getName();
        Object[] args = joinPoint.getArgs();
        
        AuditLog auditLog = AuditLog.builder()
            .action(action)
            .timestamp(LocalDateTime.now())
            .requestId(MDC.get("requestId"))
            .build();
        
        try {
            Object result = joinPoint.proceed();
            auditLog.setStatus("SUCCESS");
            return result;
        } catch (Exception e) {
            auditLog.setStatus("FAILED");
            auditLog.setErrorMessage(e.getMessage());
            throw e;
        } finally {
            log.info("AUDIT: {}", auditLog);
        }
    }
}

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Audited {
    String action() default "";
}
```

---

## 5. Kafka 이벤트

### 5.1 토픽 구조

| 토픽 | Producer | Consumer | 용도 |
|------|----------|----------|------|
| payment.requested | payment-service | - | 결제 요청 기록 |
| payment.approved | payment-service | settlement-service | 정산 대상 수집 |
| payment.cancelled | payment-service | settlement-service | 취소 반영 |
| payment.failed | payment-service | - | 실패 모니터링 |

### 5.2 이벤트 스키마

```java
@Data
@Builder
public class PaymentEvent {
    private String eventId;
    private String eventType;
    private LocalDateTime timestamp;
    private PaymentPayload payload;
    
    @Data
    @Builder
    public static class PaymentPayload {
        private Long paymentId;
        private Long merchantId;
        private Long walletId;
        private BigDecimal amount;
        private String status;
    }
}
```

---

## 6. Kubernetes 배포

### 6.1 리소스 구성

```
k8s/
├── namespace.yaml
├── configmap.yaml
├── secrets.yaml
├── mysql/
│   ├── deployment.yaml
│   ├── service.yaml
│   └── pvc.yaml
├── redis/
│   ├── deployment.yaml
│   └── service.yaml
├── kafka/
│   ├── zookeeper.yaml
│   ├── kafka.yaml
│   └── service.yaml
├── payment-service/
│   ├── deployment.yaml
│   ├── service.yaml
│   └── hpa.yaml
└── settlement-service/
    ├── deployment.yaml
    ├── service.yaml
    └── cronjob.yaml
```

### 6.2 HPA 설정

```yaml
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: payment-service-hpa
spec:
  scaleTargetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: payment-service
  minReplicas: 2
  maxReplicas: 5
  metrics:
  - type: Resource
    resource:
      name: cpu
      target:
        type: Utilization
        averageUtilization: 70
```

### 6.3 정산 배치 CronJob

```yaml
apiVersion: batch/v1
kind: CronJob
metadata:
  name: settlement-batch
spec:
  schedule: "0 1 * * *"  # 매일 01:00
  jobTemplate:
    spec:
      template:
        spec:
          containers:
          - name: settlement
            image: settlement-service:latest
            command: ["java", "-jar", "app.jar", "--spring.batch.job.names=settlementJob"]
          restartPolicy: OnFailure
```

---

## 7. 디렉토리 구조

```
payment-system-simulator/
├── payment-service/
│   └── src/main/java/com/example/payment/
│       ├── controller/
│       ├── service/
│       ├── domain/
│       ├── repository/
│       ├── event/
│       ├── infrastructure/
│       │   ├── redis/
│       │   ├── kafka/
│       │   └── pg/        # Mock PG
│       └── config/
├── settlement-service/
│   └── src/main/java/com/example/settlement/
│       ├── batch/
│       ├── domain/
│       ├── repository/
│       └── config/
├── k8s/
├── docker/
├── docs/
│   ├── REQUIREMENTS.md
│   └── DESIGN.md
├── docker-compose.yml
└── claude.md
```
