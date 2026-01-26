# Week 2: Spring Boot + JPA/MyBatis + MySQL + Multi-Tenancy ⭐

> **기간**: YYYY-MM-DD ~ YYYY-MM-DD
> **학습 시간**: N시간
> **완료 여부**: ⏳ 진행 중

---

## 📚 학습 목표

- [ ] MySQL 8.0 Docker Compose 설정 및 연결
- [ ] JPA 도메인 모델 설계 (FK 제약조건 없는 설계)
- [ ] MyBatis XML Mapper 작성 (복잡한 조회 쿼리)
- [ ] JPA와 MyBatis 혼용 전략 이해 및 적용
- [ ] MySQL JSON 타입으로 DICOM 메타데이터 저장
- [ ] **멀티테넌시 기본 구현 (tenant_id 컬럼)** ⭐ NEW

---

## 📖 학습 내용

### 1. MySQL 8.0 설치 및 설정

**개념**:
- InnoDB 스토리지 엔진 (기본)
- JSON 데이터 타입
- Master-Slave 구조 이해 (실습은 Week 14)

**실습 내용**:
```yaml
# docker-compose.yml
mysql:
  image: mysql:8.0
  ports:
    - "3306:3306"
  environment:
    MYSQL_DATABASE: sado
    MYSQL_USER: sado
    MYSQL_PASSWORD: sado123
    MYSQL_ROOT_PASSWORD: root123
  command: --character-set-server=utf8mb4 --collation-server=utf8mb4_unicode_ci
```

**연결 설정**:
```yaml
# application.yml
spring:
  datasource:
    driver-class-name: com.mysql.cj.jdbc.Driver
    url: jdbc:mysql://localhost:3306/sado
    username: sado
    password: sado123
  jpa:
    database-platform: org.hibernate.dialect.MySQL8Dialect
    hibernate:
      ddl-auto: create
    show-sql: true
```

