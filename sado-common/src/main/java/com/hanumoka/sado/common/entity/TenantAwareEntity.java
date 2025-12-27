package com.hanumoka.sado.common.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.FilterDef;
import org.hibernate.annotations.ParamDef;

/**
 * ============================================================================
 * 핵심 개념 요약: 멀티테넌시(Multi-Tenancy)
 * ============================================================================
 *
 * 1. 멀티테넌시란?
 * ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
 * [실생활 비유]
 * - 같은 아파트 건물(애플리케이션)을 여러 가구(병원)가 사용
 * - 각 가구는 자기 집 안만 볼 수 있고, 다른 집은 볼 수 없음
 * - tenant_id = 각 가구의 호수
 *
 * [SADO 프로젝트]
 * - A병원, B병원, C병원이 같은 SADO 시스템 사용
 * - A병원 의사는 A병원 환자 데이터만 조회 가능
 * - B병원 의사는 B병원 환자 데이터만 조회 가능
 * - tenant_id로 데이터 격리
 *
 *
 * 2. Hibernate @Filter의 마법
 * ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
 * [일반적인 방법 - 불편함]
 * // 매번 WHERE tenant_id = ? 조건을 직접 작성해야 함
 * @Query("SELECT s FROM Study s WHERE s.tenantId = :tenantId")
 * List<Study> findAll(@Param("tenantId") Long tenantId);
 *
 * [Hibernate @Filter 사용 - 자동화]
 * // WHERE 조건 없이 작성해도
 * studyRepository.findAll();
 *
 * // Hibernate가 자동으로 조건 추가!
 * // 실제 SQL: SELECT * FROM study WHERE tenant_id = 1;
 *
 * [장점]
 * - 개발자가 매번 WHERE 조건 작성 불필요
 * - 실수로 조건을 빼먹어도 안전 (자동으로 추가됨)
 * - 보안 강화 (다른 테넌트 데이터 접근 원천 차단)
 *
 *
 * 3. 전체 흐름 (Request → Response)
 * ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
 * [1] 사용자 로그인 (A병원 의사)
 *      ↓
 * [2] Keycloak JWT 발급 (tenant_id = 1 포함)
 *      ↓
 * [3] Gateway → Service: JWT 전파
 *      ↓
 * [4] JwtTenantFilter: JWT에서 tenant_id 추출
 *      ↓
 * [5] TenantContext.setCurrentTenantId(1); 설정
 *      ↓
 * [6] TenantFilterAspect: Hibernate Filter 활성화
 *      ↓
 * [7] studyRepository.findAll(); 호출
 *      ↓
 * [8] Hibernate: SELECT * FROM study WHERE tenant_id = 1;
 *      ↓
 * [9] A병원 데이터만 반환 (B병원 데이터는 필터링됨)
 *
 *
 * 4. 앞으로 구현할 컴포넌트
 * ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
 * ┌─────────────────────┬───────────────────────────────────┬──────────────┐
 * │ 컴포넌트             │ 역할                               │ 구현 시기     │
 * ├─────────────────────┼───────────────────────────────────┼──────────────┤
 * │ TenantEntityListener│ Entity 저장 시 tenant_id 자동 주입 │ Step 3 (곧)  │
 * │ TenantContext       │ ThreadLocal로 현재 tenant_id 관리  │ Week 3 후반  │
 * │ TenantFilterAspect  │ Repository 실행 전 Filter 활성화   │ Week 3 후반  │
 * │ JwtTenantFilter     │ JWT에서 tenant_id 추출             │ Week 12      │
 * └─────────────────────┴───────────────────────────────────┴──────────────┘
 *
 *
 * 5. 데이터베이스 구조 예시
 * ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
 * study 테이블:
 * ┌──────────┬───────────┬────────────┬─────────────┬─────────┐
 * │ study_id │ tenant_id │ patient_id │ study_date  │ status  │
 * ├──────────┼───────────┼────────────┼─────────────┼─────────┤
 * │ 1        │ 1         │ P001       │ 2025-12-20  │ DONE    │  ← A병원
 * │ 2        │ 1         │ P002       │ 2025-12-21  │ DONE    │  ← A병원
 * │ 3        │ 2         │ P101       │ 2025-12-22  │ PENDING │  ← B병원
 * │ 4        │ 2         │ P102       │ 2025-12-23  │ DONE    │  ← B병원
 * └──────────┴───────────┴────────────┴─────────────┴─────────┘
 *
 * A병원 의사가 조회하면 (tenant_id = 1):
 * → study_id: 1, 2만 조회됨 (자동 필터링!)
 *
 * B병원 의사가 조회하면 (tenant_id = 2):
 * → study_id: 3, 4만 조회됨 (자동 필터링!)
 *
 * ============================================================================
 */

