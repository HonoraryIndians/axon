# Kafka Topics Management Guide

## 📋 토픽 관리 방법

Axon CDP 프로젝트에서 Kafka 토픽은 3가지 방법으로 관리할 수 있습니다:

### 방법 1: Helm Init Job (자동 생성) ⭐ 권장

Kafka 설치 시 자동으로 토픽 생성됩니다.

**위치**: `templates/topic-init-job.yaml`

**동작**:
```bash
helm install axon-kafka . -f values.yaml
# → Kafka 설치 완료
# → Post-install hook 실행
# → 토픽 자동 생성 ✅
```

**토픽 목록 확인**:
```bash
kubectl logs -l job-name=axon-kafka-topic-init
```

### 방법 2: 수동 토픽 생성

Kafka 설치 후 수동으로 토픽 생성:

```bash
# Kafka pod에 접속
kubectl exec -it axon-kafka-controller-0 -- bash

# 토픽 생성
kafka-topics.sh --bootstrap-server localhost:9092 \
  --create \
  --topic axon.event.raw \
  --partitions 3 \
  --replication-factor 1 \
  --config retention.ms=604800000

# 토픽 목록 확인
kafka-topics.sh --bootstrap-server localhost:9092 --list

# 토픽 상세 정보
kafka-topics.sh --bootstrap-server localhost:9092 \
  --describe \
  --topic axon.event.raw
```

### 방법 3: Application에서 Auto-create

Spring Kafka에서 자동 생성 (개발 환경용):

```yaml
# application.yml
spring:
  kafka:
    producer:
      properties:
        auto.create.topics.enable: true
```

**주의**: 프로덕션에서는 비권장 (파티션 수, replication factor 제어 불가)

## 📊 Axon CDP 토픽 목록

| Topic | Partitions | Replication | Retention | 용도 |
|-------|-----------|-------------|-----------|------|
| `axon.event.raw` | 3 | 1 | 7d | 사용자 행동 이벤트 (PAGE_VIEW, CLICK, APPROVED, PURCHASE) |
| `axon.campaign-activity.command` | 3 | 1 | 30d | 캠페인 활동 명령 (FCFS 예약 등) |
| `axon.campaign-activity.log` | 3 | 1 | 30d | 캠페인 활동 도메인 이벤트 로그 |
| `axon.user.login` | 1 | 1 | 7d | 사용자 로그인 이벤트 |

## 🔧 토픽 수정

### 파티션 수 증가 (감소는 불가능!)

```bash
kafka-topics.sh --bootstrap-server localhost:9092 \
  --alter \
  --topic axon.event.raw \
  --partitions 6
```

### Retention 설정 변경

```bash
kafka-configs.sh --bootstrap-server localhost:9092 \
  --entity-type topics \
  --entity-name axon.event.raw \
  --alter \
  --add-config retention.ms=1209600000  # 14 days
```

### 토픽 삭제

```bash
kafka-topics.sh --bootstrap-server localhost:9092 \
  --delete \
  --topic axon.event.raw
```

## 📈 파티션 수 결정 가이드

**계산 공식**:
```
필요 파티션 수 = max(처리량 / 단일파티션처리량, 컨슈머수)
```

**예시**:
- 초당 30,000 이벤트
- 단일 파티션 처리량: 10,000/s
- 필요 파티션: 30,000 / 10,000 = **3개**

**Axon CDP 기준**:
- `axon.event.raw`: 3개 (높은 처리량)
- `axon.campaign-activity.*`: 3개 (FCFS 동시성)
- `axon.user.login`: 1개 (낮은 처리량)

## 🚨 주의사항

1. **파티션 수는 증가만 가능** (감소 불가)
2. **Replication Factor 변경 불가** (토픽 재생성 필요)
3. **프로덕션에서는 Replication Factor ≥ 2** 권장
4. **Retention 설정은 디스크 용량 고려**

## 📚 참고 명령어

```bash
# 모든 토픽 목록
kubectl exec -it axon-kafka-controller-0 -- \
  kafka-topics.sh --bootstrap-server localhost:9092 --list

# 특정 토픽 상세 정보
kubectl exec -it axon-kafka-controller-0 -- \
  kafka-topics.sh --bootstrap-server localhost:9092 \
  --describe --topic axon.event.raw

# 토픽별 메시지 수 확인
kubectl exec -it axon-kafka-controller-0 -- \
  kafka-run-class.sh kafka.tools.GetOffsetShell \
  --broker-list localhost:9092 \
  --topic axon.event.raw
```
