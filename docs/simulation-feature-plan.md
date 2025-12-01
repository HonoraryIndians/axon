# Activity 시뮬레이션 기능 구현 계획

## 📋 요구사항

### 기능 개요
마케터와 개발자가 새로운 Activity를 등록할 때, 실제 트래픽 없이 시뮬레이션을 통해 대시보드 데이터를 미리 확인할 수 있는 기능

### 핵심 요구사항
- **UI 위치**: Admin Dashboard (`/admin/dashboard`)
- **실행 대상**: DRAFT 또는 TEST 상태의 Activity만
- **권한**: ADMIN, MANAGER 롤 필요
- **데이터 격리**: 테스트 환경 분리 (user_id >= 1000 방식)
- **설정 가능 파라미터**:
  - 방문자 수 (visitors)
  - 전환율 (conversionRate)
  - 시간 범위 (timeRangeDays)

### 안전장치
1. **상태 제한**: ACTIVE 상태의 Activity는 시뮬레이션 불가
2. **권한 제한**: ADMIN/MANAGER만 실행 가능
3. **동시 실행 방지**: 한 번에 하나의 시뮬레이션만 실행
4. **데이터 격리**: 실제 유저/데이터와 완전 분리

---

## 🏗️ 아키텍처 설계

### System Flow

```
┌──────────────────────────────────────────────────────────────┐
│  Frontend: Admin Dashboard                                    │
│  ┌────────────────────────────────────────────────────────┐  │
│  │ Activity Card (DRAFT/TEST)                             │  │
│  │ ┌────────────────────────────────────────────────────┐ │  │
│  │ │ 🧪 시뮬레이션 실행                                 │ │  │
│  │ │                                                      │ │  │
│  │ │ 방문자 수: [100 ▼] 명                              │ │  │
│  │ │ 전환율:   [10  ▼] %                                │ │  │
│  │ │ 기간:     [7   ▼] 일                               │ │  │
│  │ │                                                      │ │  │
│  │ │ [시뮬레이션 시작] [취소]                           │ │  │
│  │ └────────────────────────────────────────────────────┘ │  │
│  └────────────────────────────────────────────────────────┘  │
└──────────────────────────────────────────────────────────────┘
                    ↓ POST /api/v1/simulation/run
┌──────────────────────────────────────────────────────────────┐
│  Core Service: SimulationController                          │
│  ┌────────────────────────────────────────────────────────┐  │
│  │ 1. 권한 검증 (isAdmin || isManager)                   │  │
│  │ 2. Activity 상태 확인 (DRAFT || TEST)                 │  │
│  │ 3. 중복 실행 체크 (Redis: simulation:lock:{id})       │  │
│  │ 4. 비동기 작업 시작 (@Async)                          │  │
│  │ 5. Job ID 반환                                         │  │
│  └────────────────────────────────────────────────────────┘  │
└──────────────────────────────────────────────────────────────┘
                    ↓ Async Execution
┌──────────────────────────────────────────────────────────────┐
│  SimulationService                                           │
│  ┌────────────────────────────────────────────────────────┐  │
│  │ Step 1: 환경 초기화                                   │  │
│  │   - Elasticsearch 정리 (activityId 기준)              │  │
│  │   - Redis 정리 (campaign keys)                        │  │
│  │   - MySQL 정리 (FK_CHECKS=0, DELETE)                  │  │
│  │                                                          │  │
│  │ Step 2: 테스트 유저 생성                              │  │
│  │   - users: id 1000 ~ 1000+visitors                     │  │
│  │   - user_summary 초기화                                │  │
│  │                                                          │  │
│  │ Step 3: 퍼널 이벤트 생성                              │  │
│  │   - VIEW events (모든 방문자)                          │  │
│  │   - APPLY events (확률적)                              │  │
│  │   - APPROVED events (Kafka 발행)                       │  │
│  │   - PURCHASE events (전환율 적용)                      │  │
│  │                                                          │  │
│  │ Step 4: 시간대 분산 처리                              │  │
│  │   - timeRangeDays 동안 균등 분산                       │  │
│  │   - created_at, purchase_at 백데이트                   │  │
│  │                                                          │  │
│  │ Step 5: 결과 집계                                     │  │
│  │   - 대시보드 메트릭 생성 확인                          │  │
│  │   - 시뮬레이션 결과 저장                               │  │
│  └────────────────────────────────────────────────────────┘  │
└──────────────────────────────────────────────────────────────┘
                    ↓ Progress Updates (SSE)
┌──────────────────────────────────────────────────────────────┐
│  Progress Monitoring (SSE Endpoint)                          │
│  GET /api/v1/simulation/progress/{jobId}                     │
│                                                                │
│  ✅ 환경 초기화 완료                                          │
│  🔄 테스트 유저 생성 중... (50/100)                          │
│  🔄 이벤트 생성 중... (30/100)                               │
│  ✅ 시뮬레이션 완료!                                          │
│  📊 결과 보기: /admin/dashboard/1                            │
└──────────────────────────────────────────────────────────────┘
```

