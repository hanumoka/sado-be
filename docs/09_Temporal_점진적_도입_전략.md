# 🔄 Temporal 점진적 도입 전략

> 🔄 **문서 역할**: Temporal 점진적 도입 전략 (최종 문서)
>
> 📅 **작성일**: 2025-12-20
>
> 🎯 **적용 시기**: Week 4-10
> - Week 4-7: Simple 구현 (Temporal 없이)
> - Week 8: Temporal 학습
> - Week 9-10: Temporal 전환
>
> 🔗 **연결**: `07_최종_구현_계획.md` Week 8-10 섹션
>
> **핵심 원칙**: "왜 Temporal이 필요한가?"를 몸으로 체감하며 학습

---

## 🎯 전략 개요

### 핵심 아이디어
```
Phase 1 (Week 4-7): Temporal 없이 구현
  → 기본 지식 확보
  → 수동 보상 트랜잭션의 어려움 체감
  → State Machine, 재시도 로직 직접 구현

Phase 2 (Week 8): Temporal 학습
  → Temporal의 필요성 실감
  → 로컬 환경 구성 및 기초 학습

Phase 3 (Week 9-10): Temporal 전환
  → 기존 인터페이스 재사용
  → 구현체만 교체 (최소한의 변경)
  → A/B 테스트로 비교
```

### 왜 이 방식인가?

**학습 효과**:
1. **기본 개념 이해**: Temporal이 해결하는 문제를 먼저 겪어봄
2. **필요성 체감**: "Temporal이 이렇게 편하구나!" 실감
3. **학습 곡선 완화**: 한 번에 너무 많은 것을 배우지 않음

**실무 적용**:
1. **점진적 마이그레이션**: 실무에서도 이런 방식으로 도입
2. **리스크 감소**: 언제든 롤백 가능
3. **유연성**: Temporal이 과하다고 판단되면 Simple 버전 유지

---

## 🏗️ 설계 패턴: 추상화 레이어

### 1. Workflow 인터페이스 정의

**핵심**: Temporal 독립적인 인터페이스 설계

```java
// orchestrator-workflow 모듈
package com.hanumoka.sado.orchestrator.workflow;

/**
 * DICOM 분석 워크플로우 인터페이스
 * - Temporal 없이도 사용 가능
 * - 나중에 Temporal 구현체로 교체 가능
 */
public interface DicomAnalysisWorkflow {

    /**
     * Study 분석 워크플로우 실행
     *
     * @param request Study 정보
     * @return 분석 결과
     * @throws WorkflowException 워크플로우 실행 실패
     */
    AnalysisResult execute(StudyRequest request);

    /**
     * 워크플로우 상태 조회
     *
     * @param workflowId 워크플로우 ID
     * @return 현재 상태
     */
    WorkflowStatus getStatus(String workflowId);

    /**
     * 워크플로우 취소
     *
     * @param workflowId 워크플로우 ID
     */
    void cancel(String workflowId);
}
```

**DTO 정의** (Temporal 독립적):
```java
@Getter
@Builder
public class StudyRequest {
    private String studyId;
    private String patientId;
    private List<String> dicomFileIds;
}

@Getter
@Builder
public class AnalysisResult {
    private String workflowId;
    private String studyId;
    private String analysisId;
    private AnalysisStatus status;
    private Map<String, Object> results;  // AI 분석 결과
    private Instant completedAt;
}

public enum WorkflowStatus {
    PENDING,      // 대기 중
    RUNNING,      // 실행 중
    COMPLETED,    // 완료
    FAILED,       // 실패
    CANCELLED     // 취소됨
}
```

---

### 2. Activity 인터페이스 정의

**핵심**: 각 단계를 독립적인 Activity로 분리

