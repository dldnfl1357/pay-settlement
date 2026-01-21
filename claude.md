# Payment System Simulator - Claude Guide

## 프로젝트 개요

LINE Pay Backend Engineer 지원을 위한 결제/정산 시스템 학습 프로젝트.
핵심 도메인(결제, 정산, 원장)만 구현하여 금융 트랜잭션 역량을 확보한다.

## 학습 목표

| 영역 | 핵심 개념 |
|------|----------|
| 트랜잭션 | ACID, Saga 보상 트랜잭션 |
| 이중 지불 방지 | 멱등성 키, 분산 락 |
| 정산 | D+2 정산, 수수료 계산, Spring Batch |
| 보안 | PCI-DSS 기초 (토큰화, 마스킹, 감사 로그) |

## 기술 스택

```
서버:       payment-service, settlement-service (Spring Boot 3.x)
메시지큐:   Kafka
캐시/락:    Redis (Redisson)
DB:         MySQL 8.x
컨테이너:   Docker + Kubernetes (minikube)
```

## 프로젝트 구조

```
payment-system-simulator/
├── payment-service/       # 결제 처리, 원장 기록
├── settlement-service/    # 정산 배치
├── k8s/                   # Kubernetes 매니페스트
├── docker/                # Dockerfile
└── docs/                  # 문서
```

---

## 코딩 컨벤션

### Java

- Google Java Style Guide
- 클래스: PascalCase
- 메서드/변수: camelCase
- 상수: UPPER_SNAKE_CASE

### 패키지 구조

```
com.example.payment/
├── controller/     # REST API
├── service/        # 비즈니스 로직
├── domain/         # 엔티티, VO
├── repository/     # 데이터 접근
├── event/          # Kafka 이벤트
├── infrastructure/ # 외부 연동 (Redis, PG)
└── config/         # 설정
```

### 네이밍 규칙

```java
// Service
PaymentService, PaymentServiceImpl

// Repository
PaymentRepository

// Controller
PaymentController

// Request/Response
PaymentRequest, PaymentResponse

// Event
PaymentApprovedEvent, PaymentCancelledEvent
```

---

## 핵심 도메인 규칙

### Payment (결제)

**상태 전이**
```
PENDING → APPROVED (승인)
PENDING → FAILED (실패)
APPROVED → CANCELLED (취소)
```

**불변 규칙**
- 멱등성 키는 유니크해야 함
- 승인된 결제만 취소 가능
- 금액은 0보다 커야 함

### Wallet (월렛)

**불변 규칙**
- 잔액은 0 미만이 될 수 없음
- 차감 시 낙관적 잠금 + 분산 락 사용

### LedgerEntry (원장)

**불변 규칙**
- Append-only (수정/삭제 불가)
- 복식부기: DEBIT + CREDIT 쌍
- DEBIT 합계 = CREDIT 합계

### Settlement (정산)

**상태 전이**
```
PENDING → CONFIRMED → PAID
```

**수수료 계산**
```
수수료 = 결제금액 × 수수료율
순정산액 = 결제금액 - 취소금액 - 수수료
```

---

## 이중 지불 방지 패턴

### 1. 멱등성 키 (필수)

```java
// 요청 시 헤더에 포함
Idempotency-Key: {UUID}

// Redis 저장
Key: idempotency:{key}
Value: {response JSON}
TTL: 24시간
```

### 2. 분산 락 (월렛 차감 시)

```java
// Redisson 사용
Lock Key: lock:wallet:{walletId}
Wait: 5초
Lease: 10초
```

### 3. 낙관적 잠금 (DB)

```java
@Version
private Long version;
```

---

## Saga 보상 트랜잭션

### 정상 플로우

```
1. 잔액 차감 ✓
2. PG 승인 ✓
3. 결제 완료 ✓
4. 원장 기록 ✓
5. 이벤트 발행 ✓
```

### 실패 시 보상

```
1. 잔액 차감 ✓
2. PG 승인 ✗ (실패)
-- 보상 시작 --
3. 잔액 복구 ✓
4. 결제 FAILED ✓
5. 보상 원장 기록 ✓
6. 실패 이벤트 발행 ✓
```

---

## Kafka 토픽

| 토픽 | 설명 |
|------|------|
| payment.approved | 결제 승인 완료 |
| payment.cancelled | 결제 취소 |
| payment.failed | 결제 실패 |