---

## 📦 데이터 모델

### SimulationRequest DTO
```java
public record SimulationRequest(
    Long activityId,           // 시뮬레이션 대상 Activity
    Integer visitors,          // 방문자 수 (기본값: 100)
    Integer conversionRate,    // 구매 전환율 % (기본값: 10)
    Integer timeRangeDays      // 시간 범위 (기본값: 7일)
) {
    // Validation
    public SimulationRequest {
        if (visitors < 10 || visitors > 10000) {
            throw new IllegalArgumentException("Visitors must be between 10 and 10000");
        }
        if (conversionRate < 1 || conversionRate > 100) {
            throw new IllegalArgumentException("Conversion rate must be between 1 and 100");
        }
        if (timeRangeDays < 1 || timeRangeDays > 90) {
            throw new IllegalArgumentException("Time range must be between 1 and 90 days");
        }
    }
}
```

### SimulationJob (저장용)
```java
@Entity
public class SimulationJob {
    @Id
    private String jobId;              // UUID
    private Long activityId;
    private Long userId;               // 실행한 사용자

    private SimulationStatus status;   // RUNNING, COMPLETED, FAILED
    private String currentStep;        // 현재 진행 단계
    private Integer progress;          // 0-100 진행률

    private Integer visitors;
    private Integer conversionRate;
    private Integer timeRangeDays;

    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private String errorMessage;       // 실패 시 에러 메시지

    @Column(columnDefinition = "JSON")
    private String resultMetrics;      // 시뮬레이션 결과 (JSON)
}

public enum SimulationStatus {
    PENDING,      // 대기 중
    RUNNING,      // 실행 중
    COMPLETED,    // 완료
    FAILED        // 실패
}
```

---

## 🔧 구현 단계 (Implementation Phases)

### Phase 1: Backend API 구현 (2-3일)

#### 1.1 Controller 구현
```java
@RestController
@RequestMapping("/api/v1/simulation")
@RequiredArgsConstructor
public class SimulationController {

    private final SimulationService simulationService;

    @PostMapping("/run")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<SimulationJobResponse> runSimulation(
        @Valid @RequestBody SimulationRequest request,
        @AuthenticationPrincipal UserDetails userDetails
    ) {
        // 1. Activity 상태 확인
        // 2. 중복 실행 방지
        // 3. 비동기 실행 시작
        String jobId = simulationService.startSimulation(request, userDetails.getUsername());
        return ResponseEntity.ok(new SimulationJobResponse(jobId));
    }

    @GetMapping("/progress/{jobId}")
    public SseEmitter streamProgress(@PathVariable String jobId) {
        // SSE로 진행 상황 스트리밍
        return simulationService.streamProgress(jobId);
    }

    @GetMapping("/status/{jobId}")
    public ResponseEntity<SimulationJob> getStatus(@PathVariable String jobId) {
        return ResponseEntity.ok(simulationService.getJobStatus(jobId));
    }

    @DeleteMapping("/cancel/{jobId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<Void> cancelSimulation(@PathVariable String jobId) {
        simulationService.cancelJob(jobId);
        return ResponseEntity.noContent().build();
    }
}
```

