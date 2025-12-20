# 🔀 JPA와 MyBatis 혼용 전략

> 🔀 **문서 역할**: JPA와 MyBatis 사용 기준 및 구현 가이드 (최종 문서)
>
> 📅 **작성일**: 2025-12-20
>
> 🎯 **적용 시기**: Week 2부터 적용
>
> 🔗 **연결**: `07_최종_구현_계획.md` Week 2 섹션

---

## 1. 왜 JPA + MyBatis를 혼용하는가?

### JPA의 장점
- ✅ 도메인 모델 중심 설계
- ✅ 변경 감지 (Dirty Checking)
- ✅ 1차 캐시, Lazy Loading
- ✅ 생산성 높음 (CRUD 자동 생성)

### JPA의 단점
- ❌ 복잡한 조인 쿼리 성능 저하
- ❌ 통계/집계 쿼리 작성 어려움
- ❌ N+1 문제 발생 가능
- ❌ 네이티브 쿼리 힌트 사용 불편

### MyBatis의 장점
- ✅ 복잡한 SQL 직접 작성
- ✅ 조회 성능 최적화 용이
- ✅ 동적 쿼리 작성 편리
- ✅ ResultMap으로 DTO 직접 매핑

### MyBatis의 단점
- ❌ SQL 작성 부담
- ❌ 도메인 모델 생명주기 관리 불편
- ❌ 변경 감지 없음

---

## 2. 사용 기준

### JPA를 사용하는 경우 ✅

1. **도메인 엔티티 CRUD**
   - Study, Series, Instance 생성, 수정, 삭제
   - 단일 엔티티 조회 (findById)

2. **연관 관계 탐색**
   - Study → Series → Instance 탐색
   - Lazy Loading 활용

3. **트랜잭션 경계 내 비즈니스 로직**
   - Dirty Checking으로 변경 감지
   - 예: Study 상태 변경 (UPLOADED → ANALYZING)

**예시:**
```java
@Service
public class StudyService {

    @Transactional
    public Study createStudy(StudyCreateRequest request) {
        Study study = new Study(request.getPatientId(), request.getStudyDate());
        return studyRepository.save(study); // JPA
    }

    @Transactional
    public void updateStudyStatus(Long studyId, StudyStatus status) {
        Study study = studyRepository.findById(studyId)
            .orElseThrow(() -> new StudyNotFoundException(studyId));
        study.changeStatus(status); // Dirty Checking
        // 자동 update 쿼리 실행
    }
}
```

---

### MyBatis를 사용하는 경우 ✅

1. **복잡한 조회 쿼리**
   - 다중 테이블 조인
   - 집계 함수, GROUP BY
   - 통계 리포트

2. **페이징 성능 최적화**
   - COUNT 쿼리 최적화
   - 커버링 인덱스 활용

3. **DTO 직접 조회**
   - 화면 출력용 데이터
   - 엔티티 변경 불필요한 조회

**예시:**
```java
@Mapper
public interface StudyQueryMapper {

    // 복잡한 조회: Study 목록 + Patient 정보 + Series 개수
    List<StudyListDTO> findStudyListByCondition(
        @Param("patientName") String patientName,
        @Param("startDate") LocalDate startDate,
        @Param("endDate") LocalDate endDate
    );

    // 통계: 월별 Study 개수
    List<StudyStatisticsDTO> getMonthlyStudyStatistics(
        @Param("year") int year
    );
}
```

```xml
<!-- StudyQueryMapper.xml -->
<select id="findStudyListByCondition" resultType="StudyListDTO">
    SELECT
        s.study_id,
        s.study_date,
        s.status,
        p.patient_name,
        COUNT(sr.series_id) as series_count
    FROM study s
    INNER JOIN patient p ON s.patient_id = p.patient_id
    LEFT JOIN series sr ON s.study_id = sr.study_id
    WHERE 1=1
    <if test="patientName != null">
        AND p.patient_name LIKE CONCAT('%', #{patientName}, '%')
    </if>
    <if test="startDate != null">
        AND s.study_date >= #{startDate}
    </if>
    <if test="endDate != null">
        AND s.study_date <= #{endDate}
    </if>
    GROUP BY s.study_id, s.study_date, s.status, p.patient_name
    ORDER BY s.study_date DESC
</select>
```

---

## 3. 모듈별 적용 전략

### MiniPACS 모듈
- **JPA**: Study, Series, Instance 생성/수정/삭제
- **MyBatis**: Study 목록 조회, 검색

### BFF 모듈
- **JPA**: 리포트 생성 (Report 엔티티 저장)
- **MyBatis**: 대시보드 통계 조회, 리포트 목록

### Orchestrator 모듈
- **JPA**: Workflow 상태 저장
- **MyBatis**: Workflow 이력 조회

---

## 4. FK 제약조건 없는 설계

### 설정 방법

