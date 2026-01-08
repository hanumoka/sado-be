package com.hanumoka.sado.common.tenant;

/**
 * 현재 요청의 테넌트 ID를 ThreadLocal로 관리
 *
 * [동작 흐름]
 * 1. TenantFilter에서 HTTP Header(X-Tenant-Id)를 읽음
 * 2. TenantContext.setCurrentTenantId(tenantId) 호출
 * 3. Service/Repository 레이어에서 TenantContext.getCurrentTenantId()로 조회
 * 4. 요청 종료 시 TenantFilter에서 clear() 호출
 *
 * [주의사항]
 * - ThreadLocal은 요청 종료 시 반드시 clear() 해야 함 (메모리 누수 방지)
 * - 비동기 처리 시 별도 전파 메커니즘 필요 (TaskDecorator 등)
 */
public class TenantContext {

    private static final ThreadLocal<Long> CURRENT_TENANT_ID = new ThreadLocal<>();

    /**
     * 현재 스레드의 테넌트 ID 설정
     *
     * @param tenantId 테넌트 ID
     */
    public static void setCurrentTenantId(Long tenantId) {
        CURRENT_TENANT_ID.set(tenantId);
    }

    /**
     * 현재 스레드의 테넌트 ID 조회
     *
     * @return 테넌트 ID (설정되지 않은 경우 null)
     */
    public static Long getCurrentTenantId() {
        return CURRENT_TENANT_ID.get();
    }

    /**
     * 현재 스레드의 테넌트 ID가 설정되어 있는지 확인
     *
     * @return 설정 여부
     */
    public static boolean hasCurrentTenantId() {
        return CURRENT_TENANT_ID.get() != null;
    }

    /**
     * 현재 스레드의 테넌트 ID 제거
     * 요청 종료 시 반드시 호출해야 함 (메모리 누수 방지)
     */
    public static void clear() {
        CURRENT_TENANT_ID.remove();
    }
}