```java
// orchestrator-workflow 모듈
package com.hanumoka.sado.orchestrator.workflow.activity;

/**
 * DICOM 업로드 Activity
 * - Temporal Activity로 전환 가능
 */
public interface DicomUploadActivity {

    /**
     * DICOM 파일 업로드 검증 및 메타데이터 추출
     */
    FileUploadResult validateAndUpload(StudyRequest request);

    /**
     * 보상 트랜잭션: 업로드 롤백
     */
    void compensateUpload(String fileId);
}

/**
 * Study 생성 Activity
 */
public interface StudyCreationActivity {

    /**
     * DB에 Study 레코드 생성
     */
    Study createStudy(FileUploadResult uploadResult);

    /**
     * 보상 트랜잭션: Study 삭제
     */
    void compensateStudy(String studyId);
}

/**
 * Triton 분석 Activity
 */
public interface TritonAnalysisActivity {

    /**
     * Triton 서버에 분석 요청
     *
     * @param studyId Study ID
     * @return 분석 Job ID
     */
    String requestAnalysis(String studyId);

    /**
     * 분석 결과 폴링 (최대 10분 대기)
     *
     * @param jobId Triton Job ID
     * @return 분석 결과
     * @throws AnalysisTimeoutException 타임아웃
     */
    AnalysisResult waitForResult(String jobId);

    /**
     * 보상 트랜잭션: 분석 취소
     */
    void compensateAnalysis(String jobId);
}

/**
 * 리포트 생성 Activity
 */
public interface ReportGenerationActivity {

    /**
     * 분석 결과 기반 리포트 생성
     */
    Report generateReport(AnalysisResult result);

    /**
     * 보상 트랜잭션: 리포트 삭제
     */
    void compensateReport(String reportId);
}
```

**공통 예외 정의**:
```java
public class WorkflowException extends RuntimeException {
    private final String workflowId;
    private final WorkflowStep failedStep;

    public WorkflowException(String workflowId, WorkflowStep step, String message, Throwable cause) {
        super(message, cause);
        this.workflowId = workflowId;
        this.failedStep = step;
    }
}

public enum WorkflowStep {
    UPLOAD,
    CREATE_STUDY,
    REQUEST_ANALYSIS,
    WAIT_RESULT,
    GENERATE_REPORT
}
```

---

## 📦 구현 1: Simple Workflow (Temporal 없이)

### Phase 1 (Week 4-7): Spring 기본 기능만 사용

#### 1.1 Simple Workflow 구현

