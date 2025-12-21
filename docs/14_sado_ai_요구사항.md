# sado_ai 요구사항 명세

> **문서 역할**: sado_ai 프로젝트 요구사항 및 구현 가이드
>
> **최종 업데이트**: 2025-12-21
>
> **관련 문서**:
> - `07_최종_구현_계획.md` - Week 10 sado_ai 연동
> - `09_Temporal_점진적_도입_전략.md` - Orchestrator에서 AI 연동

---

## 1. 프로젝트 개요

### 1.1 SADO 프로젝트 전체 구조

```
sado/
├── sado_be/    # Spring 멀티모듈 백엔드 + 통합 문서관리
├── sado_fe/    # React 프론트엔드
└── sado_ai/    # Triton AI 분석 서버 (심장 초음파) ⭐
```

### 1.2 sado_ai 목적

| 항목 | 설명 |
|------|------|
| **목적** | 심장 초음파 DICOM 파일 AI 분석 |
| **역할** | Triton 서버로 AI 분석 수행, 결과 데이터 제공 |
| **성격** | Mock 서비스 (학습용, 분석 정확도 중요하지 않음) |
| **원칙** | 최대한 간단한 구성 |

### 1.3 sado_ai와 sado_be 관계

```
[sado_be]                          [sado_ai]
    │                                   │
MiniPACS ──DICOM 저장───→              │
    │                                   │
Orchestrator ──gRPC 분석 요청──→ Triton Server
    │                                   │
    │ ←──────분석 결과 응답────────────┘
    │
    ▼
리포트 생성 (sado_be에서 처리)
```

---

## 2. 기술 스택

### 2.1 핵심 기술

| 기술 | 용도 | 비고 |
|------|------|------|
| **NVIDIA Triton Inference Server** | AI 모델 서빙 | gRPC/HTTP 인터페이스 제공 |
| **Python** | 전처리/후처리 | DICOM → 이미지 변환 |
| **gRPC** | 통신 프로토콜 | Triton 네이티브 인터페이스 |
| **Docker** | 컨테이너화 | Triton 공식 이미지 활용 |

### 2.2 Triton Inference Server

**Triton이란?**
- NVIDIA에서 제공하는 오픈소스 AI 모델 서빙 플랫폼
- 다양한 프레임워크 지원 (TensorFlow, PyTorch, ONNX, TensorRT 등)
- gRPC/HTTP 인터페이스 기본 제공
- 동적 배치, 모델 앙상블 등 고급 기능

**선택 이유**:
- gRPC 인터페이스 기본 제공
- Docker 이미지로 간단한 배포
- 오픈소스 모델 배포 용이
- 프로덕션 레벨 안정성

---

## 3. 오픈소스 모델 후보

### 3.1 EchoNet-Dynamic (추천)

**프로젝트**: Stanford Machine Learning Group
**GitHub**: https://github.com/echonet/dynamic
**논문**: "Video-based AI for beat-to-beat assessment of cardiac function"

**기능**:
- 심장 초음파 영상에서 좌심실 박출률(EF) 예측
- 좌심실 세그멘테이션
- A4C (Apical 4-Chamber) 뷰 분석

**출력**:
- EF (Ejection Fraction): 0-100% 범위의 float 값
- 세그멘테이션 마스크: 프레임별 좌심실 영역

**장점**:
- 잘 문서화된 코드베이스
- 사전 학습된 가중치 제공
- PyTorch 기반으로 ONNX 변환 용이

### 3.2 기타 후보 모델

| 모델 | 기능 | 비고 |
|------|------|------|
| **UNet 심장 세그멘테이션** | 심장 구조 분할 | 간단한 세그멘테이션 |
| **ResNet 심장 기능 분류** | 정상/비정상 분류 | 단순 분류 모델 |

### 3.3 모델 선택 기준

Mock 서비스이므로:
- **간단한 구성**: 복잡한 앙상블 피함
- **빠른 추론**: 실시간 분석 불필요하나 합리적인 응답 시간
- **문서화**: 배포 가이드가 잘 되어 있는 모델

---

## 4. 입출력 정의

### 4.1 입력

**Study 단위 DICOM 파일**

```
입력 데이터 구조:
├── study_id: string (Study UID)
└── dicom_files: []
    ├── file_path: string (SeaweedFS 경로)
    ├── sop_instance_uid: string
    └── frame_count: int (멀티프레임인 경우)
```

**DICOM 전처리**:
1. DICOM 파일 읽기 (pydicom)
2. 픽셀 데이터 추출
3. 정규화 (0-1 범위)
4. 리사이즈 (모델 입력 크기)
5. 텐서 변환

### 4.2 출력

**분석 결과 스키마**

