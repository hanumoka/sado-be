package com.hanumoka.sado.common.listener;

import com.hanumoka.sado.common.entity.TenantAwareEntity;
import com.hanumoka.sado.common.tenant.TenantProvider;
import jakarta.persistence.PrePersist;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 테넌트 자동 설정 리스너
 *
 * JPA @PrePersist 시점에 자동으로 tenant_id 주입
 */
@Component
public class TenantEntityListener {

    private static TenantProvider tenantProvider;

    /**
     * Spring Bean 주입 (static 필드)
     * JPA Listener는 Spring Bean이 아니므로 static setter 사용
     */
    @Autowired
    public void setTenantProvider(TenantProvider tenantProvider) {
        TenantEntityListener.tenantProvider = tenantProvider;
    }

    /**
     * Entity 저장 전 tenant_id 자동 설정
     */
    @PrePersist
    public void prePersist(TenantAwareEntity entity) {
        // 이미 tenant_id가 설정되어 있으면 스킵
        if (entity.getTenantId() != null) {
            return;
        }

        // TenantProvider에서 현재 tenant_id 조회 후 설정
        Long tenantId = tenantProvider.getCurrentTenantId();
        entity.setTenantId(tenantId);
    }
}
