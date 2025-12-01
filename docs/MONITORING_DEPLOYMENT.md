# 모니터링 스택 배포 가이드

> Prometheus + Grafana + Kibana

## 아키텍처

```
Prometheus (메트릭 수집)
   ↓
Grafana (메트릭 시각화)

Elasticsearch (로그 저장)
   ↓
Kibana (로그 시각화)
```

---

## 1. Prometheus 배포

### 배포 명령어
```bash
# Helm repo 추가
helm repo add prometheus-community https://prometheus-community.github.io/helm-charts
helm repo update

# Prometheus 설치
helm install prometheus prometheus-community/prometheus \
  -f /Users/dem/Project/Axon/helm/prometheus-values.yaml
```

### 검증
```bash
# Pod 확인
kubectl get pods -l app.kubernetes.io/name=prometheus

# Service 확인 (NodePort 30090)
kubectl get svc prometheus-server

# 웹 UI 접근
# http://<워커노드IP>:30090
```

### 자원 사용량
- Prometheus Server: 200m CPU, 512Mi RAM (request)
- Node Exporter: 50m CPU, 64Mi RAM (노드당)
- Kube State Metrics: 50m CPU, 64Mi RAM

---

## 2. Grafana 배포

### 배포 명령어
```bash
# Helm repo 추가
helm repo add grafana https://grafana.github.io/helm-charts
helm repo update

# Grafana 설치
helm install grafana grafana/grafana \
  -f /Users/dem/Project/Axon/helm/grafana-values.yaml
```

### 검증
```bash
# Pod 확인
kubectl get pods -l app.kubernetes.io/name=grafana

# Service 확인 (NodePort 30300)
kubectl get svc grafana

# Admin 비밀번호 확인 (설정 파일에 명시됨)
echo "admin / axon-admin-2025"

# 웹 UI 접근
# http://<워커노드IP>:30300
```

### Grafana 초기 설정
1. 로그인: `admin / axon-admin-2025`
2. Prometheus 데이터소스 자동 추가됨 확인
3. 기본 대시보드 확인:
   - Kubernetes Cluster Monitoring
   - Node Exporter Full
   - Kubernetes Pods Monitoring

### 자원 사용량
- Grafana: 200m CPU, 256Mi RAM (request)

---

## 3. Kibana 배포

### 배포 명령어
```bash
# Kibana 배포
kubectl apply -f /Users/dem/Project/Axon/helm/kibana.yaml
```

### 검증
```bash
# Pod 확인
kubectl get pods -l app=kibana

# Pod 로그 확인 (초기화 시간 2-3분 소요)
kubectl logs -l app=kibana --tail=50

# Service 확인 (NodePort 30561)
kubectl get svc kibana

# 웹 UI 접근
# http://<워커노드IP>:30561
```

### Kibana 초기 설정
1. 웹 UI 접속
2. "Explore on my own" 선택
3. Index Pattern 생성:
   - `axon.event.raw` (비즈니스 로그)
   - `k8s.logs` (K8s 로그, Fluent Bit 배포 후)
4. Discover 메뉴에서 로그 확인

### 자원 사용량
- Kibana: 200m CPU, 512Mi RAM (request)

---

## 전체 배포 순서 (한 번에)

```bash
# 1. Prometheus
helm repo add prometheus-community https://prometheus-community.github.io/helm-charts
helm install prometheus prometheus-community/prometheus \
  -f /Users/dem/Project/Axon/helm/prometheus-values.yaml

# 2. Grafana
helm repo add grafana https://grafana.github.io/helm-charts
helm install grafana grafana/grafana \
  -f /Users/dem/Project/Axon/helm/grafana-values.yaml

# 3. Kibana
kubectl apply -f /Users/dem/Project/Axon/helm/kibana.yaml

# 4. 전체 확인
kubectl get pods | grep -E "prometheus|grafana|kibana"
kubectl get svc | grep -E "prometheus|grafana|kibana"
```

---

## 접속 정보

| 서비스 | URL | 계정 | 포트 |
|--------|-----|------|------|
| Prometheus | http://워커노드IP:30090 | 없음 | 30090 |
| Grafana | http://워커노드IP:30300 | admin / axon-admin-2025 | 30300 |
| Kibana | http://워커노드IP:30561 | 없음 | 30561 |