#### 1.2 Service 구현
```java
@Service
@RequiredArgsConstructor
public class SimulationService {

    private final CampaignActivityRepository activityRepository;
    private final RedisTemplate<String, String> redisTemplate;
    private final SimulationJobRepository jobRepository;
    private final SimulationExecutor executor;

    public String startSimulation(SimulationRequest request, String username) {
        // 1. Activity 검증
        CampaignActivity activity = activityRepository.findById(request.activityId())
            .orElseThrow(() -> new NotFoundException("Activity not found"));

        if (activity.getStatus() == ActivityStatus.ACTIVE) {
            throw new IllegalStateException("Cannot simulate ACTIVE activities");
        }

        // 2. 중복 실행 방지 (Redis distributed lock)
        String lockKey = "simulation:lock:" + request.activityId();
        Boolean acquired = redisTemplate.opsForValue()
            .setIfAbsent(lockKey, "locked", Duration.ofMinutes(30));

        if (Boolean.FALSE.equals(acquired)) {
            throw new IllegalStateException("Simulation already running for this activity");
        }

        // 3. Job 생성
        String jobId = UUID.randomUUID().toString();
        SimulationJob job = SimulationJob.builder()
            .jobId(jobId)
            .activityId(request.activityId())
            .userId(getCurrentUserId(username))
            .status(SimulationStatus.PENDING)
            .visitors(request.visitors())
            .conversionRate(request.conversionRate())
            .timeRangeDays(request.timeRangeDays())
            .startedAt(LocalDateTime.now())
            .build();

        jobRepository.save(job);

        // 4. 비동기 실행
        executor.executeAsync(job, request);

        return jobId;
    }
}
```

