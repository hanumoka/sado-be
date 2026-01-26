# TASK-EXAMPLE-001-BE: Study 검색 API (예시)

## 📋 작업 개요

- **작업 ID**: TASK-EXAMPLE-001-BE
- **연관 작업**: TASK-EXAMPLE-001-FE
- **우선순위**: High
- **예상 시간**: 2시간
- **작성일**: 2026-01-02
- **담당 터미널**: BE

---

## 🎯 요구사항

Study 검색 API 엔드포인트를 구현합니다.

**기능**:
- 환자 이름 또는 Study Instance UID로 검색
- 대소문자 구분 없이 검색
- 페이지네이션 지원
- 검색어 최소 2자 이상 검증

---

## 🔗 의존성

### FE 의존성
- TASK-EXAMPLE-001-FE에서 사용할 API 제공

### DB 의존성
- Study, Patient 테이블 조회
- 인덱스 확인: patient.patient_name

---

## 📁 수정 파일 목록

### 신규 생성
- `sado_be/sado-minipacs/src/main/java/com/hanumoka/sado/minipacs/dto/request/StudySearchRequest.java`
- `sado_be/sado-minipacs/src/main/java/com/hanumoka/sado/minipacs/dto/response/StudySearchResponse.java`

### 수정 필요
- `sado_be/sado-minipacs/src/main/java/com/hanumoka/sado/minipacs/controller/StudyController.java`
- `sado_be/sado-minipacs/src/main/java/com/hanumoka/sado/minipacs/domain/service/StudyService.java`
- `sado_be/sado-minipacs/src/main/java/com/hanumoka/sado/minipacs/domain/repository/StudyRepository.java`

---

## 🛠️ 구현 가이드

### 1단계: DTO 작성

**StudySearchRequest.java**:
```java
package com.hanumoka.sado.minipacs.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class StudySearchRequest {

    @NotBlank(message = "검색어를 입력하세요")
    @Size(min = 2, message = "검색어는 최소 2자 이상이어야 합니다")
    private String searchKeyword;

    @Min(value = 0, message = "페이지는 0 이상이어야 합니다")
    private int page = 0;

    @Min(value = 1, message = "크기는 1 이상이어야 합니다")
    private int size = 20;
}
```

**StudySearchResponse.java**:
```java
package com.hanumoka.sado.minipacs.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StudySearchResponse {

    private List<StudyDto> studies;
    private int totalCount;
    private int currentPage;
}
```

### 2단계: Repository 메서드 추가

**StudyRepository.java에 추가**:
```java
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StudyRepository extends JpaRepository<Study, Long> {

    // 기존 메서드들...

    @Query("SELECT s FROM Study s " +
           "JOIN FETCH s.patient p " +
           "WHERE LOWER(p.patientName) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
           "   OR LOWER(s.studyInstanceUid) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
           "ORDER BY s.studyDate DESC")
    Page<Study> searchByKeyword(@Param("keyword") String keyword, Pageable pageable);
}
```

### 3단계: Service 로직 구현

**StudyService.java에 추가**:
```java
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

@Service
@RequiredArgsConstructor
public class StudyService {

    private final StudyRepository studyRepository;

    // 기존 메서드들...

    public StudySearchResponse searchStudies(StudySearchRequest request) {
        // 페이지네이션 설정
        Pageable pageable = PageRequest.of(request.getPage(), request.getSize());

        // 검색 실행
        Page<Study> studyPage = studyRepository.searchByKeyword(
            request.getSearchKeyword(),
            pageable
        );

        // DTO 변환
        List<StudyDto> studies = studyPage.getContent().stream()
            .map(this::convertToDto)
            .toList();

        // 응답 생성
        return StudySearchResponse.builder()
            .studies(studies)
            .totalCount((int) studyPage.getTotalElements())
            .currentPage(request.getPage())
            .build();
    }

    private StudyDto convertToDto(Study study) {
        // 기존 변환 로직 재사용
        return StudyDto.builder()
            .studyId(study.getStudyInstanceUid())
            .patientName(study.getPatient().getPatientName())
            .studyDate(study.getStudyDate())
            .modality(study.getModality())
            .build();
    }
}
```

### 4단계: Controller 엔드포인트 추가

