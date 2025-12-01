# Fluent Bit + Kafka + Elasticsearch 배포 가이드

> **상태**: 설정 파일 준비 완료, 팀 회의 후 배포 예정

## 아키텍처

```
K8s Pods (로그) → Fluent Bit DaemonSet → Kafka Topic (k8s.logs)
                                              ↓
Browser/Backend → Kafka Topic (axon.event.raw)
                                              ↓
                                        Kafka Connect
                                              ↓
                                        Elasticsearch
```

## 배포 순서

### 1. Kafka Topic 생성

**목적**: K8s 로그를 위한 별도 토픽 생성

```bash
kubectl run kafka-create-topic --rm -i --restart=Never \
  --image=confluentinc/cp-kafka:7.9.0 -- bash -c \
  "kafka-topics --bootstrap-server axon-kafka:9092 \
   --create --topic k8s.logs \
   --partitions 3 \
   --replication-factor 1"
```

**검증**:
```bash
# Topic 목록 확인
kubectl run kafka-list-topics --rm -i --restart=Never \
  --image=confluentinc/cp-kafka:7.9.0 -- bash -c \
  "kafka-topics --bootstrap-server axon-kafka:9092 --list"
```

---

### 2. Fluent Bit 배포

**목적**: 모든 노드에서 K8s 로그 수집 → Kafka로 전송

```bash
# Helm repo 추가
helm repo add fluent https://fluent.github.io/helm-charts
helm repo update

# Fluent Bit 배포
helm install fluent-bit fluent/fluent-bit \
  -f /Users/dem/Project/Axon/helm/fluentbit-values.yaml
```

**검증**:
```bash
# DaemonSet 확인 (워커 노드 개수만큼 실행되어야 함)
kubectl get daemonset fluent-bit

# Pod 상태 확인
kubectl get pods -l app.kubernetes.io/name=fluent-bit

# 로그 확인 (Kafka 연결 확인)
kubectl logs -l app.kubernetes.io/name=fluent-bit --tail=50
```

---

### 3. Kafka Connect Sink Connector 추가

**목적**: k8s.logs topic → Elasticsearch 연결

```bash
# Connector 등록
kubectl exec kafka-connect-<POD_ID> -- curl -s -X POST \
  -H "Content-Type: application/json" \
  --data @- http://localhost:8083/connectors <<'EOF'
{
  "name": "elasticsearch-sink-k8s-logs",
  "config": {
    "connector.class": "io.confluent.connect.elasticsearch.ElasticsearchSinkConnector",
    "tasks.max": "1",
    "topics": "k8s.logs",
    "connection.url": "http://elasticsearch-master:9200",
    "key.ignore": "true",
    "schema.ignore": "true",
    "type.name": "_doc",
    "behavior.on.null.values": "ignore",
    "behavior.on.malformed.documents": "warn",
    "errors.tolerance": "all",
    "errors.log.enable": "true",
    "errors.log.include.messages": "true"
  }
}
EOF
```

**또는 파일 사용**:
```bash
kubectl exec kafka-connect-<POD_ID> -- curl -s -X POST \
  -H "Content-Type: application/json" \
  --data "$(cat /Users/dem/Project/Axon/helm/es-sink-k8s-logs.json)" \
  http://localhost:8083/connectors
```

**검증**:
```bash
# Connector 목록 확인
kubectl exec kafka-connect-<POD_ID> -- curl -s http://localhost:8083/connectors

# Connector 상태 확인
kubectl exec kafka-connect-<POD_ID> -- curl -s \
  http://localhost:8083/connectors/elasticsearch-sink-k8s-logs/status
```

---

### 4. 전체 파이프라인 검증

#### 4-1. Kafka에 메시지 도착 확인

```bash
# k8s.logs topic에 메시지가 쌓이는지 확인
kubectl run kafka-consumer-test --rm -i --restart=Never \
  --image=confluentinc/cp-kafka:7.9.0 -- bash -c \
  "kafka-console-consumer --bootstrap-server axon-kafka:9092 \
   --topic k8s.logs --from-beginning --max-messages 5"
```

#### 4-2. Elasticsearch 인덱스 확인