```java
@Service
@Slf4j
public class SimpleDicomAnalysisWorkflow implements DicomAnalysisWorkflow {

    @Autowired
    private DicomUploadActivity uploadActivity;

    @Autowired
    private StudyCreationActivity studyActivity;

    @Autowired
    private TritonAnalysisActivity analysisActivity;

    @Autowired
    private ReportGenerationActivity reportActivity;

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Autowired
    private WorkflowStateRepository stateRepository;

    @Override
    @Transactional
    public AnalysisResult execute(StudyRequest request) {
        String workflowId = UUID.randomUUID().toString();

        // 워크플로우 상태 저장
        WorkflowState state = WorkflowState.builder()
            .workflowId(workflowId)
            .status(WorkflowStatus.RUNNING)
            .currentStep(WorkflowStep.UPLOAD)
            .build();
        stateRepository.save(state);

        FileUploadResult uploadResult = null;
        Study study = null;
        String jobId = null;
        AnalysisResult analysisResult = null;
        Report report = null;

        try {
            // 1. DICOM 업로드
            log.info("Workflow {}: Starting DICOM upload", workflowId);
            uploadResult = uploadActivity.validateAndUpload(request);
            state.setCurrentStep(WorkflowStep.CREATE_STUDY);
            stateRepository.save(state);
            eventPublisher.publishEvent(new DicomUploadedEvent(workflowId, uploadResult));

            // 2. Study 생성
            log.info("Workflow {}: Creating Study", workflowId);
            study = studyActivity.createStudy(uploadResult);
            state.setCurrentStep(WorkflowStep.REQUEST_ANALYSIS);
            stateRepository.save(state);
            eventPublisher.publishEvent(new StudyCreatedEvent(workflowId, study));

            // 3. Triton 분석 요청
            log.info("Workflow {}: Requesting Triton analysis", workflowId);
            jobId = analysisActivity.requestAnalysis(study.getId());
            state.setCurrentStep(WorkflowStep.WAIT_RESULT);
            stateRepository.save(state);

            // 4. 분석 결과 대기 (폴링)
            log.info("Workflow {}: Waiting for analysis result", workflowId);
            analysisResult = analysisActivity.waitForResult(jobId);
            state.setCurrentStep(WorkflowStep.GENERATE_REPORT);
            stateRepository.save(state);
            eventPublisher.publishEvent(new AnalysisCompletedEvent(workflowId, analysisResult));

            // 5. 리포트 생성
            log.info("Workflow {}: Generating report", workflowId);
            report = reportActivity.generateReport(analysisResult);

            // 워크플로우 완료
            state.setStatus(WorkflowStatus.COMPLETED);
            state.setCompletedAt(Instant.now());
            stateRepository.save(state);
            eventPublisher.publishEvent(new WorkflowCompletedEvent(workflowId, report));

            log.info("Workflow {} completed successfully", workflowId);
            return analysisResult;

        } catch (Exception e) {
            log.error("Workflow {} failed at step {}", workflowId, state.getCurrentStep(), e);

            // 수동 보상 트랜잭션 (Saga)
            compensate(state.getCurrentStep(), uploadResult, study, jobId, report);

            state.setStatus(WorkflowStatus.FAILED);
            state.setErrorMessage(e.getMessage());
            stateRepository.save(state);

            throw new WorkflowException(workflowId, state.getCurrentStep(),
                "Workflow execution failed", e);
        }
    }

    /**
     * 수동 보상 트랜잭션 (Saga Pattern)
     * - 실패한 단계에 따라 이전 단계들을 역순으로 보상
     */
    private void compensate(WorkflowStep failedStep,
                           FileUploadResult uploadResult,
                           Study study,
                           String jobId,
                           Report report) {
        log.warn("Compensating workflow, failed at step: {}", failedStep);

        try {
            // 역순으로 보상
            switch (failedStep) {
                case GENERATE_REPORT:
                    if (jobId != null) {
                        analysisActivity.compensateAnalysis(jobId);
                    }
                    // fall through
                case WAIT_RESULT:
                case REQUEST_ANALYSIS:
                    if (study != null) {
                        studyActivity.compensateStudy(study.getId());
                    }
                    // fall through
                case CREATE_STUDY:
                    if (uploadResult != null) {
                        uploadActivity.compensateUpload(uploadResult.getFileId());
                    }
                    // fall through
                case UPLOAD:
                    // 첫 단계 실패, 보상 불필요
                    break;
            }
        } catch (Exception e) {
            log.error("Compensation failed", e);
            // 보상 실패 시 알림 (운영팀 개입 필요)
            eventPublisher.publishEvent(new CompensationFailedEvent(e));
        }
    }

    @Override
    public WorkflowStatus getStatus(String workflowId) {
        return stateRepository.findById(workflowId)
            .map(WorkflowState::getStatus)
            .orElseThrow(() -> new WorkflowNotFoundException(workflowId));
    }

    @Override
    public void cancel(String workflowId) {
        WorkflowState state = stateRepository.findById(workflowId)
            .orElseThrow(() -> new WorkflowNotFoundException(workflowId));

        if (state.getStatus() == WorkflowStatus.RUNNING) {
            state.setStatus(WorkflowStatus.CANCELLED);
            stateRepository.save(state);

            // 현재 진행 중인 작업 취소 로직
            // (Simple 버전에서는 제한적)
        }
    }
}
```

---

#### 1.2 Activity 구현 (Spring의 기본 기능 활용)