#### 1.3 Executor 구현 (핵심 로직)
```java
@Component
@RequiredArgsConstructor
public class SimulationExecutor {

    private final JdbcTemplate jdbcTemplate;
    private final ElasticsearchClient esClient;
    private final RedisTemplate<String, String> redisTemplate;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Async("simulationExecutor")
    public void executeAsync(SimulationJob job, SimulationRequest request) {
        try {
            // Step 1: 환경 초기화 (20%)
            updateProgress(job.getJobId(), "환경 초기화 중...", 10);
            cleanupEnvironment(request.activityId());
            updateProgress(job.getJobId(), "환경 초기화 완료", 20);

            // Step 2: 테스트 유저 생성 (40%)
            updateProgress(job.getJobId(), "테스트 유저 생성 중...", 30);
            createTestUsers(request.visitors());
            updateProgress(job.getJobId(), "테스트 유저 생성 완료", 40);

            // Step 3: 이벤트 생성 (80%)
            updateProgress(job.getJobId(), "이벤트 생성 중...", 50);
            generateFunnelEvents(request);
            updateProgress(job.getJobId(), "이벤트 생성 완료", 80);

            // Step 4: 데이터 검증 (90%)
            updateProgress(job.getJobId(), "결과 검증 중...", 90);
            validateResults(request);

            // Step 5: 완료
            completeJob(job.getJobId());
            updateProgress(job.getJobId(), "시뮬레이션 완료!", 100);

        } catch (Exception e) {
            failJob(job.getJobId(), e.getMessage());
            log.error("Simulation failed for job {}", job.getJobId(), e);
        } finally {
            // Redis lock 해제
            redisTemplate.delete("simulation:lock:" + request.activityId());
        }
    }

    private void cleanupEnvironment(Long activityId) {
        // 기존 스크립트 로직을 Java로 포팅
        // - Elasticsearch: DELETE by query
        // - Redis: DEL keys
        // - MySQL: setup SQL 파일 실행 (SET FOREIGN_KEY_CHECKS=0)

        // SQL 파일 실행
        String sql = loadSqlFile("cleanup-simulation-data.sql");
        jdbcTemplate.execute(sql);
    }

    private void createTestUsers(int count) {
        // Batch INSERT for performance
        String sql = """
            INSERT INTO users (id, email, name, role, grade, created_at, updated_at)
            VALUES (?, ?, ?, 'USER', 'BRONZE', NOW(), NOW())
            ON DUPLICATE KEY UPDATE updated_at = NOW()
        """;

        jdbcTemplate.batchUpdate(sql, new BatchPreparedStatementSetter() {
            @Override
            public void setValues(PreparedStatement ps, int i) throws SQLException {
                int userId = 1000 + i;
                ps.setInt(1, userId);
                ps.setString(2, "test" + userId + "@axon.com");
                ps.setString(3, "TestUser" + userId);
            }

            @Override
            public int getBatchSize() {
                return count;
            }
        });
    }

    private void generateFunnelEvents(SimulationRequest request) {
        // 기존 generate-full-funnel.sh 로직을 Java로 포팅
        // - VIEW events → ES
        // - APPLY events → MySQL + Kafka
        // - PURCHASE events → MySQL (전환율 적용)

        int purchaseCount = (int) (request.visitors() * request.conversionRate() / 100.0);

        for (int i = 0; i < request.visitors(); i++) {
            int userId = 1000 + i;
            LocalDateTime eventTime = calculateEventTime(i, request);

            // VIEW event
            publishViewEvent(request.activityId(), userId, eventTime);

            // APPLY event (확률적)
            if (shouldApply()) {
                publishApplyEvent(request.activityId(), userId, eventTime);
            }

            // PURCHASE event (전환율 적용)
            if (i < purchaseCount) {
                createPurchase(request.activityId(), userId, eventTime);
            }
        }
    }

    private LocalDateTime calculateEventTime(int index, SimulationRequest request) {
        // 시간대 분산: timeRangeDays 동안 균등 분배
        long totalMinutes = request.timeRangeDays() * 24 * 60L;
        long minutesPerUser = totalMinutes / request.visitors();
        return LocalDateTime.now()
            .minusDays(request.timeRangeDays())
            .plusMinutes(index * minutesPerUser);
    }
}
```

---

### Phase 2: Frontend UI 구현 (1-2일)

#### 2.1 Dashboard에 시뮬레이션 버튼 추가

**파일 위치**: `core-service/src/main/resources/templates/admin/dashboard.html`

