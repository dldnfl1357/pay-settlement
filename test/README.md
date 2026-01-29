# Payment System Load Test

결제 시스템 부하 테스트 및 데이터 정합성 검증

---

## 디렉토리 구조

```
test/
├── jmeter/
│   └── payment-load-test.jmx    # JMeter 테스트 계획
├── sql/
│   ├── setup-test-data.sql      # 테스트 데이터 초기화
│   └── validation-queries.sql   # 정합성 검증 쿼리
├── data/
│   └── duplicate-keys.csv       # 이중 지불 테스트용 고정 키
├── results/                     # 테스트 결과 (자동 생성)
└── README.md
```

---

## 테스트 시나리오

| # | 시나리오 | 목적 | Thread | Duration |
|---|----------|------|--------|----------|
| 1 | Basic Load Test | 기본 부하 및 TPS 측정 | 500 | 5분 |
| 2 | Double Payment Test | 이중 지불 방지 검증 | 100 | - |
| 3 | Concurrency Race Test | 동일 월렛 동시성 검증 | 100 | - |
| 4 | Saga Compensation Test | 보상 트랜잭션 검증 | 200 | - |

---

## 실행 가이드

### 1. 사전 준비

```bash
# 1.1 인프라 실행
docker-compose up -d

# 1.2 애플리케이션 빌드 및 실행
./gradlew :service-payment:bootRun

# 1.3 결과 디렉토리 생성
mkdir -p test/results
```

### 2. 테스트 데이터 초기화

```bash
# MySQL 접속 후 실행
mysql -u root -p payment_db < test/sql/setup-test-data.sql
```

또는 MySQL 클라이언트에서:
```sql
source /path/to/test/sql/setup-test-data.sql
```

### 3. JMeter 실행

#### GUI 모드 (시나리오 확인/수정)
```bash
jmeter -t test/jmeter/payment-load-test.jmx
```

#### CLI 모드 (실제 테스트)
```bash
# 시나리오 1: 기본 부하 테스트
jmeter -n -t test/jmeter/payment-load-test.jmx \
  -l test/results/result.jtl \
  -e -o test/results/report

# 특정 Thread Group만 실행하려면 JMeter GUI에서 다른 그룹 disable
```

#### 주요 옵션
- `-n`: Non-GUI 모드
- `-t`: 테스트 계획 파일
- `-l`: 결과 로그 파일
- `-e -o`: HTML 리포트 생성
- `-JBASE_URL=xxx`: 변수 오버라이드

### 4. 시나리오별 실행

JMeter GUI에서 실행할 시나리오만 enable하고 나머지는 disable:

```
시나리오 1 (Basic Load): 기본 활성화
시나리오 2 (Double Payment): 기본 비활성화 → 이중 지불 테스트 시 활성화
시나리오 3 (Concurrency Race): 기본 비활성화 → 동시성 테스트 시 활성화
시나리오 4 (Saga Compensation): 기본 비활성화 → 보상 테스트 시 활성화
```

### 5. 데이터 정합성 검증

테스트 종료 후 MySQL에서 검증 쿼리 실행:

```bash
mysql -u root -p payment_db < test/sql/validation-queries.sql
```

또는 섹션별 실행:
```sql
-- 종합 리포트만 확인
source /path/to/test/sql/validation-queries.sql
-- 마지막 섹션 "9. 종합 검증 리포트" 결과 확인
```

---

## 검증 항목

| 검증 | 설명 | 기대 결과 |
|------|------|----------|
| WALLET_BALANCE | 월렛 잔액 = 초기 - DEBIT + CREDIT | PASS |
| DOUBLE_ENTRY | SUM(DEBIT) = SUM(CREDIT) | PASS |
| IDEMPOTENCY | 동일 멱등성 키로 중복 결제 없음 | PASS |
| PAYMENT_LEDGER_MATCH | 승인 결제 수 = 원장 기록 수 | PASS |

---

## 예상 이슈 및 디버깅

### 1. 이중 차감 발생 시
```sql
-- 불일치 월렛 확인
SELECT * FROM wallets w
JOIN test_initial_balance tib ON w.id = tib.wallet_id
WHERE w.balance != tib.initial_balance - (SELECT ...);

-- 해당 월렛의 결제 내역 확인
SELECT * FROM payments WHERE wallet_id = {wallet_id} ORDER BY created_at;

-- 해당 월렛의 원장 내역 확인
SELECT * FROM ledger_entries WHERE account_id = {wallet_id} ORDER BY created_at;
```

### 2. 멱등성 실패 시
```sql
-- 중복 멱등성 키 확인
SELECT idempotency_key, COUNT(*), GROUP_CONCAT(id)
FROM payments
GROUP BY idempotency_key
HAVING COUNT(*) > 1;
```

### 3. Saga 보상 실패 시
```sql
-- FAILED인데 보상 기록 없는 결제 확인
SELECT p.* FROM payments p
LEFT JOIN ledger_entries le ON p.id = le.payment_id AND le.description LIKE '%보상%'
WHERE p.status = 'FAILED' AND le.id IS NULL;
```

---

## 성능 튜닝 포인트

### JMeter
```
# Heap 메모리 증가 (jmeter.bat 또는 jmeter.sh)
HEAP="-Xms2g -Xmx4g"
```

### Application
```yaml
# application.yml
spring:
  datasource:
    hikari:
      maximum-pool-size: 50
      minimum-idle: 10
```

### MySQL
```sql
-- Connection 확인
SHOW VARIABLES LIKE 'max_connections';
SHOW STATUS LIKE 'Threads_connected';
```

### Redis
```bash
# 연결 수 확인
redis-cli INFO clients
```

---

## 결과 해석

### JMeter Summary Report

| 항목 | 의미 | 목표 |
|------|------|------|
| Average | 평균 응답 시간 | < 300ms |
| 90% Line | P90 응답 시간 | < 500ms |
| Error % | 에러율 | < 1% |
| Throughput | 초당 처리량 | > 1000/sec |

### 검증 쿼리 결과

```
+--------------------+--------+
| check_type         | result |
+--------------------+--------+
| WALLET_BALANCE     | PASS   |  ← 모두 PASS여야 정상
| DOUBLE_ENTRY       | PASS   |
| IDEMPOTENCY        | PASS   |
| PAYMENT_LEDGER_MATCH | PASS |
+--------------------+--------+
```

---

## 테스트 체크리스트

- [ ] 인프라 실행 확인 (MySQL, Redis, Kafka)
- [ ] 애플리케이션 실행 확인
- [ ] 테스트 데이터 초기화
- [ ] 초기 잔액 스냅샷 저장
- [ ] JMeter 테스트 실행
- [ ] 검증 쿼리 실행
- [ ] 결과 리포트 확인
- [ ] 이슈 발견 시 로그 분석
