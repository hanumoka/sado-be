# [Week 1] 공통 모듈 설계 - ApiResponse 표준화 패턴

> 작성일: 2025-12-26
> 카테고리: 아키텍처 설계
> 관련 기술: Spring Boot, REST API, Exception Handling, Design Pattern
> Week: 1 (Phase 1 - 기반 구축)

## TL;DR

- **문제**: API 응답 형식이 컨트롤러마다 다르면 프론트엔드 통합이 어렵고, 에러 처리가 일관성 없음
- **해결**: ApiResponse 표준 포맷 + ApiCode Enum + GlobalExceptionHandler로 응답 통일
- **핵심**: 모든 성공/실패 응답을 동일한 구조로 반환하여 클라이언트 개발 편의성 극대화
- **참조**: 실무 프로젝트(kingarthur) 패턴 분석 및 SADO 프로젝트 적용

---

## 들어가며

Week 1에서 Gradle 멀티모듈 프로젝트를 구성한 후, 가장 먼저 설계해야 할 것은 **공통 모듈(sado-common)**입니다. 특히 REST API 응답 표준화는 프로젝트 초기에 확립해야 이후 모든 모듈에서 일관되게 사용할 수 있습니다.

### 이번 글에서 다룰 내용

1. **왜 API 응답 표준화가 필요한가?**
2. **ApiResponse 패턴의 핵심 구조**
3. **kingarthur 프로젝트 분석** (실무 사례)
4. **SADO 프로젝트 적용 계획**
5. **구현 시 주의사항**

---

## 배경: 왜 API 응답 표준화가 필요한가?

### 문제 상황 1: 비일관적인 응답 형식

**표준화 전:**

```java
// 컨트롤러 A
@GetMapping("/users/{id}")
public User getUser(@PathVariable Long id) {
    return userService.findById(id);  // User 객체 직접 반환
}

// 컨트롤러 B
@GetMapping("/studies/{id}")
public Map<String, Object> getStudy(@PathVariable Long id) {
    return Map.of("study", studyService.findById(id), "status", "success");
}

// 컨트롤러 C - 예외 발생 시
@GetMapping("/series/{id}")
public Series getSeries(@PathVariable Long id) {
    return seriesService.findById(id);  // 없으면 500 에러
}
```

**문제점:**
- 응답 형식이 컨트롤러마다 다름 (User 객체, Map, 에러 시 기본 에러 페이지)
- 성공/실패 구분이 명확하지 않음
- 클라이언트는 각 API마다 다른 파싱 로직 필요
- 에러 응답이 Spring 기본 형식이라 사용자 친화적이지 않음

### 문제 상황 2: 예외 처리의 어려움

```java
// 예외 발생 시 Spring 기본 에러 응답
{
  "timestamp": "2025-12-26T12:00:00.000+00:00",
  "status": 500,
  "error": "Internal Server Error",
  "path": "/api/studies/123"
}
```

**문제점:**
- 에러 코드가 없어서 프론트엔드에서 조건 분기 어려움
- 메시지가 기술적이라 사용자에게 노출하기 부적절
- 추가 정보(traceId 등) 제공 불가

### 해결책: API 응답 표준화

**표준화 후:**

```java
// 모든 컨트롤러가 동일한 형식 반환
@GetMapping("/users/{id}")
public ApiResponse<User> getUser(@PathVariable Long id) {
    User user = userService.findById(id);
    return ApiResponse.success(user);
}

// 성공 응답
{
  "type": "SUCCESS",
  "code": 200000,
  "message": "Success",
  "data": {
    "id": 1,
    "name": "홍길동"
  },
  "traceId": "abc-123",
  "timestamp": 1703577600000,
  "path": "/api/users/1"
}

// 실패 응답 (자동 처리)
{
  "type": "RESOURCE_NOT_FOUND",
  "code": 404001,
  "message": "Resource not found",
  "data": null,
  "traceId": "def-456",
  "timestamp": 1703577600000,
  "path": "/api/users/999"
}
```

**장점:**
- ✅ 모든 API 응답 형식 통일
- ✅ 성공/실패 명확히 구분 (`code`, `type`)
- ✅ 에러 코드로 프론트엔드 조건 분기 쉬움
- ✅ traceId로 로그 추적 가능
- ✅ 클라이언트는 하나의 파싱 로직만 필요