```html
<!-- Activity 카드에 시뮬레이션 버튼 추가 -->
<div class="activity-card" th:if="${activity.status == 'DRAFT' or activity.status == 'TEST'}">
    <h3 th:text="${activity.name}">Activity Name</h3>
    <p>Status: <span class="badge" th:text="${activity.status}">DRAFT</span></p>

    <!-- 시뮬레이션 버튼 -->
    <button class="btn btn-primary"
            onclick="openSimulationModal([[${activity.id}]])">
        🧪 시뮬레이션 실행
    </button>
</div>

<!-- 시뮬레이션 설정 모달 -->
<div id="simulationModal" class="modal">
    <div class="modal-content">
        <h2>시뮬레이션 설정</h2>
        <form id="simulationForm">
            <div class="form-group">
                <label>방문자 수</label>
                <select name="visitors">
                    <option value="50">50명</option>
                    <option value="100" selected>100명</option>
                    <option value="500">500명</option>
                    <option value="1000">1000명</option>
                </select>
            </div>

            <div class="form-group">
                <label>구매 전환율 (%)</label>
                <select name="conversionRate">
                    <option value="5">5%</option>
                    <option value="10" selected>10%</option>
                    <option value="20">20%</option>
                    <option value="30">30%</option>
                </select>
            </div>

            <div class="form-group">
                <label>시간 범위 (일)</label>
                <select name="timeRangeDays">
                    <option value="1">1일</option>
                    <option value="7" selected>7일</option>
                    <option value="30">30일</option>
                </select>
            </div>

            <div class="modal-actions">
                <button type="button" onclick="startSimulation()">시작</button>
                <button type="button" onclick="closeModal()">취소</button>
            </div>
        </form>
    </div>
</div>

<!-- 진행 상황 모달 -->
<div id="progressModal" class="modal">
    <div class="modal-content">
        <h2>시뮬레이션 실행 중...</h2>
        <div class="progress-bar">
            <div id="progressFill" style="width: 0%"></div>
        </div>
        <p id="progressText">준비 중...</p>
        <ul id="progressLog"></ul>
    </div>
</div>

<script>
let currentJobId = null;
let eventSource = null;

function openSimulationModal(activityId) {
    document.getElementById('simulationModal').style.display = 'block';
    document.getElementById('simulationForm').dataset.activityId = activityId;
}

function closeModal() {
    document.getElementById('simulationModal').style.display = 'none';
}

async function startSimulation() {
    const form = document.getElementById('simulationForm');
    const activityId = form.dataset.activityId;
    const formData = new FormData(form);

    const request = {
        activityId: parseInt(activityId),
        visitors: parseInt(formData.get('visitors')),
        conversionRate: parseInt(formData.get('conversionRate')),
        timeRangeDays: parseInt(formData.get('timeRangeDays'))
    };

    try {
        // 시뮬레이션 시작
        const response = await fetch('/api/v1/simulation/run', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(request)
        });

        if (!response.ok) {
            throw new Error('Failed to start simulation');
        }

        const { jobId } = await response.json();
        currentJobId = jobId;

        // 모달 전환
        closeModal();
        document.getElementById('progressModal').style.display = 'block';

        // SSE로 진행 상황 구독
        subscribeToProgress(jobId);

    } catch (error) {
        alert('시뮬레이션 시작 실패: ' + error.message);
    }
}

function subscribeToProgress(jobId) {
    eventSource = new EventSource(`/api/v1/simulation/progress/${jobId}`);

    eventSource.addEventListener('progress', (event) => {
        const data = JSON.parse(event.data);
        updateProgress(data.progress, data.message);
    });

    eventSource.addEventListener('complete', (event) => {
        const data = JSON.parse(event.data);
        completeSimulation(data);
        eventSource.close();
    });

    eventSource.addEventListener('error', (event) => {
        alert('시뮬레이션 실패');
        eventSource.close();
        document.getElementById('progressModal').style.display = 'none';
    });
}

function updateProgress(percentage, message) {
    document.getElementById('progressFill').style.width = percentage + '%';
    document.getElementById('progressText').textContent = message;

    // 로그 추가
    const log = document.getElementById('progressLog');
    const li = document.createElement('li');
    li.textContent = `[${new Date().toLocaleTimeString()}] ${message}`;
    log.appendChild(li);
    log.scrollTop = log.scrollHeight;
}

function completeSimulation(data) {
    document.getElementById('progressText').textContent = '✅ 시뮬레이션 완료!';

    setTimeout(() => {
        document.getElementById('progressModal').style.display = 'none';
        // 대시보드 새로고침
        location.reload();
    }, 2000);
}
</script>
```

---

### Phase 3: 테스트 및 최적화 (1일)

#### 3.1 단위 테스트
```java
@SpringBootTest
class SimulationServiceTest {

    @Test
    void shouldRejectActiveActivity() {
        // ACTIVE 상태 Activity 시뮬레이션 시도
        // → IllegalStateException 발생 확인
    }

    @Test
    void shouldPreventConcurrentSimulations() {
        // 동일 Activity에 대해 동시 시뮬레이션 시도
        // → 두 번째 요청 실패 확인
    }

    @Test
    void shouldGenerateCorrectConversionRate() {
        // 100명 방문, 10% 전환율 → 10명 구매 확인
    }
}
```