**참고 자료**:
- [MySQL 공식 문서](https://dev.mysql.com/doc/)
- [MySQL JSON 타입](https://dev.mysql.com/doc/refman/8.0/en/json.html)

---

### 2. JPA 도메인 모델 설계 (FK 제약조건 없음)

**개념**:
- 물리적 FK 제약조건 없이 논리적 관계만 정의
- 데이터 무결성은 애플리케이션 레벨에서 보장
- DB 마이그레이션 유연성 확보

**실습 내용**:
```java
@Entity
@Table(name = "study")
public class Study {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long studyId;

    private String patientId;
    private LocalDate studyDate;
    private String status;

    @OneToMany(mappedBy = "study", fetch = FetchType.LAZY)
    private List<Series> seriesList = new ArrayList<>();

    // JSON 메타데이터
    @Column(columnDefinition = "JSON")
    @Convert(converter = DicomMetadataConverter.class)
    private DicomMetadata metadata;
}
```

```java
@Entity
@Table(name = "series")
public class Series {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long seriesId;

    // FK 제약 없이 관계 설정
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "study_id",
                foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
    private Study study;

    private String seriesNumber;
}
```

**참고 자료**:
- [JPA FK 제약조건 설정](https://docs.jboss.org/hibernate/orm/6.0/userguide/html_single/Hibernate_User_Guide.html#associations)
- `10_JPA_MyBatis_혼용_전략.md`

---

### 3. MyBatis 설정 및 Mapper 작성

**개념**:
- JPA는 도메인 CRUD, MyBatis는 복잡한 조회
- XML Mapper로 SQL 직접 작성
- 동적 쿼리 작성 (if, choose, foreach)

**실습 내용**:
```java
// MyBatis Mapper 인터페이스
@Mapper
public interface StudyQueryMapper {
    List<StudyListDTO> findStudyListByCondition(
        @Param("patientName") String patientName,
        @Param("startDate") LocalDate startDate,
        @Param("endDate") LocalDate endDate
    );

    Long countStudies(@Param("status") String status);
}
```

```xml
<!-- StudyQueryMapper.xml -->
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE mapper PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN"
        "http://mybatis.org/dtd/mybatis-3-mapper.dtd">

<mapper namespace="com.sado.minipacs.mapper.StudyQueryMapper">

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

    <select id="countStudies" resultType="long">
        SELECT COUNT(1)
        FROM study
        WHERE status = #{status}
    </select>

</mapper>
```

**MyBatis 설정**:
```yaml
# application.yml
mybatis:
  mapper-locations: classpath:mapper/**/*.xml
  type-aliases-package: com.sado.minipacs.dto
  configuration:
    map-underscore-to-camel-case: true
```

**참고 자료**:
- [MyBatis 공식 문서](https://mybatis.org/mybatis-3/)
- [Dynamic SQL](https://mybatis.org/mybatis-3/dynamic-sql.html)

---

### 4. 멀티테넌시 기본 구현 ⭐

**개념**:
- Shared Database 전략: 하나의 DB에 모든 테넌트 데이터
- tenant_id 컬럼으로 격리
- JPA @Filter로 자동 필터링

**실습 내용**:

**1) TenantAwareEntity 구현**:
```java
@FilterDef(name = "tenantFilter",
           parameters = @ParamDef(name = "tenantId", type = Long.class))
@Filter(name = "tenantFilter",
        condition = "tenant_id = :tenantId")
@MappedSuperclass
@EntityListeners(TenantEntityListener.class)
public abstract class TenantAwareEntity {

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    public Long getTenantId() {
        return tenantId;
    }

    public void setTenantId(Long tenantId) {
        this.tenantId = tenantId;
    }
}
```

**2) Study Entity 변경**:
```java
@Entity
@Table(name = "study")
public class Study extends TenantAwareEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long studyId;

    private String patientId;
    private LocalDate studyDate;
    private String status;

    // tenant_id는 TenantAwareEntity에서 상속받음
}
```

**3) TenantContext**:
```java
public class TenantContext {
    private static final ThreadLocal<Long> currentTenant = new ThreadLocal<>();

    public static void setCurrentTenantId(Long tenantId) {
        currentTenant.set(tenantId);
    }

    public static Long getCurrentTenantId() {
        Long tenantId = currentTenant.get();
        if (tenantId == null) {
            throw new TenantNotSetException("Tenant ID not set in context");
        }
        return tenantId;
    }

    public static void clear() {
        currentTenant.remove();
    }
}
```

**4) TenantInterceptor**:
```java
@Component
public class TenantInterceptor implements HandlerInterceptor {
    @Override
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response,
                             Object handler) {
        String tenantIdHeader = request.getHeader("X-Tenant-ID");

        if (tenantIdHeader == null) {
            throw new TenantNotProvidedException("X-Tenant-ID header missing");
        }

        Long tenantId = Long.parseLong(tenantIdHeader);
        TenantContext.setCurrentTenantId(tenantId);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request,
                                HttpServletResponse response,
                                Object handler,
                                Exception ex) {
        TenantContext.clear();
    }
}
```

**5) TenantFilterAspect**:
```java
@Component
@Aspect
public class TenantFilterAspect {
    @PersistenceContext
    private EntityManager entityManager;

    @Before("execution(* com.sado..repository..*(..))")
    public void enableTenantFilter() {
        Long tenantId = TenantContext.getCurrentTenantId();
        Session session = entityManager.unwrap(Session.class);
        session.enableFilter("tenantFilter")
               .setParameter("tenantId", tenantId);
    }
}
```

**6) AspectJ 활성화**:
```java
@Configuration
@EnableAspectJAutoProxy
public class AopConfig {
    // AspectJ 활성화
}
```