### 이벤트 포맷

```json
{
  "eventId": "uuid",
  "eventType": "PAYMENT_APPROVED",
  "timestamp": "2026-01-21T10:00:00Z",
  "payload": {
    "paymentId": 1,
    "merchantId": 1,
    "amount": 50000,
    "status": "APPROVED"
  }
}
```

---

## PCI-DSS 기초

### 1. 토큰화

```java
// 카드번호 원본 저장 금지
// tok_xxxx 형태의 토큰만 저장
String token = tokenizationService.tokenize(cardNumber);
```

### 2. 마스킹

```java
// 로그 출력 시
"카드번호: ************1234"
"금액: 1~10만원"
```

### 3. 감사 로그

```java
@Audited(action = "PAYMENT_APPROVE")
public PaymentResponse approve(Long paymentId) { ... }

// 로그 출력
AUDIT: {action=PAYMENT_APPROVE, status=SUCCESS, timestamp=...}
```

---

## Mock PG 규칙

| 카드번호 끝 4자리 | 결과 |
|------------------|------|
| 1111 | 승인 성공 |
| 2222 | 잔액 부족 |
| 3333 | 카드 정지 |
| 9999 | 타임아웃 (3초) |

---

## 자주 사용하는 명령어

### 로컬 개발

```bash
# 인프라 실행
docker-compose up -d

# 서비스 빌드
./gradlew build

# 테스트
./gradlew test

# 서비스 실행
./gradlew :payment-service:bootRun
./gradlew :settlement-service:bootRun
```

### Kubernetes

```bash
# minikube 시작
minikube start --cpus=4 --memory=8192

# 전체 배포
kubectl apply -f k8s/

# 로그 확인
kubectl logs -f deployment/payment-service

# 정산 배치 수동 실행
kubectl create job settlement-manual --from=cronjob/settlement-batch
```

### 테스트 API

```bash
# 결제 요청
curl -X POST http://localhost:8080/api/payments \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: $(uuidgen)" \
  -d '{
    "walletId": 1,
    "merchantId": 1,
    "orderId": "ORDER-001",
    "amount": 50000,
    "cardToken": "tok_test_1111"
  }'

# 결제 취소
curl -X POST http://localhost:8080/api/payments/1/cancel

# 정산 조회
curl http://localhost:8081/api/settlements?merchantId=1
```

---

## 작업 요청 시 참고

### 코드 작성

- 어떤 서비스인지 명시 (payment / settlement)
- 기존 코드와의 관계 설명
- 테스트 코드 필요 여부

### 설계 논의

- REQUIREMENTS.md, DESIGN.md 참조
- 트레이드오프 고려 시 옵션 제시 요청

### 트러블슈팅

- 에러 메시지 전문 포함
- 재현 단계 설명

---

## 핵심 파일 위치

### payment-service

| 파일 | 역할 |
|------|------|
| `PaymentController.java` | 결제 API |
| `PaymentSagaService.java` | Saga 패턴 구현 |
| `IdempotencyService.java` | 멱등성 처리 |
| `DistributedLockService.java` | 분산 락 |
| `LedgerService.java` | 원장 기록 |
| `MockPgClient.java` | PG Mock |

### settlement-service

| 파일 | 역할 |
|------|------|
| `SettlementBatchConfig.java` | 배치 Job 설정 |
| `SettlementProcessor.java` | 수수료 계산 |
| `PaymentEventConsumer.java` | Kafka 컨슈머 |

---

## 테스트 시나리오

### 1. 정상 결제

```
입력: walletId=1, amount=50000, cardToken=tok_test_1111
기대: status=APPROVED, 잔액 차감, 원장 기록
```

### 2. 이중 지불 방지

```
1차 요청: Idempotency-Key=abc → 결제 성공
2차 요청: Idempotency-Key=abc → 기존 결과 반환 (중복 차감 없음)
```

### 3. Saga 보상

```
입력: cardToken=tok_test_2222 (잔액 부족 유도)
기대: PG 실패 → 잔액 복구 → status=FAILED
```

### 4. 정산 배치

```
전일 결제: 3건 (총 150,000원), 취소: 1건 (50,000원)
수수료율: 2.5%
기대 순정산액: 150,000 - 50,000 - 2,500 = 97,500원
```