#### 3.2 통합 테스트
```java
@SpringBootTest
@AutoConfigureMockMvc
class SimulationIntegrationTest {

    @Test
    @WithMockUser(roles = "ADMIN")
    void shouldCompleteSimulationSuccessfully() {
        // 1. 시뮬레이션 시작
        // 2. Job ID 반환 확인
        // 3. 완료까지 대기
        // 4. 대시보드 데이터 생성 확인
    }
}
```

---

## 🔒 보안 고려사항

### 1. 권한 검증
- Spring Security `@PreAuthorize` 사용
- ADMIN, MANAGER 롤만 접근 가능

### 2. Rate Limiting
- Redis를 사용한 요청 제한
- 사용자당 하루 10회 제한

### 3. 리소스 보호
- 최대 방문자 수 제한 (10,000명)
- 동시 실행 제한 (1개)
- Timeout 설정 (10분)

### 4. 데이터 격리
- 테스트 유저 ID 범위 명확히 구분 (1000~)
- 실수로 프로덕션 데이터 삭제 방지

---

## 📊 모니터링 & 로깅

### 1. 메트릭 수집
```java
@Timed(value = "simulation.duration", description = "Simulation execution time")
public void executeAsync(SimulationJob job, SimulationRequest request) {
    // ...
}
```

### 2. 로그 레벨
- INFO: 시뮬레이션 시작/완료
- WARN: 검증 실패, 재시도
- ERROR: 실행 실패

### 3. Slack 알림 (Optional)
- 시뮬레이션 완료 시 채널 알림
- 실패 시 에러 상세 정보 전송

---

## 🚀 배포 계획

### 1. Database Migration
```sql
-- simulation_jobs 테이블 생성
CREATE TABLE simulation_jobs (
    job_id VARCHAR(36) PRIMARY KEY,
    activity_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL,
    current_step VARCHAR(255),
    progress INT DEFAULT 0,
    visitors INT,
    conversion_rate INT,
    time_range_days INT,
    started_at DATETIME(6),
    completed_at DATETIME(6),
    error_message TEXT,
    result_metrics JSON,
    INDEX idx_activity_id (activity_id),
    INDEX idx_user_id (user_id),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
```

### 2. 환경 변수 설정
```yaml
# application.yml
simulation:
  max-visitors: 10000
  max-concurrent-jobs: 1
  timeout-minutes: 10
  rate-limit:
    per-user-daily: 10
    per-activity-daily: 20
```

### 3. Feature Flag
```java
@ConditionalOnProperty(name = "feature.simulation.enabled", havingValue = "true")
public class SimulationConfiguration {
    // ...
}
```

---

## 📅 일정

| Phase | Task | Duration | Assignee |
|-------|------|----------|----------|
| 1 | Backend API 구현 | 2-3일 | Backend Dev |
| 2 | Frontend UI 구현 | 1-2일 | Frontend Dev |
| 3 | 테스트 & QA | 1일 | QA Team |
| 4 | 배포 & 모니터링 | 0.5일 | DevOps |
| **Total** | | **4-6일** | |

---

## ✅ 완료 기준

- [ ] DRAFT/TEST Activity에서 시뮬레이션 버튼 표시
- [ ] 시뮬레이션 파라미터 설정 가능
- [ ] 실시간 진행 상황 표시 (SSE)
- [ ] 대시보드에 시뮬레이션 데이터 반영
- [ ] ACTIVE Activity 시뮬레이션 방지
- [ ] 동시 실행 방지
- [ ] 에러 핸들링 및 롤백
- [ ] 단위/통합 테스트 작성
- [ ] 문서화 (API 스펙, 사용 가이드)

---

## 🔮 향후 확장 아이디어

1. **시나리오 프리셋**: 저/중/고 트래픽 시나리오 저장
2. **비교 분석**: 여러 설정의 시뮬레이션 결과 비교
3. **A/B 테스트 시뮬레이션**: 두 Activity 성과 비교
4. **예측 모델**: 과거 데이터 기반 전환율 예측
5. **비용 시뮬레이션**: 예상 마케팅 비용 계산