**참고 자료**:
- [Hibernate @Filter 공식 문서](https://docs.jboss.org/hibernate/orm/6.0/userguide/html_single/Hibernate_User_Guide.html#mapping-column-filter)
- `12_멀티테넌시_설계_가이드.md`

---

### 5. JPA vs MyBatis 사용 기준

**JPA 사용 경우**:
```java
// Study 생성 (JPA)
@Service
public class StudyService {
    @Transactional
    public Study createStudy(StudyCreateRequest request) {
        Study study = new Study(request.getPatientId(), request.getStudyDate());
        return studyRepository.save(study);
    }

    @Transactional
    public void updateStudyStatus(Long studyId, String status) {
        Study study = studyRepository.findById(studyId)
            .orElseThrow(() -> new StudyNotFoundException(studyId));
        study.changeStatus(status); // Dirty Checking
        // 자동 update 쿼리 실행
    }
}
```

**MyBatis 사용 경우**:
```java
// Study 목록 조회 (MyBatis)
@Service
public class StudyQueryService {
    public List<StudyListDTO> getStudyList(String patientName) {
        return studyQueryMapper.findStudyListByCondition(patientName, null, null);
    }
}
```

**사용 기준 정리**:
| 상황 | 사용 기술 | 이유 |
|------|---------|------|
| Study 생성/수정/삭제 | JPA | Dirty Checking, 도메인 모델 |
| Study 목록 조회 | MyBatis | 조인 성능, DTO 직접 매핑 |
| Study 단건 조회 | JPA | findById, Lazy Loading |
| 통계 쿼리 | MyBatis | 집계 함수, GROUP BY |

---

### 6. MySQL JSON 타입 활용

**개념**:
- DICOM 메타데이터를 JSON으로 저장
- JSON_EXTRACT로 경로 쿼리
- JPA Converter로 매핑

**실습 내용**:
```java
// JSON Converter
@Converter
public class DicomMetadataConverter implements AttributeConverter<DicomMetadata, String> {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public String convertToDatabaseColumn(DicomMetadata attribute) {
        try {
            return objectMapper.writeValueAsString(attribute);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to convert DicomMetadata to JSON", e);
        }
    }

    @Override
    public DicomMetadata convertToEntityAttribute(String dbData) {
        try {
            return objectMapper.readValue(dbData, DicomMetadata.class);
        } catch (IOException e) {
            throw new RuntimeException("Failed to convert JSON to DicomMetadata", e);
        }
    }
}
```

**JSON 경로 쿼리 (MyBatis)**:
```xml
<select id="findStudiesByModality" resultType="Study">
    SELECT *
    FROM study
    WHERE JSON_EXTRACT(metadata, '$.modality') = #{modality}
</select>
```

**참고 자료**:
- [MySQL JSON Functions](https://dev.mysql.com/doc/refman/8.0/en/json-function-reference.html)

---

### 7. HikariCP Connection Pool 튜닝

**실습 내용**:
```yaml
# application.yml
spring:
  datasource:
    hikari:
      maximum-pool-size: 10
      minimum-idle: 5
      connection-timeout: 30000
      idle-timeout: 600000
      max-lifetime: 1800000
```

**튜닝 기준**:
- maximum-pool-size: CPU 코어 수 * 2 + 디스크 수
- minimum-idle: 트래픽 최소값 기준
- connection-timeout: 30초 (기본값)

---

## 💡 핵심 인사이트

1. **JPA + MyBatis 혼용의 핵심**:
   - 도메인 로직은 JPA (생성, 수정, 삭제)
   - 조회 최적화는 MyBatis (복잡한 조인, 통계)
   - 각 기술의 장점을 살려서 사용

2. **FK 제약조건 없는 설계의 장점**:
   - DB 마이그레이션 시 테이블 순서 자유로움
   - 성능 (FK 체크 오버헤드 없음)
   - 단점: 애플리케이션에서 무결성 보장 필요

3. **MySQL JSON 타입의 활용**:
   - DICOM 메타데이터처럼 스키마가 유동적인 데이터에 적합
   - JSON_EXTRACT로 쿼리 가능
   - 인덱싱 필요 시 Virtual Column 활용

4. **멀티테넌시 구현의 핵심**: ⭐ NEW
   - Shared Database 전략: 비용 효율적, POC에 최적
   - Hibernate @Filter: 모든 JPA 쿼리에 자동 tenant_id 조건 추가
   - ThreadLocal TenantContext: 요청별 tenant 관리
   - AspectJ 활용: Filter 자동 활성화로 개발자 실수 방지

---

## 🐛 문제 및 해결

### 문제 1: MyBatis와 JPA 트랜잭션 충돌

**증상**:
```
No transactional SqlSession found
```

**원인**:
- MyBatis와 JPA가 서로 다른 DataSource를 사용

**해결 방법**:
```java
@Configuration
public class MyBatisConfig {

    @Bean
    public SqlSessionFactory sqlSessionFactory(DataSource dataSource) throws Exception {
        SqlSessionFactoryBean factory = new SqlSessionFactoryBean();
        factory.setDataSource(dataSource); // JPA와 동일한 DataSource
        factory.setMapperLocations(
            new PathMatchingResourcePatternResolver().getResources("classpath:mapper/**/*.xml")
        );
        return factory.getObject();
    }
}
```

**교훈**:
- MyBatis와 JPA는 같은 DataSource를 공유해야 함
- @Transactional로 트랜잭션 경계 명확히

---

### 문제 2: FK 없이 삭제 시 Orphan 데이터 발생

**증상**:
- Study를 삭제했는데 Series가 남아있음
- DB에 불일치 데이터 존재

**원인**:
- FK Cascade 옵션이 없어서 자동 삭제 안 됨

**해결 방법**:
```java
@Service
public class StudyService {

    @Transactional
    public void deleteStudy(Long studyId) {
        // 1. 하위 데이터 확인
        if (seriesRepository.existsByStudyId(studyId)) {
            throw new StudyHasSeriesException("Cannot delete study with series");
        }

        // 2. Study 삭제
        studyRepository.deleteById(studyId);
    }

    @Transactional
    public void deleteStudyWithSeries(Long studyId) {
        // 1. 하위 데이터 먼저 삭제
        seriesRepository.deleteAllByStudyId(studyId);

        // 2. Study 삭제
        studyRepository.deleteById(studyId);
    }
}
```

**교훈**:
- FK 없는 설계에서는 애플리케이션에서 무결성 체크 필수
- 삭제 순서 중요 (하위 → 상위)

---

### 문제 3: JPA N+1 문제

**증상**:
```java
List<Study> studies = studyRepository.findAll();
for (Study study : studies) {
    study.getSeries().size(); // N개의 쿼리 실행!
}
```

**원인**:
- Lazy Loading으로 인한 N+1 문제

**해결 방법 1 (JPA Fetch Join)**:
```java
@Query("SELECT s FROM Study s LEFT JOIN FETCH s.seriesList WHERE s.id = :id")
Optional<Study> findByIdWithSeries(@Param("id") Long id);
```

**해결 방법 2 (MyBatis로 전환)**:
```xml
<select id="findStudiesWithSeriesCount" resultType="StudyListDTO">
    SELECT s.study_id, s.study_date, COUNT(sr.series_id) as series_count
    FROM study s
    LEFT JOIN series sr ON s.study_id = sr.study_id
    GROUP BY s.study_id
</select>
```

**교훈**:
- 대량 조회는 MyBatis로 최적화
- JPA는 단건 조회나 도메인 로직에 활용

---

### 문제 4: Tenant Filter가 적용 안 됨 ⭐

**증상**:
```java
List<Study> studies = studyRepository.findAll();
// Tenant A 사용자인데 Tenant B 데이터도 조회됨!
```

**원인**:
- TenantFilterAspect가 실행되지 않음
- @Aspect가 Spring Bean으로 등록 안 됨

**해결 방법**:
```java
@Configuration
@EnableAspectJAutoProxy
public class AopConfig {
    // AspectJ 활성화
}
```

**교훈**:
- @Aspect는 @EnableAspectJAutoProxy 필요
- Filter 활성화를 로그로 확인: `log.info("Tenant filter enabled for tenant: {}", tenantId);`

---

## 📊 학습 성과

### 달성한 목표
- ✅ MySQL 8.0 Docker Compose 설정
- ✅ JPA 도메인 모델 설계 (FK 제약 없음)
- ✅ MyBatis XML Mapper 작성
- ✅ JPA+MyBatis 혼용 전략 적용
- ✅ MySQL JSON 타입 활용
- ✅ **멀티테넌시 기본 구현 (tenant_id 컬럼)** ⭐ NEW

### 마스터 수준
| 기술 | 학습 전 | 학습 후 | 목표 |
|------|---------|---------|------|
| JPA | ⭐⭐ | ⭐⭐⭐⭐ | ⭐⭐⭐⭐ |
| MyBatis | ⭐ | ⭐⭐⭐ | ⭐⭐⭐⭐ |
| MySQL | ⭐⭐ | ⭐⭐⭐ | ⭐⭐⭐⭐ |
| **Multi-Tenancy** ⭐ | ⭐ | ⭐⭐⭐ | ⭐⭐⭐⭐ |

---

## 🔄 다음 주 준비 (Week 3)

### 선행 학습 필요 사항
- [ ] Spring Cloud Gateway 공식 문서 읽기
- [ ] WebFlux 리액티브 프로그래밍 개념 학습
- [ ] Gateway Filter vs GlobalFilter 차이점 이해

### 질문 리스트
1. Gateway에서 JWT 토큰 검증을 어떻게 처리할까?
2. Route별로 다른 설정을 적용하려면?
3. Rate Limiting은 어떻게 구현할까?

---

## 📝 블로그 작성 가이드

### 추천 주제
1. "[Week 2] JPA와 MyBatis 혼용 전략 - 언제 어떻게 섞어 쓸까?"
2. **"[Week 2] 멀티테넌시 구현 - Shared Database 전략"** ⭐ NEW
3. **"[Week 2] JPA @Filter로 자동 테넌트 격리하기"** ⭐ NEW

### 핵심 포인트
**주제 1 (JPA+MyBatis)**:
1. **문제 정의**: JPA만으로는 복잡한 조회 쿼리 성능 저하
2. **해결 방법**: 도메인 로직은 JPA, 조회는 MyBatis
3. **트러블슈팅**: N+1 문제, FK 없는 설계의 함정
4. **코드 예시**: Study 목록 조회 (Before/After)

**주제 2 (멀티테넌시)**: ⭐ NEW
1. **문제 정의**: 여러 병원/클리닉이 같은 시스템 사용, 데이터 격리 필요
2. **해결 방법**: Shared Database with tenant_id 컬럼
3. **트러블슈팅**: Tenant Filter 미적용 문제
4. **코드 예시**: TenantAwareEntity, TenantFilterAspect

자세한 작성 가이드는 `11_블로그_작성_가이드.md` 참조

---

## 📎 참고 자료

- [Spring Data JPA 공식 문서](https://spring.io/projects/spring-data-jpa)
- [MyBatis 공식 문서](https://mybatis.org/)
- [MySQL 공식 문서](https://dev.mysql.com/doc/)
- [Hibernate 성능 최적화](https://docs.jboss.org/hibernate/orm/6.0/userguide/html_single/Hibernate_User_Guide.html#best-practices)
- [Hibernate @Filter 공식 문서](https://docs.jboss.org/hibernate/orm/6.0/userguide/html_single/Hibernate_User_Guide.html#mapping-column-filter)
- `10_JPA_MyBatis_혼용_전략.md`
- **`12_멀티테넌시_설계_가이드.md`** ⭐ NEW
- `11_블로그_작성_가이드.md`

---

**작성일**: YYYY-MM-DD
**다음 업데이트**: Week 3 완료 후