```json
{
  "study_id": "1.2.3.4.5",
  "analysis_timestamp": "2025-12-21T10:30:00Z",
  "status": "completed",

  "metrics": {
    "ef": {
      "value": 55.2,
      "unit": "percent",
      "description": "Left Ventricular Ejection Fraction"
    },
    "edv": {
      "value": 120.5,
      "unit": "ml",
      "description": "End-Diastolic Volume"
    },
    "esv": {
      "value": 54.0,
      "unit": "ml",
      "description": "End-Systolic Volume"
    }
  },

  "segmentation": {
    "frame_count": 30,
    "images": [
      {
        "frame_index": 0,
        "image_base64": "iVBORw0KGgo...",
        "format": "png"
      }
    ]
  }
}
```

### 4.3 지표 설명

| 지표 | 설명 | 정상 범위 |
|------|------|----------|
| **EF (Ejection Fraction)** | 좌심실 박출률, 심장 수축 능력 | 55-70% |
| **EDV (End-Diastolic Volume)** | 이완기말 용적 | 65-240ml (남성) |
| **ESV (End-Systolic Volume)** | 수축기말 용적 | 16-100ml (남성) |

---

## 5. gRPC 인터페이스

### 5.1 Triton gRPC 인터페이스

Triton은 표준 gRPC 인터페이스를 제공합니다.

**Proto 정의** (Triton 표준):
```protobuf
service GRPCInferenceService {
  rpc ModelInfer(ModelInferRequest) returns (ModelInferResponse) {}
  rpc ModelMetadata(ModelMetadataRequest) returns (ModelMetadataResponse) {}
  rpc ServerLive(ServerLiveRequest) returns (ServerLiveResponse) {}
}
```

### 5.2 sado_be에서 호출 방법

**Java gRPC 클라이언트 (sado-orchestrator)**:

```java
// Triton gRPC 클라이언트 설정
@Configuration
public class TritonGrpcConfig {

    @Value("${sado.ai.triton.host}")
    private String tritonHost;

    @Value("${sado.ai.triton.port}")
    private int tritonPort;

    @Bean
    public ManagedChannel tritonChannel() {
        return ManagedChannelBuilder
            .forAddress(tritonHost, tritonPort)
            .usePlaintext()
            .build();
    }

    @Bean
    public GRPCInferenceServiceGrpc.GRPCInferenceServiceBlockingStub tritonStub(
            ManagedChannel channel) {
        return GRPCInferenceServiceGrpc.newBlockingStub(channel);
    }
}
```

**분석 요청 서비스**:

```java
@Service
public class TritonAnalysisService {

    private final GRPCInferenceServiceBlockingStub tritonStub;

    public AnalysisResult analyzeStudy(String studyId, List<DicomFile> files) {
        // 1. DICOM 파일을 이미지로 변환 (sado_ai 전처리 서비스 호출)
        byte[] inputTensor = preprocessDicomFiles(files);

        // 2. Triton 추론 요청
        ModelInferRequest request = buildInferRequest(inputTensor);
        ModelInferResponse response = tritonStub.modelInfer(request);

        // 3. 결과 파싱
        return parseAnalysisResult(response);
    }
}
```

### 5.3 설정 예시

```yaml
# application.yml (sado-orchestrator)
sado:
  ai:
    triton:
      host: localhost
      port: 8001
      model-name: echonet-dynamic
      timeout-seconds: 60
```

---

## 6. sado_be 연동 플로우

### 6.1 전체 플로우

```
1. DICOM 업로드
   └── MiniPACS: DICOM 파일 저장 (SeaweedFS)
   └── Kafka: StudyUploadCompletedEvent 발행

2. 분석 요청
   └── Orchestrator: Kafka 이벤트 수신
   └── Orchestrator: Temporal Workflow 시작

3. AI 분석 (Temporal Activity)
   └── RequestAnalysisActivity: sado_ai gRPC 호출
   └── WaitForAnalysisActivity: 결과 대기 (최대 5분)

4. 결과 처리
   └── SaveResultActivity: 분석 결과 저장 (DB)
   └── GenerateReportActivity: 리포트 생성

5. 리포트 제공
   └── BFF: 리포트 조회 API
   └── Frontend: 리포트 표시
```

### 6.2 Temporal Workflow 연동

```java
// DicomAnalysisWorkflow.java (sado-orchestrator)
@WorkflowInterface
public interface DicomAnalysisWorkflow {

    @WorkflowMethod
    AnalysisResult analyzeStudy(String studyId);
}

@WorkflowImpl
public class DicomAnalysisWorkflowImpl implements DicomAnalysisWorkflow {

    private final AnalysisActivities activities =
        Workflow.newActivityStub(AnalysisActivities.class);

    @Override
    public AnalysisResult analyzeStudy(String studyId) {
        // 1. DICOM 검증
        activities.validateDicom(studyId);

        // 2. AI 분석 요청 (sado_ai gRPC 호출)
        String analysisId = activities.requestAnalysis(studyId);

        // 3. 결과 대기
        AnalysisResult result = activities.waitForAnalysis(analysisId);

        // 4. 결과 저장
        activities.saveResult(studyId, result);

        // 5. 리포트 생성
        activities.generateReport(studyId, result);

        return result;
    }
}
```

---