---

## 총 자원 사용량 (예상)

| 컴포넌트 | CPU Request | Memory Request | CPU Limit | Memory Limit |
|----------|-------------|----------------|-----------|--------------|
| Prometheus Server | 200m | 512Mi | 500m | 1Gi |
| Node Exporter (×2) | 100m | 128Mi | 200m | 256Mi |
| Kube State Metrics | 50m | 64Mi | 100m | 128Mi |
| Grafana | 200m | 256Mi | 500m | 512Mi |
| Kibana | 200m | 512Mi | 500m | 1Gi |
| **총합** | **750m** | **1.5Gi** | **1.8 vCPU** | **3Gi** |

**워커 노드별 영향:**
- Worker01: +375m CPU, +750Mi RAM
- Worker02: +375m CPU, +750Mi RAM

**여유 확인:**
- Worker01: 현재 44% CPU, 20% Memory → 배포 후 53% CPU, 24% Memory
- Worker02: 현재 71% CPU, 46% Memory → 배포 후 80% CPU, 51% Memory

---

## 문제 해결

### Prometheus Pod가 시작 안 됨
```bash
# PVC 상태 확인
kubectl get pvc

# StorageClass 확인
kubectl get sc ktc-nfs-client

# Pod 이벤트 확인
kubectl describe pod -l app.kubernetes.io/name=prometheus
```

### Grafana 대시보드가 로드 안 됨
```bash
# Prometheus 데이터소스 확인
kubectl logs -l app.kubernetes.io/name=grafana | grep datasource

# Prometheus Service 연결 테스트
kubectl run curl-test --rm -i --restart=Never --image=curlimages/curl -- \
  curl http://prometheus-server:9090/api/v1/status/config
```

### Kibana가 Elasticsearch 연결 실패
```bash
# Elasticsearch 상태 확인
kubectl get pods elasticsearch-0

# Elasticsearch Service 확인
kubectl get svc elasticsearch-master

# Kibana 로그 확인
kubectl logs -l app=kibana | grep -i elasticsearch
```

---

## 롤백

### Prometheus 제거
```bash
helm uninstall prometheus
kubectl delete pvc -l app.kubernetes.io/name=prometheus
```

### Grafana 제거
```bash
helm uninstall grafana
kubectl delete pvc -l app.kubernetes.io/name=grafana
```

### Kibana 제거
```bash
kubectl delete -f /Users/dem/Project/Axon/helm/kibana.yaml
```

---

## 다음 단계

### 1. Grafana 커스텀 대시보드 생성
- Axon CDP 전용 메트릭 대시보드
- FCFS 이벤트 모니터링 대시보드
- Kafka/ES 성능 대시보드

### 2. Kibana Index Pattern 설정
- 비즈니스 로그: `axon.event.raw`
- K8s 로그: `k8s.logs` (Fluent Bit 배포 후)

### 3. Alert 설정
- Prometheus Alert Rules
- Grafana Alert Notifications

### 4. 애플리케이션 메트릭 연동
- Core-Service: Spring Boot Actuator + Prometheus
- Entry-Service: Spring Boot Actuator + Prometheus
- Annotation: `prometheus.io/scrape: "true"`

---

## 참고 자료

### 설정 파일 위치
- Prometheus: `/Users/dem/Project/Axon/helm/prometheus-values.yaml`
- Grafana: `/Users/dem/Project/Axon/helm/grafana-values.yaml`
- Kibana: `/Users/dem/Project/Axon/helm/kibana.yaml`

### Grafana 대시보드 ID
- Kubernetes Cluster: 7249
- Node Exporter: 1860
- Kubernetes Pods: 6417

### 유용한 명령어
```bash
# 전체 Pod 리소스 사용량 확인 (Metrics Server 필요)
kubectl top pods

# 노드 리소스 사용량 확인
kubectl top nodes

# Prometheus 메트릭 확인
curl http://워커노드IP:30090/api/v1/targets

# Grafana API 테스트
curl http://admin:axon-admin-2025@워커노드IP:30300/api/health
```