**StudyController.java에 추가**:
```java
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/studies")
@RequiredArgsConstructor
public class StudyController {

    private final StudyService studyService;

    // 기존 엔드포인트들...

    @Operation(summary = "Study 검색", description = "환자 이름 또는 Study UID로 검색")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "검색 성공"
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "400",
            description = "잘못된 요청 (검색어 누락 또는 너무 짧음)"
        )
    })
    @GetMapping("/search")
    public ApiResponse<StudySearchResponse> searchStudies(
        @Valid @ModelAttribute StudySearchRequest request
    ) {
        StudySearchResponse response = studyService.searchStudies(request);
        return ApiResponse.success(response);
    }
}
```

---

## ✅ 체크리스트

### DTO
- [ ] StudySearchRequest 작성
- [ ] StudySearchResponse 작성
- [ ] 유효성 검증 애노테이션 추가

### Repository
- [ ] searchByKeyword 메서드 작성
- [ ] JOIN FETCH로 N+1 방지
- [ ] Pageable 지원

### Service
- [ ] searchStudies 메서드 구현
- [ ] DTO 변환 로직
- [ ] 예외 처리

### Controller
- [ ] GET /api/studies/search 엔드포인트
- [ ] @Valid 유효성 검증
- [ ] ApiResponse 래핑
- [ ] Swagger 문서화

### 테스트
- [ ] Postman/Bruno 테스트
- [ ] Swagger UI 테스트
- [ ] 유효성 검증 테스트
- [ ] 페이지네이션 테스트

---

## 🧪 테스트 시나리오

### 1. 정상 검색
```bash
GET /api/studies/search?searchKeyword=Kim&page=0&size=10

응답:
{
  "code": 200000,
  "message": "Success",
  "data": {
    "studies": [
      {
        "studyId": "1.2.840...",
        "patientName": "Kim^Chul^Soo",
        "studyDate": "2024-01-15",
        "modality": "US"
      }
    ],
    "totalCount": 5,
    "currentPage": 0
  }
}
```

### 2. 결과 없음
```bash
GET /api/studies/search?searchKeyword=NonExistent

응답:
{
  "code": 200000,
  "message": "Success",
  "data": {
    "studies": [],
    "totalCount": 0,
    "currentPage": 0
  }
}
```

### 3. 검증 실패 (1자)
```bash
GET /api/studies/search?searchKeyword=K

응답:
{
  "code": 400000,
  "message": "검색어는 최소 2자 이상이어야 합니다"
}
```

### 4. 페이지네이션
```bash
GET /api/studies/search?searchKeyword=Kim&page=1&size=5

응답: 2페이지 결과 (5개 스킵 후 다음 5개)
```

---

## 🗄️ DB 쿼리 확인

### 생성되는 쿼리
```sql
SELECT s.*, p.*
FROM study s
INNER JOIN patient p ON s.patient_id = p.patient_id
WHERE LOWER(p.patient_name) LIKE LOWER('%kim%')
   OR LOWER(s.study_instance_uid) LIKE LOWER('%kim%')
ORDER BY s.study_date DESC
LIMIT 20 OFFSET 0;
```

### 인덱스 확인
```sql
-- patient_name 인덱스 확인
SHOW INDEX FROM patient WHERE Column_name = 'patient_name';

-- 인덱스가 없다면 생성 (선택사항)
CREATE INDEX idx_patient_name ON patient(patient_name);
```

---

## 📊 API 스펙

### Endpoint
```
GET /api/studies/search
```

### Query Parameters
| 파라미터 | 타입 | 필수 | 기본값 | 설명 |
|---------|------|------|--------|------|
| searchKeyword | String | ✅ | - | 검색어 (최소 2자) |
| page | Integer | ❌ | 0 | 페이지 번호 (0부터 시작) |
| size | Integer | ❌ | 20 | 페이지 크기 |

### Response
```json
{
  "code": 200000,
  "message": "Success",
  "data": {
    "studies": [StudyDto[]],
    "totalCount": number,
    "currentPage": number
  }
}
```

---

## 📝 추가 노트

### 성능 최적화
- JOIN FETCH로 N+1 문제 방지
- 인덱스 활용 (patient_name)
- 페이지네이션으로 대량 데이터 처리

### 보안
- JPA 사용으로 SQL Injection 자동 방지
- 유효성 검증으로 잘못된 입력 차단

### 개선 가능 사항
- Full-text search 적용 (MySQL FULLTEXT 또는 Elasticsearch)
- 검색어 하이라이팅
- 검색 통계 로깅

---

## 🔄 상태 추적

- [ ] 작업 시작
- [ ] DTO 작성 완료
- [ ] Repository 구현 완료
- [ ] Service 구현 완료
- [ ] Controller 구현 완료
- [ ] Swagger 문서화 완료
- [ ] 테스트 완료
- [ ] 완료 → archive 이동