```java
@Service
@Slf4j
public class TritonAnalysisActivityImpl implements TritonAnalysisActivity {

    @Autowired
    private TritonClient tritonClient;

    @Autowired
    private AnalysisJobRepository jobRepository;

    @Override
    public String requestAnalysis(String studyId) {
        String jobId = tritonClient.submitAnalysisJob(studyId);

        AnalysisJob job = AnalysisJob.builder()
            .jobId(jobId)
            .studyId(studyId)
            .status(AnalysisJobStatus.SUBMITTED)
            .submittedAt(Instant.now())
            .build();
        jobRepository.save(job);

        return jobId;
    }

    @Override
    @Retryable(
        value = {AnalysisTimeoutException.class},
        maxAttempts = 3,
        backoff = @Backoff(delay = 5000)
    )
    public AnalysisResult waitForResult(String jobId) {
        // 폴링 방식으로 결과 대기 (최대 10분)
        int maxAttempts = 120;  // 10분 / 5초
        int attempt = 0;

        while (attempt < maxAttempts) {
            AnalysisJob job = jobRepository.findById(jobId)
                .orElseThrow(() -> new JobNotFoundException(jobId));

            // Triton 서버에 상태 조회
            AnalysisJobStatus status = tritonClient.getJobStatus(jobId);
            job.setStatus(status);
            jobRepository.save(job);

            if (status == AnalysisJobStatus.COMPLETED) {
                AnalysisResult result = tritonClient.getResult(jobId);
                log.info("Analysis job {} completed", jobId);
                return result;
            } else if (status == AnalysisJobStatus.FAILED) {
                throw new AnalysisFailedException("Job " + jobId + " failed");
            }

            // 5초 대기
            try {
                Thread.sleep(5000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AnalysisInterruptedException("Polling interrupted", e);
            }

            attempt++;
        }

        throw new AnalysisTimeoutException("Job " + jobId + " timed out after 10 minutes");
    }

    @Override
    public void compensateAnalysis(String jobId) {
        log.warn("Compensating analysis job: {}", jobId);

        try {
            tritonClient.cancelJob(jobId);

            AnalysisJob job = jobRepository.findById(jobId)
                .orElseThrow(() -> new JobNotFoundException(jobId));
            job.setStatus(AnalysisJobStatus.CANCELLED);
            jobRepository.save(job);

        } catch (Exception e) {
            log.error("Failed to compensate analysis job: {}", jobId, e);
            // 보상 실패는 별도 처리 (운영팀 알림)
        }
    }
}
```

---

#### 1.3 워크플로우 상태 관리

```java
@Entity
@Table(name = "workflow_state")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowState {

    @Id
    private String workflowId;

    @Enumerated(EnumType.STRING)
    private WorkflowStatus status;

    @Enumerated(EnumType.STRING)
    private WorkflowStep currentStep;

    private String studyId;

    private String errorMessage;

    private Instant createdAt;

    private Instant completedAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
```

---

### Phase 1의 한계점 (직접 체감할 것들)

#### 1. 수동 보상 트랜잭션의 복잡도
```java
// 문제: 보상 로직을 수동으로 작성해야 함
// - 각 단계마다 try-catch
// - 보상 순서 관리
// - 보상 실패 시 처리 어려움

private void compensate(...) {
    // 이 코드를 매번 작성하는 것은 고통스러움
    // Temporal은 이것을 자동화해줌!
}
```

#### 2. 재시도 로직의 한계
```java
// 문제: @Retryable로는 부족
// - 재시도 상태를 DB에 저장 불가
// - 애플리케이션 재시작 시 재시도 상태 소실
// - 복잡한 재시도 전략 구현 어려움

@Retryable(maxAttempts = 3)  // 이것만으로는 부족
public AnalysisResult waitForResult(String jobId) {
    // 앱이 재시작되면?
    // 재시도 중간 상태는 어떻게 관리?
}
```

#### 3. 장기 실행 작업의 어려움
```java
// 문제: Thread.sleep()은 비효율적
// - 10분 동안 스레드 점유
// - 동시 요청 많으면 스레드 풀 고갈

while (attempt < maxAttempts) {
    Thread.sleep(5000);  // 이것은 리소스 낭비
    // Temporal은 Timer로 해결
}
```

#### 4. 워크플로우 재시작 불가
```java
// 문제: 애플리케이션 재시작 시
// - 진행 중인 워크플로우 복구 어려움
// - 어디까지 진행했는지 DB에서 조회해야 함
// - 중간부터 재시작 로직 복잡

// Temporal은 워크플로우 히스토리로 자동 복구
```

---

## 📦 구현 2: Temporal Workflow

### Phase 3 (Week 9-10): Temporal로 전환

#### 2.1 Temporal Workflow 구현