---

## 핵심 개념: ApiResponse 패턴의 구조

### 1. ApiCode 인터페이스

**역할**: HTTP 상태 코드, 응답 코드, 메시지를 하나로 묶어 관리

```java
public interface ApiCode {
    String name();              // Enum 이름 (예: SUCCESS, RESOURCE_NOT_FOUND)
    HttpStatus getHttpStatus(); // HTTP 상태 코드 (200, 404, 500 등)
    String getMessage();        // 응답 메시지
    int getCode();             // 커스텀 응답 코드 (200000, 404001 등)
}
```

**설계 의도:**
- HTTP 상태 코드만으로는 세부 에러 구분 어려움 (404는 "리소스 없음"인데, 어떤 리소스?)
- 커스텀 코드(`code`)로 세부 에러 구분 (404001: 사용자 없음, 404002: 스터디 없음)
- 인터페이스로 정의하여 도메인별 확장 가능 (CommonCode, AuthCode, DicomCode 등)

### 2. CommonCode Enum

**역할**: ApiCode 구현체로 공통 응답 코드 정의

```java
@Getter
public enum CommonCode implements ApiCode {
    // 성공
    SUCCESS(HttpStatus.OK, 200000, "Success"),

    // 클라이언트 에러
    INVALID_PARAMETER(HttpStatus.BAD_REQUEST, 400001, "Invalid parameter"),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, 401001, "Unauthorized"),
    FORBIDDEN(HttpStatus.FORBIDDEN, 403001, "Forbidden"),

    // 리소스 에러
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, 404001, "Resource not found"),

    // 서버 에러
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, 500001, "Internal server error");

    private final HttpStatus httpStatus;
    private final int code;
    private final String message;

    CommonCode(HttpStatus httpStatus, int code, String message) {
        this.httpStatus = httpStatus;
        this.code = code;
        this.message = message;
    }
}
```

**코드 체계:**
- `200000`: 성공
- `400xxx`: 클라이언트 에러 (잘못된 요청, 인증 실패 등)
- `404xxx`: 리소스 없음
- `500xxx`: 서버 에러

**확장 예시:**
```java
// 인증 관련 코드 (Week 6+ Keycloak 연동 시)
public enum AuthCode implements ApiCode {
    INVALID_TOKEN(HttpStatus.UNAUTHORIZED, 401101, "Invalid token"),
    EXPIRED_TOKEN(HttpStatus.UNAUTHORIZED, 401102, "Expired token"),
    INSUFFICIENT_PERMISSION(HttpStatus.FORBIDDEN, 403101, "Insufficient permission");
    // ...
}
```

### 3. ApiResponse<T> 제네릭 클래스

**역할**: 모든 API 응답을 감싸는 표준 포맷

```java
@Getter
@Builder
public class ApiResponse<T> {
    // 응답 정보
    private final ApiCode type;        // 응답 타입 (SUCCESS, RESOURCE_NOT_FOUND 등)
    private final Integer code;        // 응답 코드 (200000, 404001 등)
    private final String message;      // 응답 메시지
    private final T data;              // 실제 데이터 (제네릭)

    // 메타데이터 (ResponseBodyAdvice에서 자동 주입)
    private final String traceId;      // 로그 추적 ID
    private final Long timestamp;      // 응답 시간
    private final String path;         // 요청 경로

    // 페이징 정보 (페이징 응답 시)
    private final PageInfo page;

    // 팩토리 메서드: 성공 응답
    public static <T> ApiResponse<T> success(T data) {
        return ApiResponse.<T>builder()
                .type(CommonCode.SUCCESS)
                .code(CommonCode.SUCCESS.getCode())
                .message(CommonCode.SUCCESS.getMessage())
                .data(data)
                .build();
    }

    // 팩토리 메서드: 실패 응답
    public static <T> ApiResponse<T> of(ApiCode apiCode) {
        return ApiResponse.<T>builder()
                .type(apiCode)
                .code(apiCode.getCode())
                .message(apiCode.getMessage())
                .data(null)
                .build();
    }

    // 팩토리 메서드: 페이징 응답
    public static <T> ApiResponse<List<T>> ofPaged(
            List<T> data,
            int page,
            int size,
            long total) {
        return ApiResponse.<List<T>>builder()
                .type(CommonCode.SUCCESS)
                .code(CommonCode.SUCCESS.getCode())
                .message(CommonCode.SUCCESS.getMessage())
                .data(data)
                .page(PageInfo.of(page, size, total))
                .build();
    }

    // PageInfo 내부 클래스
    @Getter
    @AllArgsConstructor
    public static class PageInfo {
        private final int page;
        private final int size;
        private final long total;
        private final int totalPages;

        public static PageInfo of(int page, int size, long total) {
            int totalPages = (int) Math.ceil((double) total / size);
            return new PageInfo(page, size, total, totalPages);
        }
    }
}
```

