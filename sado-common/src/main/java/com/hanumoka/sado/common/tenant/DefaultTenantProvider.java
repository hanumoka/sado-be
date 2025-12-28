package com.hanumoka.sado.common.tenant;

import org.springframework.stereotype.Component;

/**
 * 기본 테넌트 Provider (Standalone 모드)
 *
 * 항상 tenant_id = 1 반환
 * Week 12 이전까지 사용 (멀티테넌시 비활성화)
 */
@Component
public class DefaultTenantProvider implements TenantProvider {

    private static final Long DEFAULT_TENANT_ID = 1L;

    @Override
    public Long getCurrentTenantId() {
        return DEFAULT_TENANT_ID;
    }
}
