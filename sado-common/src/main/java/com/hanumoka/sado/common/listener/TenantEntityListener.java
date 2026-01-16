package com.hanumoka.sado.common.listener;

import com.hanumoka.sado.common.entity.TenantAwareEntity;
import com.hanumoka.sado.common.tenant.TenantProvider;
import jakarta.persistence.PrePersist;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 테넌트 자동 설정 리스너
 *
 * JPA @PrePersist 시점에 자동으로 tenant_id 주입
 */
@Slf4j
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

        // TenantProvider null 체크 (Spring 초기화 전 호출 방지)
        if (tenantProvider == null) {
            log.error("TenantProvider가 초기화되지 않았습니다. Entity: {}", entity.getClass().getSimpleName());
            throw new IllegalStateException(
                    "TenantProvider가 초기화되지 않았습니다. Spring Context가 완전히 로드된 후 Entity를 저장해주세요.");
        }

        // TenantProvider에서 현재 tenant_id 조회 후 설정
        Long tenantId = tenantProvider.getCurrentTenantId();

        // tenant_id null 체크 (TenantContext가 설정되지 않은 경우)
        if (tenantId == null) {
            log.error("TenantContext에서 tenant_id를 가져올 수 없습니다. Entity: {}", entity.getClass().getSimpleName());
            throw new IllegalStateException(
                    "tenant_id가 설정되지 않았습니다. TenantContext.setCurrentTenantId()를 먼저 호출해주세요.");
        }

        entity.setTenantId(tenantId);
    }
}