**설계 포인트:**
- **제네릭 타입 `<T>`**: 어떤 데이터든 감쌀 수 있음 (User, Study, List<Study> 등)
- **Builder 패턴**: 선택적 필드 설정 편리
- **팩토리 메서드**: `success()`, `of()`, `ofPaged()` 등으로 생성 간편화
- **불변 객체**: `final` 필드로 응답 변경 방지

### 4. BusinessException

**역할**: 비즈니스 로직에서 발생하는 예외를 ApiCode와 연결

```java
@Getter
public class BusinessException extends RuntimeException {
    private final ApiCode apiCode;

    public BusinessException(ApiCode apiCode) {
        super(apiCode.getMessage());
        this.apiCode = apiCode;
    }

    public BusinessException(ApiCode apiCode, String message) {
        super(message);
        this.apiCode = apiCode;
    }
}
```

**확장 예외:**
```java
// 리소스 없음
public class ResourceNotFoundException extends BusinessException {
    public ResourceNotFoundException() {
        super(CommonCode.RESOURCE_NOT_FOUND);
    }

    public ResourceNotFoundException(String message) {
        super(CommonCode.RESOURCE_NOT_FOUND, message);
    }
}

// 잘못된 파라미터
public class InvalidParameterException extends BusinessException {
    public InvalidParameterException() {
        super(CommonCode.INVALID_PARAMETER);
    }
}
```

**사용 예시:**
```java
@Service
public class UserService {
    public User findById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + id));
    }
}
```

### 5. GlobalExceptionHandler

**역할**: 모든 예외를 잡아서 ApiResponse로 변환

```java
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    // BusinessException 처리
    @ExceptionHandler(BusinessException.class)
    protected ResponseEntity<ApiResponse<?>> handleBusinessException(
            BusinessException ex) {
        ApiCode apiCode = ex.getApiCode();
        log.warn("Business exception: {}", ex.getMessage());

        return ResponseEntity
                .status(apiCode.getHttpStatus())
                .body(ApiResponse.of(apiCode));
    }

    // Validation 에러 처리
    @ExceptionHandler(MethodArgumentNotValidException.class)
    protected ResponseEntity<ApiResponse<?>> handleValidationException(
            MethodArgumentNotValidException ex) {
        log.warn("Validation exception: {}", ex.getMessage());

        // 첫 번째 에러 메시지 추출
        String errorMessage = ex.getBindingResult()
                .getAllErrors()
                .stream()
                .findFirst()
                .map(DefaultMessageSourceResolvable::getDefaultMessage)
                .orElse("Invalid parameter");

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.<String>builder()
                        .type(CommonCode.INVALID_PARAMETER)
                        .code(CommonCode.INVALID_PARAMETER.getCode())
                        .message(errorMessage)
                        .build());
    }

    // 모든 미처리 예외
    @ExceptionHandler(Exception.class)
    protected ResponseEntity<ApiResponse<?>> handleException(Exception ex) {
        log.error("Unexpected exception", ex);

        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.of(CommonCode.INTERNAL_SERVER_ERROR));
    }
}
```

**처리 흐름:**
```
1. 비즈니스 로직에서 예외 발생
   ↓
2. GlobalExceptionHandler가 예외 캐치
   ↓
3. 예외 타입에 따라 적절한 ApiCode 선택
   ↓
4. ApiResponse로 변환하여 반환
   ↓
5. 클라이언트는 일관된 형식의 에러 응답 수신
```

---

