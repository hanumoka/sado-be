package com.hanumoka.sado.common.tenant;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 기본 테넌트 Provider
 *
 * <p>테넌트 ID 조회 우선순위:
 * <ol>
 *   <li>TenantContext (TenantFilter에서 설정한 값)</li>
 *   <li>기본값: 1 (Standalone/POC 모드)</li>
 * </ol>
 *
 * <h3>동작 흐름</h3>
 * <pre>
 * 요청 → TenantFilter → TenantContext.set(tenantId)
 *                              ↓
 *      Service → TenantEntityListener → TenantProvider.getCurrentTenantId()
 *                                              ↓
 *                                       TenantContext.get() → tenantId
 *                                              or
 *                                       DEFAULT_TENANT_ID → 1
 * </pre>
 *
 * <h3>Week 12+ 계획</h3>
 * JWT 기반 멀티테넌시 구현 시 JwtTenantProvider로 대체 또는
 * JWT에서 추출한 값을 TenantContext에 설정하는 방식 유지
 */
@Slf4j
@Component
public class DefaultTenantProvider implements TenantProvider {

    private static final Long DEFAULT_TENANT_ID = 1L;

    @Override
    public Long getCurrentTenantId() {
        // 1순위: TenantContext에서 조회 (TenantFilter에서 설정)
        if (TenantContext.hasCurrentTenantId()) {
            Long tenantId = TenantContext.getCurrentTenantId();
            log.trace("TenantProvider: using TenantContext value: {}", tenantId);
            return tenantId;
        }

        // 2순위: 기본값 반환 (Standalone/POC 모드)
        log.trace("TenantProvider: using default value: {}", DEFAULT_TENANT_ID);
        return DEFAULT_TENANT_ID;
    }
}