```java
@WorkflowImpl(taskQueues = "dicom-analysis")
@Slf4j
public class TemporalDicomAnalysisWorkflow implements DicomAnalysisWorkflow {

    // Activity Stub (Temporal이 프록시 생성)
    private final DicomUploadActivity uploadActivity = Workflow.newActivityStub(
        DicomUploadActivity.class,
        ActivityOptions.newBuilder()
            .setStartToCloseTimeout(Duration.ofMinutes(5))
            .setRetryOptions(RetryOptions.newBuilder()
                .setMaximumAttempts(3)
                .setBackoffCoefficient(2.0)
                .build())
            .build()
    );

    private final StudyCreationActivity studyActivity = Workflow.newActivityStub(
        StudyCreationActivity.class,
        ActivityOptions.newBuilder()
            .setStartToCloseTimeout(Duration.ofMinutes(2))
            .build()
    );

    private final TritonAnalysisActivity analysisActivity = Workflow.newActivityStub(
        TritonAnalysisActivity.class,
        ActivityOptions.newBuilder()
            .setStartToCloseTimeout(Duration.ofMinutes(30))  // AI 분석은 길 수 있음
            .setHeartbeatTimeout(Duration.ofMinutes(1))
            .build()
    );

    private final ReportGenerationActivity reportActivity = Workflow.newActivityStub(
        ReportGenerationActivity.class,
        ActivityOptions.newBuilder()
            .setStartToCloseTimeout(Duration.ofMinutes(5))
            .build()
    );

    @Override
    @WorkflowMethod
    public AnalysisResult execute(StudyRequest request) {
        String workflowId = Workflow.getInfo().getWorkflowId();

        // Saga 패턴 (Temporal이 자동 관리)
        Saga saga = new Saga(new Saga.Options.Builder()
            .setParallelCompensation(false)  // 순차적 보상
            .build());

        try {
            // 1. DICOM 업로드 (자동 보상)
            log.info("Workflow {}: Starting DICOM upload", workflowId);
            FileUploadResult uploadResult = saga.addCompensation(
                () -> uploadActivity.compensateUpload(uploadResult.getFileId()),
                () -> uploadActivity.validateAndUpload(request)
            );

            // 2. Study 생성 (자동 보상)
            log.info("Workflow {}: Creating Study", workflowId);
            Study study = saga.addCompensation(
                () -> studyActivity.compensateStudy(study.getId()),
                () -> studyActivity.createStudy(uploadResult)
            );

            // 3. Triton 분석 요청 (자동 보상)
            log.info("Workflow {}: Requesting Triton analysis", workflowId);
            String jobId = saga.addCompensation(
                () -> analysisActivity.compensateAnalysis(jobId),
                () -> analysisActivity.requestAnalysis(study.getId())
            );

            // 4. 분석 결과 대기 (Temporal Timer 사용)
            log.info("Workflow {}: Waiting for analysis result", workflowId);
            AnalysisResult analysisResult = Workflow.await(
                Duration.ofMinutes(30),  // 최대 30분 대기
                () -> analysisActivity.waitForResult(jobId)
            );

            // 5. 리포트 생성 (자동 보상)
            log.info("Workflow {}: Generating report", workflowId);
            Report report = saga.addCompensation(
                () -> reportActivity.compensateReport(report.getId()),
                () -> reportActivity.generateReport(analysisResult)
            );

            log.info("Workflow {} completed successfully", workflowId);
            return analysisResult;

        } catch (Exception e) {
            log.error("Workflow {} failed", workflowId, e);
            saga.compensate();  // 자동 보상 (역순으로 실행)
            throw e;
        }
    }

    @Override
    public WorkflowStatus getStatus(String workflowId) {
        // Temporal이 워크플로우 상태 관리
        WorkflowExecution execution = WorkflowExecution.newBuilder()
            .setWorkflowId(workflowId)
            .build();

        DescribeWorkflowExecutionResponse response =
            Workflow.getWorkflowClient().describeWorkflowExecution(execution);

        return convertStatus(response.getWorkflowExecutionInfo().getStatus());
    }

    @Override
    @SignalMethod
    public void cancel(String workflowId) {
        // Temporal Signal로 취소
        Workflow.getWorkflowClient().newUntypedWorkflowStub(workflowId).cancel();
    }
}
```

---

#### 2.2 Activity 구현 (Temporal Activity로 변환)