## kingarthur 프로젝트 분석 (실무 사례)

SADO 프로젝트의 sado-common 설계를 위해 실무 프로젝트인 **kingarthur**를 분석했습니다.

### kingarthur 프로젝트 구조

```
kingarthur/
├── kingarthur-core/              # 기술적 기능 (분산락, 트랜잭션 등)
│   ├── core-lock/
│   ├── core-observability/
│   ├── core-stream/
│   └── core-transaction/
└── kingarthur-app/               # 애플리케이션 공통 코드 ⭐
    ├── common/
    │   ├── ApiResponse.java      # API 응답 표준화
    │   ├── PageableRequest.java
    │   └── PagedData.java
    ├── exception/
    │   ├── GlobalExceptionHandler.java
    │   ├── BusinessException.java
    │   └── [도메인별 예외]
    └── code/
        ├── ApiCode.java
        └── CommonCode.java
```

### kingarthur의 ApiResponse 고급 기능

kingarthur의 ApiResponse는 다음 고급 기능들을 포함합니다:

1. **메타데이터 자동 주입** (ResponseBodyAdvice 사용)
2. **메트릭 수집** (ErrorMetricsCollector)
3. **시스템 로그 이벤트 발행** (SystemLogEvent)
4. **커스텀 트랜잭션 어노테이션** (@BusinessTransactional)

**kingarthur의 GlobalExceptionHandler 예시:**
```java
@RestControllerAdvice
public class GlobalExceptionHandler {

    private final ErrorMetricsCollector metricsCollector;
    private final ApplicationEventPublisher eventPublisher;

    @ExceptionHandler(BusinessException.class)
    protected ResponseEntity<ApiResponse<?>> handleBusinessException(
            BusinessException ex, HttpServletRequest request) {

        // 메트릭 수집
        metricsCollector.recordBusinessException(ex.getApiCode());

        // 시스템 로그 이벤트 발행
        eventPublisher.publishEvent(
            SystemLogEvent.of(ex, request, ex.getApiCode())
        );

        return ResponseEntity
                .status(ex.getApiCode().getHttpStatus())
                .body(ApiResponse.of(ex.getApiCode()));
    }
}
```

### SADO vs kingarthur 비교

| 항목 | kingarthur | SADO (초기) |
|------|-----------|-------------|
| **메트릭 수집** | ErrorMetricsCollector | ❌ 생략 (Week 10+) |
| **시스템 로그** | SystemLogEvent 발행 | ❌ 생략 |
| **트랜잭션** | @BusinessTransactional | ❌ 생략 (Week 3+) |
| **ResponseBodyAdvice** | 메타데이터 자동 주입 | ⚠️ 간단한 버전 |
| **기본 패턴** | ApiCode + ApiResponse + GlobalExceptionHandler | ✅ 동일 |

**결론**: SADO는 핵심 패턴만 먼저 구현하고, 고급 기능은 점진적으로 추가

---

## SADO 프로젝트 적용 계획

### Phase 1: 핵심 구조 (Week 1-2)

**구현 순서:**
1. ApiCode 인터페이스
2. CommonCode Enum (4개 기본 코드)
3. BusinessException
4. ApiResponse (간소화 버전)
5. GlobalExceptionHandler (기본 예외만 처리)

**패키지 구조:**
```
sado-common/src/main/java/com/hanumoka/sado/common/
├── code/
│   ├── ApiCode.java
│   └── CommonCode.java
├── dto/
│   └── ApiResponse.java
└── exception/
    ├── BusinessException.java
    ├── GlobalExceptionHandler.java
    └── ResourceNotFoundException.java
```

### Phase 2: 확장 (Week 3+)

**추가 기능:**
1. **도메인별 ApiCode** (Week 3+)
   - DicomCode (DICOM 관련 에러)
   - AuthCode (인증/인가 에러, Week 6+)

2. **추가 예외** (필요시)
   - InvalidParameterException
   - AuthenticationException (Week 6+)
   - StorageException (Week 3+, SeaweedFS)

3. **ResponseBodyAdvice** (Week 10+)
   - traceId, timestamp, path 자동 주입
   - 간단한 버전으로 시작

### Phase 3: 관찰성 (Week 10+)

