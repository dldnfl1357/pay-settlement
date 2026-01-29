# Payment System Monitoring

Prometheus + Grafana 기반 모니터링 설정

---

## 구성 요소

```
monitoring/
├── prometheus.yml                      # Prometheus 설정
├── grafana/
│   ├── provisioning/
│   │   ├── datasources/
│   │   │   └── datasource.yml          # Prometheus 데이터소스
│   │   └── dashboards/
│   │       └── dashboard.yml           # 대시보드 프로비저닝
│   └── dashboards/
│       └── payment-dashboard.json      # 결제 시스템 대시보드
└── README.md
```

---

## 실행 방법

```bash
# 전체 인프라 실행 (Prometheus, Grafana 포함)
docker-compose up -d

# 접속
# Prometheus: http://localhost:9090
# Grafana: http://localhost:3000 (admin/admin)
```

---

## 수집 지표

### 비즈니스 지표

| 지표 | PromQL | 설명 |
|------|--------|------|
| 결제 생성 수 | `payment_created_total` | 총 생성된 결제 수 |
| 결제 승인 수 | `payment_approved_total` | 총 승인된 결제 수 |
| 결제 실패 수 | `payment_failed_total` | 총 실패한 결제 수 |
| 결제 취소 수 | `payment_cancelled_total` | 총 취소된 결제 수 |
| 결제 금액 | `payment_amount_total` | 상태별 총 결제 금액 |

### 성능 지표

| 지표 | PromQL | 설명 |
|------|--------|------|
| 결제 승인 시간 (P95) | `histogram_quantile(0.95, payment_approval_duration_seconds_bucket)` | 95% 승인 처리 시간 |
| PG 호출 시간 (P95) | `histogram_quantile(0.95, payment_pg_call_duration_seconds_bucket)` | 95% PG API 호출 시간 |
| 락 획득 시간 (P95) | `histogram_quantile(0.95, payment_lock_acquisition_duration_seconds_bucket)` | 95% 분산 락 대기 시간 |

### 안정성 지표

| 지표 | PromQL | 설명 |
|------|--------|------|
| 성공률 | `payment_approved_total / (payment_approved_total + payment_failed_total) * 100` | 결제 성공률 |
| 멱등성 히트 | `payment_idempotency_hit_total` | 중복 요청 차단 수 |
| Saga 보상 | `payment_saga_compensation_total` | 보상 트랜잭션 발생 수 |
| 락 실패 | `payment_lock_failure_total` | 분산 락 획득 실패 수 |

### 인프라 지표 (Spring Boot Actuator 기본 제공)

| 지표 | PromQL | 설명 |
|------|--------|------|
| DB 커넥션 | `hikaricp_connections_active` | 활성 DB 커넥션 수 |
| JVM 힙 메모리 | `jvm_memory_used_bytes{area="heap"}` | 사용 중인 힙 메모리 |
| HTTP 요청 시간 | `http_server_requests_seconds` | API 응답 시간 |

---

## Grafana 대시보드

### 패널 구성

```
┌────────────────┬────────────────┬────────────────┬────────────────┐
│ Created (1m)   │ Approved (1m)  │ Failed (1m)    │ Success Rate   │
├────────────────┴────────────────┼────────────────┴────────────────┤
│ Payment TPS                     │ Approval Latency (P50/P95/P99)  │
├─────────────────────────────────┼─────────────────────────────────┤
│ External Call Latency           │ System Events                   │
│ (PG, Lock)                      │ (Saga, Idempotency, Lock Fail)  │
├─────────────────────────────────┼─────────────────────────────────┤
│ HikariCP Connections            │ JVM Heap Memory                 │
└─────────────────────────────────┴─────────────────────────────────┘
```

---

## 알람 설정 (예시)

Grafana Alert 또는 Prometheus Alertmanager에서 설정:

```yaml
# 결제 성공률 99% 미만
- alert: PaymentSuccessRateLow
  expr: |
    100 * sum(rate(payment_approved_total[5m]))
    / (sum(rate(payment_approved_total[5m])) + sum(rate(payment_failed_total[5m]))) < 99
  for: 5m
  labels:
    severity: critical
  annotations:
    summary: "결제 성공률 저하"

# 응답 시간 500ms 초과
- alert: PaymentLatencyHigh
  expr: |
    histogram_quantile(0.95, sum(rate(payment_approval_duration_seconds_bucket[5m])) by (le)) > 0.5
  for: 5m
  labels:
    severity: warning
  annotations:
    summary: "결제 응답 시간 증가"

# Saga 보상 급증
- alert: SagaCompensationSpike
  expr: sum(increase(payment_saga_compensation_total[5m])) > 10
  for: 1m
  labels:
    severity: warning
  annotations:
    summary: "Saga 보상 트랜잭션 급증"
```

---

## 로컬 개발 시 설정

애플리케이션을 Docker 외부에서 실행할 경우 `prometheus.yml` 수정:

```yaml
scrape_configs:
  - job_name: 'payment-service'
    metrics_path: '/actuator/prometheus'
    static_configs:
      - targets: ['host.docker.internal:8080']  # Docker에서 호스트 접근
```

---

## Endpoints

| 서비스 | URL | 설명 |
|--------|-----|------|
| Payment Metrics | http://localhost:8080/actuator/prometheus | 원시 메트릭 |
| Prometheus | http://localhost:9090 | 메트릭 쿼리 |
| Grafana | http://localhost:3000 | 대시보드 (admin/admin) |
