# Kafka Deployment for Axon CDP

## Overview

Kafka deployment using Bitnami Helm chart for Kubernetes.

**Versions:**
- Helm Chart: 31.4.0
- Apache Kafka: 3.9.1
- Mode: KRaft (No ZooKeeper)

## Compatibility

| Component | Local Dev | K8s Deployment | Status |
|-----------|-----------|----------------|--------|
| Kafka | Confluent 7.9.0 (3.9.x) | Bitnami 3.9.1 | ✅ Compatible |
| Kafka Connect | Confluent 7.9.0 | Confluent 7.9.0 | ✅ Same |
| ES Connector | 15.0.0 | 15.0.0 | ✅ Same |
| Elasticsearch | 8.15.0 | 8.15.0 | ✅ Compatible |

## Prerequisites

- Kubernetes cluster (1.19+)
- Helm 3.8+
- kubectl configured
- Persistent storage provisioner

## Installation

### 1. Update Dependencies

```bash
cd k8s/kafka
helm dependency update
```

### 2. Install

```bash
# Dry run (recommended first)
helm install axon-kafka . -f values.yaml --dry-run --debug

# Actual installation
helm install axon-kafka . -f values.yaml
```

### 3. Verify

```bash
# Check pods
kubectl get pods -l app.kubernetes.io/name=kafka

# Check service
kubectl get svc axon-kafka

# Test connection
kubectl run kafka-test --rm -it --restart=Never \
  --image=bitnami/kafka:3.9.1 -- \
  kafka-topics.sh --list --bootstrap-server axon-kafka:9092
```

## Configuration

All configuration is in `values.yaml`. Key settings:

- **Replication**: `kafka.replicaCount: 3`
- **Storage**: `kafka.persistence.size: 100Gi`
- **Resources**: `kafka.resources.limits.memory: 4Gi`
- **KRaft**: `kafka.kraft.enabled: true`

## Upgrade

```bash
# Update Chart.yaml version first
helm dependency update
helm upgrade axon-kafka . -f values.yaml
```

## Uninstall

```bash
helm uninstall axon-kafka

# Delete PVCs (optional)
kubectl delete pvc -l app.kubernetes.io/name=kafka
```

## Troubleshooting

### Check Logs
```bash
kubectl logs -l app.kubernetes.io/name=kafka --tail=100 -f
```

### Describe Pod
```bash
kubectl describe pod <kafka-pod-name>
```

### Connect to Pod
```bash
kubectl exec -it <kafka-pod-name> -- bash
```

## GitOps Integration

This configuration is ready for GitOps tools:

- **ArgoCD**: Point to this directory
- **FluxCD**: Use HelmRelease CRD
- **CI/CD**: Add to pipeline

## References

- [Bitnami Kafka Chart](https://github.com/bitnami/charts/tree/main/bitnami/kafka)
- [Kafka Documentation](https://kafka.apache.org/documentation/)