**고급 기능:**
1. ErrorMetricsCollector - 에러 메트릭 수집
2. SystemLogEvent - 로그 이벤트 발행
3. 분산 추적 (Sleuth/Zipkin)

---

## 사용 예시

### 1. 성공 응답

```java
@RestController
@RequestMapping("/api/v1/studies")
public class StudyController {

    @GetMapping("/{id}")
    public ApiResponse<Study> getStudy(@PathVariable Long id) {
        Study study = studyService.findById(id);
        return ApiResponse.success(study);
    }
}
```

**응답:**
```json
{
  "type": "SUCCESS",
  "code": 200000,
  "message": "Success",
  "data": {
    "id": 1,
    "patientId": "P001",
    "studyDate": "2025-12-26"
  }
}
```

### 2. 실패 응답 (자동 처리)

```java
@Service
public class StudyService {

    public Study findById(Long id) {
        return studyRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Study not found: " + id));
    }
}
```

**응답 (GlobalExceptionHandler가 자동 변환):**
```json
{
  "type": "RESOURCE_NOT_FOUND",
  "code": 404001,
  "message": "Study not found: 123",
  "data": null
}
```

### 3. 페이징 응답

```java
@GetMapping
public ApiResponse<List<Study>> getStudies(
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size) {

    Page<Study> studyPage = studyService.findAll(PageRequest.of(page, size));

    return ApiResponse.ofPaged(
            studyPage.getContent(),
            page,
            size,
            studyPage.getTotalElements()
    );
}
```

**응답:**
```json
{
  "type": "SUCCESS",
  "code": 200000,
  "message": "Success",
  "data": [
    { "id": 1, "patientId": "P001" },
    { "id": 2, "patientId": "P002" }
  ],
  "page": {
    "page": 0,
    "size": 20,
    "total": 100,
    "totalPages": 5
  }
}
```

### 4. Validation 에러

```java
@PostMapping
public ApiResponse<Study> createStudy(@Valid @RequestBody CreateStudyRequest request) {
    Study study = studyService.create(request);
    return ApiResponse.success(study);
}

// DTO
public record CreateStudyRequest(
    @NotBlank(message = "Patient ID is required")
    String patientId,

    @NotNull(message = "Study date is required")
    LocalDate studyDate
) {}
```

**응답 (Validation 실패 시):**
```json
{
  "type": "INVALID_PARAMETER",
  "code": 400001,
  "message": "Patient ID is required",
  "data": null
}
```

---

## 주의사항 / 함정

### 1. HTTP 상태 코드 vs 커스텀 코드

**잘못된 설계:**
```java
// ❌ 모든 응답을 200으로 반환하고 커스텀 코드로만 구분
return ResponseEntity.ok(ApiResponse.of(CommonCode.RESOURCE_NOT_FOUND));
// HTTP 200인데 실제로는 에러 → 잘못된 설계
```

**올바른 설계:**
```java
// ✅ HTTP 상태 코드와 커스텀 코드 모두 올바르게 설정
return ResponseEntity
        .status(apiCode.getHttpStatus())  // 404
        .body(ApiResponse.of(apiCode));   // code: 404001
```

**이유:**
- HTTP 상태 코드는 RESTful API의 표준
- 커스텀 코드는 세부 에러 구분용
- 둘 다 올바르게 설정해야 API 게이트웨이, 모니터링 도구와 호환

### 2. 제네릭 타입 와일드카드 주의

```java
// ❌ 컴파일 에러
public ApiResponse<?> getStudy() {
    return ApiResponse.success(new Study());  // 타입 추론 실패
}

// ✅ 명시적 타입 지정
public ApiResponse<Study> getStudy() {
    return ApiResponse.success(new Study());
}
```

### 3. GlobalExceptionHandler 순서

```java
@RestControllerAdvice
public class GlobalExceptionHandler {

    // ✅ 구체적인 예외를 먼저 처리
    @ExceptionHandler(ResourceNotFoundException.class)
    protected ResponseEntity<ApiResponse<?>> handleResourceNotFoundException(
            ResourceNotFoundException ex) {
        // 구체적 처리
    }

    @ExceptionHandler(BusinessException.class)
    protected ResponseEntity<ApiResponse<?>> handleBusinessException(
            BusinessException ex) {
        // 일반 처리
    }

    // ❌ 가장 일반적인 예외는 마지막에
    @ExceptionHandler(Exception.class)
    protected ResponseEntity<ApiResponse<?>> handleException(Exception ex) {
        // 폴백
    }
}
```