**JPA Entity:**
```java
@Entity
@Table(name = "series")
public class Series {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long seriesId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "study_id",
                foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
    private Study study;

    // ...
}
```

**MyBatis에서도 FK 없이 조인:**
```sql
-- FK 제약이 없어도 조인 가능
SELECT s.*, st.study_date
FROM series s
INNER JOIN study st ON s.study_id = st.study_id
WHERE s.series_id = #{seriesId}
```

### 데이터 무결성 보장

**애플리케이션 레벨에서 검증:**
```java
@Service
public class SeriesService {

    @Transactional
    public Series createSeries(Long studyId, SeriesCreateRequest request) {
        // 1. Study 존재 여부 확인 (FK 제약 대신 애플리케이션에서)
        Study study = studyRepository.findById(studyId)
            .orElseThrow(() -> new StudyNotFoundException(studyId));

        // 2. Series 생성
        Series series = new Series(study, request.getSeriesNumber());
        return seriesRepository.save(series);
    }

    @Transactional
    public void deleteSeries(Long seriesId) {
        Series series = seriesRepository.findById(seriesId)
            .orElseThrow(() -> new SeriesNotFoundException(seriesId));

        // 3. 하위 데이터 확인 (FK Cascade 대신 애플리케이션에서)
        if (instanceRepository.countBySeriesId(seriesId) > 0) {
            throw new SeriesHasInstancesException("Cannot delete series with instances");
        }

        seriesRepository.delete(series);
    }
}
```

---

## 5. 성능 고려사항

### JPA N+1 문제 방지

**Bad:**
```java
// N+1 발생
List<Study> studies = studyRepository.findAll();
for (Study study : studies) {
    study.getSeries().size(); // N개의 쿼리 실행
}
```

**Good:**
```java
// Fetch Join으로 해결
@Query("SELECT s FROM Study s LEFT JOIN FETCH s.series WHERE s.id = :id")
Optional<Study> findByIdWithSeries(@Param("id") Long id);
```

**Better (대량 조회):**
```java
// MyBatis로 최적화
List<StudyWithSeriesCountDTO> findStudiesWithSeriesCount();
```

### MyBatis 페이징 최적화

```xml
<!-- COUNT 쿼리 분리 -->
<select id="countStudies" resultType="long">
    SELECT COUNT(1) FROM study WHERE status = #{status}
</select>

<select id="findStudiesWithPaging" resultType="StudyDTO">
    SELECT * FROM study
    WHERE status = #{status}
    ORDER BY study_date DESC
    LIMIT #{offset}, #{limit}
</select>
```

---

## 6. 테스트 전략

### JPA 테스트
```java
@DataJpaTest
class StudyRepositoryTest {

    @Autowired
    private StudyRepository studyRepository;

    @Test
    void testCreateStudy() {
        Study study = new Study("P001", LocalDate.now());
        Study saved = studyRepository.save(study);
        assertThat(saved.getId()).isNotNull();
    }
}
```

### MyBatis 테스트
```java
@MybatisTest
class StudyQueryMapperTest {

    @Autowired
    private StudyQueryMapper studyQueryMapper;

    @Test
    void testFindStudyList() {
        List<StudyListDTO> results = studyQueryMapper
            .findStudyListByCondition("John", null, null);
        assertThat(results).isNotEmpty();
    }
}
```

---

## 7. 블로그 작성 가이드

### 추천 주제
1. "JPA와 MyBatis 어떻게 섞어 쓸까? 실전 가이드"
2. "FK 제약조건 없이 데이터 무결성 지키기"
3. "JPA N+1 문제? MyBatis로 해결!"

### 코드 예시 구조
- Before (JPA만 사용): N+1 문제 발생
- After (MyBatis 추가): 조회 성능 개선
- 벤치마크: JPA vs MyBatis 성능 비교

---

## 8. 참고 자료

- [Spring Data JPA 공식 문서](https://spring.io/projects/spring-data-jpa)
- [MyBatis 공식 문서](https://mybatis.org/)
- [Hibernate 성능 최적화](https://docs.jboss.org/hibernate/orm/6.0/userguide/html_single/Hibernate_User_Guide.html#best-practices)

---

## 결론

**JPA와 MyBatis 혼용 전략:**

1. **도메인 로직 = JPA** (생성, 수정, 삭제, 비즈니스 로직)
2. **조회 최적화 = MyBatis** (복잡한 조회, 통계, 리포트)
3. **FK 없는 설계** = 애플리케이션 레벨에서 무결성 보장

**학습 우선순위:**
1. Week 2: JPA 엔티티 설계 (FK 제약 없음)
2. Week 2: MyBatis Mapper 작성
3. Week 2: 성능 비교 및 최적화

**최종 목표**: 각 기술의 장점을 살려 프로덕션 레벨의 데이터 접근 계층 구축 ⭐⭐⭐⭐

---

**작성일**: 2025-12-20
**다음 업데이트**: Week 2 학습 완료 후