```bash
# 인덱스 목록 확인
kubectl exec elasticsearch-0 -- curl -s 'http://localhost:9200/_cat/indices?v'

# k8s.logs 인덱스 문서 개수
kubectl exec elasticsearch-0 -- curl -s 'http://localhost:9200/k8s.logs/_count?pretty'

# 실제 로그 데이터 확인
kubectl exec elasticsearch-0 -- curl -s 'http://localhost:9200/k8s.logs/_search?pretty&size=5'
```

---

## 리소스 사용량

### Fluent Bit DaemonSet (노드당)
- CPU: 100m (request), 200m (limit)
- Memory: 256Mi (request), 512Mi (limit)
- 워커 노드 2개 = 총 2개 Pod

### 예상 총 증가량
- CPU: 200m × 2 = 400m
- Memory: 512Mi × 2 = 1Gi

---

## 문제 해결

### Fluent Bit Pod가 시작 안 됨
```bash
# Pod 상태 확인
kubectl describe pod -l app.kubernetes.io/name=fluent-bit

# 권한 문제 확인
kubectl get serviceaccount fluent-bit
kubectl get clusterrolebinding | grep fluent-bit
```

### Kafka 연결 실패
```bash
# Fluent Bit 로그에서 에러 확인
kubectl logs -l app.kubernetes.io/name=fluent-bit | grep -i error

# Kafka Service 확인
kubectl get svc axon-kafka
```

### Connector 실패
```bash
# Connector 상세 에러 확인
kubectl exec kafka-connect-<POD_ID> -- curl -s \
  http://localhost:8083/connectors/elasticsearch-sink-k8s-logs/status | jq

# Kafka Connect 로그 확인
kubectl logs kafka-connect-<POD_ID> | grep -i k8s.logs
```

### ES 인덱스가 안 생김
```bash
# Topic에 메시지는 있는지 확인
kubectl run kafka-consumer-test --rm -i --restart=Never \
  --image=confluentinc/cp-kafka:7.9.0 -- bash -c \
  "kafka-console-consumer --bootstrap-server axon-kafka:9092 \
   --topic k8s.logs --from-beginning --max-messages 1"

# Connector Task 상태 확인
kubectl exec kafka-connect-<POD_ID> -- curl -s \
  http://localhost:8083/connectors/elasticsearch-sink-k8s-logs/tasks/0/status
```

---

## 롤백

### Fluent Bit 제거
```bash
helm uninstall fluent-bit
```

### Connector 제거
```bash
kubectl exec kafka-connect-<POD_ID> -- curl -X DELETE \
  http://localhost:8083/connectors/elasticsearch-sink-k8s-logs
```

### Topic 제거 (선택)
```bash
kubectl run kafka-delete-topic --rm -i --restart=Never \
  --image=confluentinc/cp-kafka:7.9.0 -- bash -c \
  "kafka-topics --bootstrap-server axon-kafka:9092 \
   --delete --topic k8s.logs"
```

---

## 설정 파일 위치

- Fluent Bit Values: `/Users/dem/Project/Axon/helm/fluentbit-values.yaml`
- ES Sink (k8s.logs): `/Users/dem/Project/Axon/helm/es-sink-k8s-logs.json`
- ES Sink (axon.event.raw): `/Users/dem/Project/Axon/helm/es-sink-connector.json`
- Kafka Connect: `/Users/dem/Project/Axon/helm/kafka-connect.yaml`

---

## 참고 사항

### Topic 설정
- **k8s.logs**: K8s 로그 (Fluent Bit → Kafka Connect → ES)
- **axon.event.raw**: 비즈니스 이벤트 (Browser/Backend → Kafka Connect → ES)

### Partitions
- k8s.logs: 3 partitions (권장)
- 노드가 많아지면 partition 수 조정 가능

### Replication Factor
- 현재: 1 (SingleNode Kafka)
- 프로덕션: 3 권장

---

## 다음 단계

1. [ ] 팀 회의: k8s.logs topic 생성 결정
2. [ ] 위 가이드대로 배포
3. [ ] 파이프라인 검증
4. [ ] 대시보드 연동 테스트