**이유:** Spring은 예외 핸들러를 위에서부터 순서대로 탐색하므로 구체적인 것을 먼저 배치

### 4. null 데이터 처리

```java
// ❌ data가 null일 때 응답 생략하지 말 것
{
  "type": "SUCCESS",
  "code": 200000,
  "message": "Success"
  // data 필드 없음 → 클라이언트 파싱 에러
}

// ✅ data: null 명시
{
  "type": "SUCCESS",
  "code": 200000,
  "message": "Success",
  "data": null  // 명시적으로 null
}
```

**이유:** 클라이언트는 일관된 필드를 기대함. 선택적 필드는 파싱 복잡도 증가

---

## 구현 시 체크리스트

### 설계 단계
- [ ] ApiCode 인터페이스 정의 (4개 메서드)
- [ ] CommonCode Enum 정의 (기본 4개 코드)
- [ ] 코드 체계 확립 (200xxx, 400xxx, 404xxx, 500xxx)
- [ ] ApiResponse 제네릭 클래스 설계
- [ ] 팩토리 메서드 정의 (success, of, ofPaged)

### 구현 단계
- [ ] BusinessException 기본 클래스 구현
- [ ] ResourceNotFoundException 등 확장 예외 구현
- [ ] GlobalExceptionHandler @RestControllerAdvice 구현
- [ ] 예외 핸들러 메서드 작성 (BusinessException, Validation, Exception)

### 테스트 단계
- [ ] 성공 응답 테스트
- [ ] 실패 응답 테스트 (404, 500)
- [ ] Validation 에러 테스트
- [ ] 페이징 응답 테스트

### 문서화 단계
- [ ] 응답 코드 체계 문서화
- [ ] API 응답 예시 작성
- [ ] 에러 핸들링 가이드 작성

---

## 결론

### 배운 점

1. **API 응답 표준화의 중요성**
   - 프론트엔드 개발 편의성 극대화
   - 에러 처리 일관성 확보
   - 유지보수성 향상

2. **Enum + Interface 패턴의 강력함**
   - HttpStatus, 코드, 메시지를 하나로 관리
   - 타입 안전성 보장
   - 도메인별 확장 용이

3. **GlobalExceptionHandler의 역할**
   - 모든 예외를 한 곳에서 처리
   - 비즈니스 로직에서는 예외만 던지면 됨
   - 응답 변환 로직 중앙화

4. **실무 사례(kingarthur) 분석의 가치**
   - 검증된 패턴 학습
   - 단계별 구현 전략 수립
   - 오버엔지니어링 방지

### 다음 단계

**Week 2 예고:**
- Version Catalog 설정으로 의존성 중앙 관리
- sado-common 핵심 클래스 실제 구현
  - ApiCode.java
  - CommonCode.java
  - ApiResponse.java
  - BusinessException.java
  - GlobalExceptionHandler.java
- sado-gateway에서 통합 테스트

### 참고 자료

**공식 문서:**
- [Spring Boot Exception Handling](https://spring.io/guides/tutorials/rest)
- [HTTP Status Codes](https://developer.mozilla.org/en-US/docs/Web/HTTP/Status)

**참조 프로젝트:**
- kingarthur-app (실무 프로젝트)
  - `kingarthur-app/src/main/java/com/ontact/kingarthur/common/ApiResponse.java`
  - `kingarthur-app/src/main/java/com/ontact/kingarthur/exception/GlobalExceptionHandler.java`

**관련 문서:**
- [valiant-discovering-karp.md (plan)](../../.claude/plans/) - sado-common 구현 계획
- [20_Claude_Code_파일_충돌_대응_전략.md](../../01_백엔드/02_가이드/20_Claude_Code_파일_충돌_대응_전략.md)

---

**작성**: 2025-12-26
**카테고리**: Week 1, 아키텍처 설계, 공통 모듈
**태그**: #ApiResponse #GlobalExceptionHandler #REST #ExceptionHandling #DesignPattern
