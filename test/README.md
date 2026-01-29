# Payment System Load Test Guide

결제 시스템 부하 테스트 및 데이터 정합성 검증 가이드

---

## 목차

1. [개요](#1-개요)
2. [사전 준비](#2-사전-준비)
3. [테스트 시나리오](#3-테스트-시나리오)
4. [실행 방법](#4-실행-방법)
5. [정합성 검증](#5-정합성-검증)
6. [결과 분석](#6-결과-분석)
7. [트러블슈팅](#7-트러블슈팅)

---

## 1. 개요

### 1.1 테스트 목적

| 목적 | 설명 |
|------|------|
| 성능 측정 | 최대 TPS, 응답 시간 (P50/P95/P99) |
| 이중 지불 방지 검증 | 멱등성 키, 분산 락 동작 확인 |
| 동시성 검증 | 동일 월렛 동시 결제 시 잔액 정합성 |
| Saga 보상 검증 | PG 실패 시 잔액 복구 확인 |
| 복식부기 검증 | DEBIT/CREDIT 합계 일치 |

### 1.2 디렉토리 구조

```
test/
├── jmeter/
│   └── payment-load-test.jmx    # JMeter 테스트 계획 (4개 시나리오)
├── sql/
│   ├── setup-test-data.sql      # 테스트 데이터 초기화
│   └── validation-queries.sql   # 정합성 검증 쿼리 (10개 섹션)
├── data/
│   └── duplicate-keys.csv       # 이중 지불 테스트용 고정 키
├── results/                     # 테스트 결과 저장
└── README.md
```

---

## 2. 사전 준비

### 2.1 필수 소프트웨어

| 소프트웨어 | 버전 | 용도 |
|-----------|------|------|
| Docker | 20.x+ | 인프라 실행 |
| JDK | 17+ | 애플리케이션 실행 |
| JMeter | 5.6+ | 부하 테스트 |
| MySQL Client | 8.x | 검증 쿼리 실행 |

### 2.2 JMeter 설치

```bash
# macOS
brew install jmeter

# Ubuntu
sudo apt-get install jmeter

# Windows
# https://jmeter.apache.org/download_jmeter.cgi 에서 다운로드
```

### 2.3 인프라 실행

```bash
# 프로젝트 루트에서 실행
docker-compose up -d

# 서비스 상태 확인
docker-compose ps

# 예상 결과:
# payment-mysql      running   0.0.0.0:3306->3306/tcp
# payment-redis      running   0.0.0.0:6379->6379/tcp
# payment-kafka      running   0.0.0.0:9092->9092/tcp
# payment-zookeeper  running   0.0.0.0:2181->2181/tcp
```

### 2.4 애플리케이션 실행

```bash
# 결제 서비스 실행
./gradlew :service-payment:bootRun

# 정상 실행 확인
curl http://localhost:8080/actuator/health
# {"status":"UP"}
```

### 2.5 테스트 데이터 초기화

```bash
# MySQL 접속
mysql -h localhost -u root -proot payment_db

# 또는 Docker 내부에서
docker exec -it payment-mysql mysql -u root -proot payment_db
```

```sql
-- 테스트 데이터 초기화 스크립트 실행
source /path/to/test/sql/setup-test-data.sql;

-- 또는 직접 명령어로
-- 1. 기존 데이터 삭제
SET FOREIGN_KEY_CHECKS = 0;
TRUNCATE TABLE ledger_entries;
TRUNCATE TABLE payments;
TRUNCATE TABLE wallets;
TRUNCATE TABLE merchants;
SET FOREIGN_KEY_CHECKS = 1;

-- 2. 가맹점 생성
INSERT INTO merchants (id, name, fee_rate, created_at) VALUES
(1, '테스트가맹점A', 0.025, NOW()),
(2, '테스트가맹점B', 0.020, NOW());

-- 3. 월렛 100개 생성 (초기 잔액 100만원)
-- setup-test-data.sql의 프로시저 참고
```

**초기화 후 확인:**
```sql
SELECT COUNT(*) FROM wallets;      -- 100
SELECT COUNT(*) FROM merchants;    -- 2
SELECT SUM(balance) FROM wallets;  -- 100,000,000 (100 × 100만원)
```

---

## 3. 테스트 시나리오

### 3.1 시나리오 1: 기본 부하 테스트

| 항목 | 설정 |
|------|------|
| 목적 | 정상 상황에서 최대 TPS 및 응답 시간 측정 |
| Thread | 500 |
| Ramp-up | 60초 |
| Duration | 300초 (5분) |
| 요청 | 결제 생성 → 결제 승인 (순차) |
| 데이터 | walletId 1~100 랜덤, 멱등성 키 UUID |

**기대 결과:**
- TPS: 500+ (인프라에 따라 다름)
- 평균 응답 시간: < 300ms
- 에러율: < 1%

### 3.2 시나리오 2: 이중 지불 테스트

| 항목 | 설정 |
|------|------|
| 목적 | 동일 멱등성 키로 동시 요청 시 중복 결제 방지 확인 |
| Thread | 100 |
| 동시 요청 | 동일 멱등성 키로 10개씩 동시 전송 |
| 데이터 | duplicate-keys.csv (고정 키 10개) |

**기대 결과:**
- 동일 멱등성 키로 1건만 결제 생성
- 나머지는 캐시된 결과 반환 또는 DuplicatePaymentException

**검증 쿼리:**
```sql
-- 동일 멱등성 키로 여러 결제가 생성되면 FAIL
SELECT idempotency_key, COUNT(*)
FROM payments
GROUP BY idempotency_key
HAVING COUNT(*) > 1;
-- 결과가 없어야 정상
```

### 3.3 시나리오 3: 동시성 경쟁 테스트

| 항목 | 설정 |
|------|------|
| 목적 | 동일 월렛에 동시 결제 시 잔액 정합성 확인 |
| Thread | 100 |
| 동시 요청 | walletId=1로 50개 동시 결제 (각 1,000원) |
| Synchronizing Timer | 50개씩 동시 전송 |

**기대 결과:**
- 분산 락으로 순차 처리
- 잔액 부족 시 일부 실패
- 최종 잔액 = 초기 잔액 - (성공 건수 × 1,000원)

**검증 쿼리:**
```sql
-- 월렛 1번의 잔액 검증
SELECT
    w.balance AS current_balance,
    1000000 - (SELECT COUNT(*) * 1000 FROM payments
               WHERE wallet_id = 1 AND status = 'APPROVED') AS expected_balance
FROM wallets w WHERE w.id = 1;
-- current_balance = expected_balance 여야 정상
```

### 3.4 시나리오 4: Saga 보상 테스트

| 항목 | 설정 |
|------|------|
| 목적 | PG 실패 시 Saga 보상 트랜잭션 동작 확인 |
| Thread | 200 |
| Loops | 50 |
| 요청 | cardToken = `tok_test_2222` (PG 잔액 부족 유도) |

**기대 결과:**
- 결제 상태: FAILED
- 잔액 복구됨
- 보상 원장 기록 존재

**검증 쿼리:**
```sql
-- FAILED 결제의 보상 원장 확인
SELECT p.id, p.status, le.description
FROM payments p
LEFT JOIN ledger_entries le ON p.id = le.payment_id
  AND le.description LIKE '%보상%'
WHERE p.status = 'FAILED';
-- 모든 FAILED 결제에 보상 원장이 있어야 정상
```

---

## 4. 실행 방법

### 4.1 JMeter GUI 모드 (시나리오 확인/수정)

```bash
# JMeter GUI 실행
jmeter -t test/jmeter/payment-load-test.jmx
```

**시나리오 활성화/비활성화:**
1. 좌측 트리에서 Thread Group 선택
2. 우클릭 → Enable/Disable
3. 실행할 시나리오만 Enable

### 4.2 JMeter CLI 모드 (실제 테스트)

```bash
# 결과 디렉토리 생성
mkdir -p test/results

# 기본 부하 테스트 실행
jmeter -n -t test/jmeter/payment-load-test.jmx \
  -l test/results/result.jtl \
  -e -o test/results/report

# 옵션 설명:
# -n: Non-GUI 모드
# -t: 테스트 계획 파일
# -l: 결과 로그 파일
# -e -o: HTML 리포트 생성
```

### 4.3 변수 오버라이드

```bash
# 서버 주소 변경
jmeter -n -t test/jmeter/payment-load-test.jmx \
  -JBASE_URL=192.168.1.100 \
  -JPORT=8080 \
  -l test/results/result.jtl

# Thread 수 변경 (JMeter GUI에서 변수화 필요)
jmeter -n -t test/jmeter/payment-load-test.jmx \
  -JTHREADS=1000 \
  -l test/results/result.jtl
```

### 4.4 시나리오별 실행 순서

```bash
# 1. 테스트 데이터 초기화
mysql -h localhost -u root -proot payment_db < test/sql/setup-test-data.sql

# 2. 기본 부하 테스트 (시나리오 1만 Enable)
jmeter -n -t test/jmeter/payment-load-test.jmx -l test/results/basic.jtl

# 3. 정합성 검증
mysql -h localhost -u root -proot payment_db < test/sql/validation-queries.sql

# 4. 데이터 재초기화 후 다음 시나리오 실행
```

---

## 5. 정합성 검증

### 5.1 검증 쿼리 실행

```bash
# 전체 검증 쿼리 실행
mysql -h localhost -u root -proot payment_db < test/sql/validation-queries.sql
```

### 5.2 검증 항목

| 검증 | 쿼리 섹션 | 설명 | 기대 결과 |
|------|----------|------|----------|
| 월렛 잔액 | 섹션 2 | 초기잔액 - DEBIT + CREDIT = 현재잔액 | 모든 월렛 OK |
| 복식부기 | 섹션 3 | SUM(DEBIT) = SUM(CREDIT) | OK |
| 멱등성 | 섹션 4 | 동일 키로 중복 결제 없음 | 결과 없음 |
| 원장 일치 | 섹션 6 | 승인 결제 수 = 원장 기록 수 | OK |
| Saga 보상 | 섹션 7 | FAILED 결제에 보상 기록 존재 | 모두 COMPENSATED |

### 5.3 종합 검증 리포트

```sql
-- 섹션 9: 종합 검증 리포트
-- 모든 항목이 PASS여야 정상

+----------------------+--------+
| check_type           | result |
+----------------------+--------+
| WALLET_BALANCE       | PASS   |
| DOUBLE_ENTRY         | PASS   |
| IDEMPOTENCY          | PASS   |
| PAYMENT_LEDGER_MATCH | PASS   |
+----------------------+--------+
```

### 5.4 개별 검증 쿼리

**월렛 잔액 불일치 확인:**
```sql
SELECT
    w.id AS wallet_id,
    w.balance AS current_balance,
    tib.initial_balance - COALESCE(d.total, 0) + COALESCE(c.total, 0) AS expected_balance,
    w.balance - (tib.initial_balance - COALESCE(d.total, 0) + COALESCE(c.total, 0)) AS difference
FROM wallets w
JOIN test_initial_balance tib ON w.id = tib.wallet_id
LEFT JOIN (
    SELECT account_id, SUM(amount) AS total FROM ledger_entries
    WHERE account_type = 'WALLET' AND entry_type = 'DEBIT' GROUP BY account_id
) d ON w.id = d.account_id
LEFT JOIN (
    SELECT account_id, SUM(amount) AS total FROM ledger_entries
    WHERE account_type = 'WALLET' AND entry_type = 'CREDIT' GROUP BY account_id
) c ON w.id = c.account_id
WHERE w.balance != tib.initial_balance - COALESCE(d.total, 0) + COALESCE(c.total, 0);
-- 결과가 없어야 정상
```

---

## 6. 결과 분석

### 6.1 JMeter 리포트 확인

```bash
# HTML 리포트 열기
open test/results/report/index.html  # macOS
xdg-open test/results/report/index.html  # Linux
start test/results/report/index.html  # Windows
```

### 6.2 주요 지표

| 지표 | 의미 | 목표 | 비고 |
|------|------|------|------|
| Average | 평균 응답 시간 | < 300ms | |
| 90% Line (P90) | 90% 요청 응답 시간 | < 500ms | |
| 99% Line (P99) | 99% 요청 응답 시간 | < 1000ms | |
| Error % | 에러율 | < 1% | 잔액 부족 제외 |
| Throughput | 초당 처리량 | > 500/sec | 인프라에 따라 다름 |

### 6.3 결과 해석

**정상 결과 예시:**
```
Label                              # Samples  Average  90% Line  99% Line  Error %  Throughput
POST /api/payments (Create)        50000      45       78        156       0.00%    834.2/sec
POST /api/payments/{id}/approve    50000      123      245       489       0.12%    834.0/sec
```

**문제 징후:**
- Error % > 1%: 병목 또는 버그 가능성
- Average > 500ms: 성능 이슈
- Throughput 급감: 리소스 고갈

---

## 7. 트러블슈팅

### 7.1 Connection Refused

```
java.net.ConnectException: Connection refused
```

**원인:** 애플리케이션 또는 인프라 미실행

**해결:**
```bash
# 인프라 상태 확인
docker-compose ps

# 애플리케이션 상태 확인
curl http://localhost:8080/actuator/health
```

### 7.2 Too Many Connections

```
com.mysql.cj.jdbc.exceptions.CommunicationsException: Too many connections
```

**원인:** DB 커넥션 풀 고갈

**해결:**
```yaml
# application.yml
spring:
  datasource:
    hikari:
      maximum-pool-size: 50  # 기본 10에서 증가
      minimum-idle: 10
```

### 7.3 Lock Acquisition Failed

```
LockAcquisitionException: Failed to acquire lock
```

**원인:** 분산 락 대기 시간 초과

**해결:**
```yaml
# application.yml
payment:
  lock:
    wait-seconds: 10  # 기본 5에서 증가
    lease-seconds: 15
```

### 7.4 JMeter Out of Memory

```
java.lang.OutOfMemoryError: Java heap space
```

**해결:**
```bash
# jmeter.bat 또는 jmeter.sh 수정
HEAP="-Xms2g -Xmx4g"
```

### 7.5 이중 차감 발생

**증상:** 월렛 잔액 불일치

**디버깅:**
```sql
-- 1. 불일치 월렛 확인
SELECT * FROM wallets WHERE id IN (
    SELECT w.id FROM wallets w
    JOIN test_initial_balance tib ON w.id = tib.wallet_id
    -- ... (위 검증 쿼리 참고)
);

-- 2. 해당 월렛의 결제 내역
SELECT * FROM payments WHERE wallet_id = {불일치_월렛_id} ORDER BY created_at;

-- 3. 해당 월렛의 원장 내역
SELECT * FROM ledger_entries WHERE account_id = {불일치_월렛_id} ORDER BY created_at;

-- 4. 중복 멱등성 키 확인
SELECT idempotency_key, COUNT(*), GROUP_CONCAT(id)
FROM payments WHERE wallet_id = {불일치_월렛_id}
GROUP BY idempotency_key HAVING COUNT(*) > 1;
```

### 7.6 Saga 보상 미실행

**증상:** FAILED 결제인데 잔액 복구 안됨

**디버깅:**
```sql
-- 보상 기록 없는 FAILED 결제 확인
SELECT p.id, p.wallet_id, p.amount, p.status
FROM payments p
LEFT JOIN ledger_entries le ON p.id = le.payment_id AND le.description LIKE '%보상%'
WHERE p.status = 'FAILED' AND le.id IS NULL;
```

**원인 추정:**
- compensate() 메서드 예외 발생
- 트랜잭션 롤백 시점 문제

---

## 부록: 테스트 체크리스트

### 테스트 전

- [ ] Docker 서비스 실행 확인 (`docker-compose ps`)
- [ ] 애플리케이션 헬스체크 (`/actuator/health`)
- [ ] 테스트 데이터 초기화 (`setup-test-data.sql`)
- [ ] 초기 잔액 스냅샷 저장 확인 (`test_initial_balance` 테이블)
- [ ] JMeter 시나리오 활성화 상태 확인
- [ ] 결과 디렉토리 생성 (`test/results`)

### 테스트 중

- [ ] JMeter Summary Report 에러율 모니터링
- [ ] 애플리케이션 로그 확인 (`docker logs -f payment-service`)
- [ ] 리소스 모니터링 (Grafana 또는 `docker stats`)

### 테스트 후

- [ ] 종합 검증 리포트 확인 (모두 PASS)
- [ ] JMeter HTML 리포트 확인
- [ ] 불일치 발생 시 원인 분석
- [ ] 결과 기록 및 문서화