```java
@Component
@Slf4j
public class TritonAnalysisActivityImpl implements TritonAnalysisActivity {

    @Autowired
    private TritonClient tritonClient;

    @Autowired
    private AnalysisJobRepository jobRepository;

    @Override
    @ActivityMethod
    public String requestAnalysis(String studyId) {
        // 동일한 로직 (Simple 버전과 동일)
        String jobId = tritonClient.submitAnalysisJob(studyId);
        // ...
        return jobId;
    }

    @Override
    @ActivityMethod
    public AnalysisResult waitForResult(String jobId) {
        // Temporal의 Heartbeat 활용
        int maxAttempts = 120;
        int attempt = 0;

        while (attempt < maxAttempts) {
            // Heartbeat (Temporal에게 "살아있음" 알림)
            Activity.getExecutionContext().heartbeat(attempt);

            AnalysisJob job = jobRepository.findById(jobId)
                .orElseThrow(() -> new JobNotFoundException(jobId));

            AnalysisJobStatus status = tritonClient.getJobStatus(jobId);
            job.setStatus(status);
            jobRepository.save(job);

            if (status == AnalysisJobStatus.COMPLETED) {
                return tritonClient.getResult(jobId);
            } else if (status == AnalysisJobStatus.FAILED) {
                throw new AnalysisFailedException("Job " + jobId + " failed");
            }

            // Temporal의 sleep (스레드 점유 없음!)
            Workflow.sleep(Duration.ofSeconds(5));

            attempt++;
        }

        throw new AnalysisTimeoutException("Job " + jobId + " timed out");
    }

    @Override
    @ActivityMethod
    public void compensateAnalysis(String jobId) {
        // 동일한 로직 (Simple 버전과 동일)
        log.warn("Compensating analysis job: {}", jobId);
        tritonClient.cancelJob(jobId);
        // ...
    }
}
```

---

## 🔄 전환 전략

### 단계별 마이그레이션

#### Step 1: 인터페이스 유지 (Week 4-7)
```java
// Simple 구현으로 개발
@Service
@Primary  // 기본 구현체
public class SimpleDicomAnalysisWorkflow implements DicomAnalysisWorkflow {
    // ...
}
```

#### Step 2: Temporal 환경 구성 (Week 8)
```yaml
# docker-compose.yml에 Temporal 추가
temporal:
  image: temporalio/auto-setup:latest
  ports:
    - "7233:7233"  # Temporal Server
    - "8080:8080"  # Temporal UI
  environment:
    - DB=postgresql
    - DB_PORT=5432
    - POSTGRES_USER=temporal
    - POSTGRES_PWD=temporal
    - POSTGRES_SEEDS=postgres
```

#### Step 3: Temporal 구현체 추가 (Week 9)
```java
// Temporal 구현 추가
@Service
@ConditionalOnProperty(name = "workflow.engine", havingValue = "temporal")
public class TemporalDicomAnalysisWorkflow implements DicomAnalysisWorkflow {
    // ...
}
```

#### Step 4: A/B 테스트 (Week 10)
```java
// 설정으로 전환 가능
@Configuration
public class WorkflowConfig {

    @Bean
    @ConditionalOnProperty(name = "workflow.engine", havingValue = "simple", matchIfMissing = true)
    public DicomAnalysisWorkflow simpleWorkflow() {
        return new SimpleDicomAnalysisWorkflow();
    }

    @Bean
    @ConditionalOnProperty(name = "workflow.engine", havingValue = "temporal")
    public DicomAnalysisWorkflow temporalWorkflow() {
        return new TemporalDicomAnalysisWorkflow();
    }
}
```

```yaml
# application.yml
workflow:
  engine: simple  # 또는 temporal
```

---

## 📊 비교표: Simple vs Temporal

| 기능 | Simple 구현 | Temporal 구현 | 학습 효과 |
|------|------------|--------------|-----------|
| **보상 트랜잭션** | 수동 구현 (복잡) | 자동 (Saga) | ⭐⭐⭐⭐⭐ Temporal의 가치 체감 |
| **재시도** | @Retryable (제한적) | Temporal Retry (강력) | ⭐⭐⭐⭐ 자동 재시도의 편리함 |
| **장기 대기** | Thread.sleep (비효율) | Workflow.sleep (효율) | ⭐⭐⭐⭐⭐ 리소스 절약 체감 |
| **워크플로우 재시작** | 수동 복구 (어려움) | 자동 복구 (History) | ⭐⭐⭐⭐⭐ 신뢰성 향상 |
| **타임아웃** | 수동 관리 | Temporal Timeout | ⭐⭐⭐⭐ 선언적 설정의 편리함 |
| **모니터링** | 커스텀 구현 필요 | Temporal UI 제공 | ⭐⭐⭐⭐ 가시성 향상 |
| **학습 곡선** | 낮음 | 높음 | ⭐⭐⭐⭐⭐ 점진적 학습 |

