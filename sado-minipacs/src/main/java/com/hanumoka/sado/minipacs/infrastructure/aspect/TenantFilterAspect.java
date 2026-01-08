package com.hanumoka.sado.minipacs.infrastructure.aspect;

import com.hanumoka.sado.common.tenant.TenantContext;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.hibernate.Session;
import org.springframework.stereotype.Component;

/**
 * 테넌트 필터 활성화 AOP
 *
 * <p>Repository 메서드 실행 전에 Hibernate @Filter를 활성화하여
 * tenant_id 기반 데이터 격리를 자동으로 적용합니다.
 *
 * <h3>동작 흐름</h3>
 * <pre>
 * 1. TenantFilter → TenantContext.setCurrentTenantId(tenantId)
 * 2. Service → Repository.findAll() 호출
 * 3. TenantFilterAspect (@Before) → Hibernate Filter 활성화
 * 4. Hibernate → SELECT * FROM study WHERE tenant_id = :tenantId
 * </pre>
 *
 * <h3>적용 대상</h3>
 * <ul>
 *   <li>com.hanumoka.sado.minipacs.domain.repository 패키지의 모든 메서드</li>
 *   <li>TenantContext에 tenantId가 설정된 경우에만 활성화</li>
 * </ul>
 *
 * @see com.hanumoka.sado.common.entity.TenantAwareEntity
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class TenantFilterAspect {

    private final EntityManager entityManager;

    /**
     * Repository 메서드 실행 전 Hibernate Filter 활성화
     *
     * <p>TenantContext에 tenantId가 설정되어 있으면
     * Hibernate의 "tenantFilter"를 활성화하여 자동으로
     * WHERE tenant_id = :tenantId 조건을 추가합니다.
     */
    @Before("execution(* com.hanumoka.sado.minipacs.domain.repository.*.*(..))")
    public void enableTenantFilter() {
        if (TenantContext.hasCurrentTenantId()) {
            Long tenantId = TenantContext.getCurrentTenantId();

            Session session = entityManager.unwrap(Session.class);

            // 이미 활성화되어 있으면 스킵
            if (session.getEnabledFilter("tenantFilter") == null) {
                session.enableFilter("tenantFilter")
                       .setParameter("tenantId", tenantId);

                log.debug("TenantFilter enabled: tenantId={}", tenantId);
            }
        }
    }
}