## 7. 개발 환경 설정

### 7.1 Docker Compose

```yaml
# docker-compose.yml (sado 루트)
version: '3.8'

services:
  # Triton Inference Server
  triton:
    image: nvcr.io/nvidia/tritonserver:24.01-py3
    container_name: sado-triton
    ports:
      - "8000:8000"  # HTTP
      - "8001:8001"  # gRPC
      - "8002:8002"  # Metrics
    volumes:
      - ./sado_ai/models:/models
    command: ["tritonserver", "--model-repository=/models"]
    # GPU 사용 시 주석 해제
    # deploy:
    #   resources:
    #     reservations:
    #       devices:
    #         - driver: nvidia
    #           count: 1
    #           capabilities: [gpu]
    networks:
      - sado-network

networks:
  sado-network:
    driver: bridge
```

### 7.2 모델 디렉토리 구조

```
sado_ai/
├── models/                      # Triton 모델 저장소
│   └── echonet-dynamic/
│       ├── 1/                   # 모델 버전 1
│       │   └── model.onnx       # ONNX 모델 파일
│       └── config.pbtxt         # 모델 설정
├── preprocessing/               # 전처리 스크립트
│   ├── dicom_to_tensor.py
│   └── requirements.txt
├── postprocessing/              # 후처리 스크립트
│   ├── parse_results.py
│   └── generate_segmentation.py
└── docker-compose.yml           # sado_ai 전용 컴포즈
```

### 7.3 모델 설정 파일

```protobuf
# config.pbtxt (echonet-dynamic)
name: "echonet-dynamic"
platform: "onnxruntime_onnx"
max_batch_size: 1

input [
  {
    name: "input"
    data_type: TYPE_FP32
    dims: [ 3, 112, 112, 32 ]  # C, H, W, T (프레임 수)
  }
]

output [
  {
    name: "ef_output"
    data_type: TYPE_FP32
    dims: [ 1 ]
  },
  {
    name: "segmentation_output"
    data_type: TYPE_FP32
    dims: [ 112, 112, 32 ]
  }
]

instance_group [
  {
    count: 1
    kind: KIND_CPU  # GPU 사용 시: KIND_GPU
  }
]
```

---

## 8. 개발 일정 (Week 10)

### 8.1 Week 10 상세 계획

**기존 Week 10 목표** (Temporal 고급):
- Workflow 버전 관리
- Activity 병렬 실행 최적화
- Temporal 메트릭

**추가 목표** (sado_ai 연동):

| 일자 | 작업 | 산출물 |
|------|------|--------|
| Day 1-2 | Triton Docker Compose 설정 | docker-compose.yml |
| Day 3 | 오픈소스 모델 ONNX 변환 | model.onnx, config.pbtxt |
| Day 4 | gRPC 클라이언트 구현 (Java) | TritonAnalysisService.java |
| Day 5 | Temporal Activity 연동 | RequestAnalysisActivity.java |
| Day 6-7 | E2E 테스트 및 문서화 | 통합 테스트, 학습 노트 |

### 8.2 실습 체크리스트

- [ ] Triton Docker Compose 추가 및 실행 확인
- [ ] EchoNet-Dynamic 모델 다운로드 및 ONNX 변환
- [ ] Triton 모델 배포 및 Health Check
- [ ] Java gRPC 클라이언트 구현 (sado-orchestrator)
- [ ] 더미 DICOM으로 분석 요청 테스트
- [ ] Temporal Activity에서 Triton 호출
- [ ] 분석 결과 파싱 및 저장
- [ ] E2E 플로우 테스트 (DICOM 업로드 → 분석 → 결과 저장)

---

## 9. 제한사항 및 참고

### 9.1 제한사항

| 항목 | 설명 |
|------|------|
| **분석 정확도** | 중요하지 않음 (Mock 서비스) |
| **모델 학습** | 직접 학습하지 않음 (오픈소스 활용) |
| **GPU** | 필수 아님 (CPU 추론 가능) |
| **실시간 분석** | 불필요 (배치 처리) |

### 9.2 향후 확장 가능성

학습 프로젝트 완료 후 고려할 수 있는 확장:
- 실제 학습된 모델로 교체
- GPU 추론 최적화
- 모델 앙상블
- 실시간 스트리밍 분석

### 9.3 참고 자료

**Triton**:
- [Triton Inference Server 문서](https://github.com/triton-inference-server/server)
- [Triton gRPC API](https://github.com/triton-inference-server/common/tree/main/protobuf)

**EchoNet-Dynamic**:
- [GitHub 저장소](https://github.com/echonet/dynamic)
- [논문](https://www.nature.com/articles/s41586-020-2145-8)

**DICOM 처리**:
- [pydicom](https://pydicom.github.io/)
- [dcm4che](https://www.dcm4che.org/) (Java)

---

## 10. 문서 이력

| 날짜 | 변경 내용 | 작성자 |
|------|-----------|--------|
| 2025-12-21 | 최초 작성 | Claude |