---

## 📝 수정된 16주 계획

### Week 4-7: Simple 구현 (Temporal 없이)
- [x] Week 4: 첫 E2E 플로우 (Simple Workflow)
- [ ] Week 5-7: Kafka + Simple Workflow
  - Kafka 이벤트 발행
  - Simple Workflow로 이벤트 컨슈밍
  - **수동 보상 트랜잭션 구현** (Saga의 어려움 체감)
  - **폴링 방식의 한계** 체감

**Week 7 후 회고**:
- "수동 보상 트랜잭션이 이렇게 복잡하구나"
- "장기 실행 작업이 이렇게 어렵구나"
- "Temporal이 왜 필요한지 이제 알겠다"

---

### Week 8: Temporal 학습 (전환 준비)
**학습 목표**:
- Temporal 아키텍처 이해
- Hello World Workflow 실습
- Activity, Signal, Timer 학습
- **Simple 구현의 한계점과 Temporal 해결 방법 비교**

**실습**:
- [ ] Temporal 로컬 환경 구성
- [ ] 간단한 Workflow 작성 (주문 처리 예제)
- [ ] Activity 재시도 및 타임아웃 실험
- [ ] Temporal UI로 워크플로우 모니터링

**산출물**:
- `docs/learning/week08-temporal-basics.md`
- **"Simple vs Temporal 비교" 섹션 작성**

---

### Week 9: Temporal 전환
**학습 목표**:
- TemporalDicomAnalysisWorkflow 구현
- 기존 Activity 인터페이스 재사용
- Saga 자동화

**실습**:
- [ ] TemporalDicomAnalysisWorkflow 구현
- [ ] Activity를 @ActivityMethod로 전환
- [ ] Saga 패턴 적용 (자동 보상)
- [ ] Workflow.sleep()으로 폴링 개선

**산출물**:
- Temporal 구현체 완성
- `docs/learning/week09-temporal-saga.md`

---

### Week 10: A/B 테스트 및 최종 선택
**학습 목표**:
- Simple vs Temporal 성능 비교
- 모니터링 및 디버깅 경험

**실습**:
- [ ] 동일한 워크플로우를 Simple/Temporal로 실행
- [ ] 성능 비교 (리소스, 처리 시간)
- [ ] Temporal UI로 워크플로우 추적
- [ ] **최종 선택: Temporal 채택 결정**

**산출물**:
- 비교 분석 문서
- `docs/learning/week10-temporal-migration.md`

---

## 🎓 학습 체크리스트

### Phase 1 (Simple 구현) 학습 목표
- [ ] Saga 패턴을 수동으로 구현해봄
- [ ] 보상 트랜잭션의 복잡도 체감
- [ ] 재시도 로직의 한계 이해
- [ ] 장기 실행 작업의 어려움 체감
- [ ] 워크플로우 상태 관리의 어려움 이해

### Phase 2 (Temporal 학습) 학습 목표
- [ ] Temporal 아키텍처 이해
- [ ] Workflow와 Activity 개념 이해
- [ ] Temporal의 핵심 가치 이해 (자동 재시도, 보상, 타임아웃)

### Phase 3 (Temporal 전환) 학습 목표
- [ ] 기존 코드를 최소 변경으로 전환
- [ ] Temporal Saga의 편리함 체감
- [ ] Workflow.sleep()의 효율성 체감
- [ ] Temporal UI로 가시성 향상 체감

---

## 💡 핵심 인사이트

1. **"왜 Temporal인가?"를 몸으로 체감**
   - Simple 구현의 고통을 먼저 경험
   - Temporal이 얼마나 편한지 실감

2. **점진적 마이그레이션 전략**
   - 인터페이스 추상화로 언제든 전환 가능
   - 실무에서도 이런 방식으로 도입

3. **학습 곡선 완화**
   - Temporal의 복잡한 개념을 한 번에 배우지 않음
   - 필요성을 느낀 후 학습하면 이해 빠름

4. **유연성 확보**
   - Temporal이 과하다고 판단되면 Simple 유지 가능
   - 프로젝트 특성에 맞게 선택

---

**최종 업데이트**: 2025-12-20
**다음 업데이트**: Week 7 Simple 구현 완료 후