/**
 * 멀티테넌시를 지원하는 Base Entity
 *
 * [멀티테넌시란?]
 * - 여러 조직(병원, 클리닉)이 같은 애플리케이션을 사용하지만 데이터는 완전히 격리되는 구조
 * - 예: A병원 사용자는 A병원 데이터만, B병원 사용자는 B병원 데이터만 조회 가능
 *
 * [tenant_id란?]
 * - 각 조직(병원)을 구분하는 고유 ID
 * - 모든 데이터(Study, Series, Instance)에 tenant_id 컬럼이 포함됨
 * - 예: tenant_id = 1 (서울대병원), tenant_id = 2 (고대병원)
 *
 * [이 클래스의 역할]
 * 1. tenant_id 필드를 모든 Entity에 자동 추가
 * 2. 조회 시 자동으로 현재 테넌트의 데이터만 필터링 (@Filter)
 * 3. 저장 시 자동으로 tenant_id 주입 (@EntityListeners)
 *
 * [사용 예시]
 * Study, Series, Instance 등 모든 도메인 Entity가 이 클래스를 상속받음
 */
@Getter
@Setter
@MappedSuperclass  // 이 클래스는 테이블로 생성되지 않고, 자식 Entity에게 필드만 상속

/**
 * @FilterDef: Hibernate Filter 정의
 *
 * [Hibernate Filter란?]
 * - JPA 표준이 아닌 Hibernate의 고급 기능
 * - 모든 SELECT 쿼리에 자동으로 WHERE 조건을 추가하는 기능
 * - 코드 레벨에서는 특별한 조건 없이 조회해도, DB 쿼리에는 조건이 자동 추가됨
 *
 * [동작 원리]
 * 1. 필터 이름: "tenantFilter"
 * 2. 파라미터: tenantId (Long 타입)
 * 3. 이 필터가 활성화되면, 모든 쿼리에 "tenant_id = :tenantId" 조건이 자동 추가
 *
 * [언제 활성화되는가?]
 * - TenantFilterAspect에서 Repository 메서드 실행 전에 자동 활성화 (Week 3 후반 구현 예정)
 * - TenantContext에서 현재 로그인한 사용자의 tenant_id를 가져와 필터에 설정
 *
 * [예시]
 * 코드:   studyRepository.findAll();
 * 실제 SQL: SELECT * FROM study WHERE tenant_id = 1;  (자동 추가!)
 */
@FilterDef(
    name = "tenantFilter",  // 필터 이름 (활성화 시 이 이름으로 참조)
    parameters = @ParamDef(name = "tenantId", type = Long.class)  // 필터 파라미터 정의
)

/**
 * @Filter: FilterDef로 정의한 필터를 실제로 적용
 *
 * [condition 설명]
 * - "tenant_id = :tenantId"
 * - 이 조건이 모든 SELECT 쿼리의 WHERE 절에 자동 추가됨
 * - :tenantId는 FilterDef에서 정의한 파라미터 (런타임에 실제 값으로 치환됨)
 *
 * [보안 효과]
 * - A병원 사용자가 B병원 데이터를 조회하려 해도 자동으로 차단됨
 * - 개발자가 실수로 WHERE 조건을 빼먹어도 안전함
 */
@Filter(
    name = "tenantFilter",  // FilterDef에서 정의한 필터 이름과 동일해야 함
    condition = "tenant_id = :tenantId"  // 실제 SQL WHERE 조건
)

/**
 * @EntityListeners: Entity 생명주기 이벤트 리스너 등록
 *
 * [TenantEntityListener 역할]
 * - Entity가 저장(@PrePersist)되기 직전에 tenant_id를 자동 주입
 * - TenantContext에서 현재 로그인한 사용자의 tenant_id를 가져와 설정
 *
 * [왜 필요한가?]
 * - 개발자가 매번 study.setTenantId(...)를 호출하지 않아도 됨
 * - 실수로 tenant_id를 설정하지 않는 경우를 방지
 *
 * [동작 흐름]
 * 1. 사용자가 Study 생성: Study study = new Study(...);
 * 2. JPA save 호출: studyRepository.save(study);
 * 3. @PrePersist 시점에 TenantEntityListener 실행
 * 4. TenantContext.getCurrentTenantId()로 현재 테넌트 ID 조회
 * 5. study.setTenantId(1); 자동 설정
 * 6. DB에 INSERT: INSERT INTO study (..., tenant_id) VALUES (..., 1);
 */
@EntityListeners(TenantEntityListener.class)
public abstract class TenantAwareEntity extends BaseEntity {

    /**
     * 테넌트 ID (병원/조직 식별자)
     *
     * [nullable = false]
     * - tenant_id는 반드시 값이 있어야 함 (NULL 불가)
     * - TenantEntityListener가 자동으로 설정하므로 개발자는 신경 쓸 필요 없음
     *
     * [데이터베이스 컬럼명]
     * - DB 테이블에는 "tenant_id" 컬럼으로 생성됨
     *
     * [인덱싱]
     * - tenant_id는 모든 쿼리에서 사용되므로 인덱스 필수
     * - 보통 (tenant_id, id) 복합 인덱스 사용
     * - 예: CREATE INDEX idx_tenant_study ON study (tenant_id, study_id);
     */
    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    /*
     * [상속 구조]
     * TenantAwareEntity extends BaseEntity
     *
     * [상속받은 필드] (BaseEntity로부터)
     * - Long id
     * - LocalDateTime createdAt
     * - LocalDateTime updatedAt
     *
     * [추가한 필드] (이 클래스)
     * - Long tenantId
     *
     * [최종 필드 구성]
     * Study Entity가 TenantAwareEntity를 상속받으면:
     * - id, createdAt, updatedAt, tenantId 필드를 자동으로 가짐
     */
}
