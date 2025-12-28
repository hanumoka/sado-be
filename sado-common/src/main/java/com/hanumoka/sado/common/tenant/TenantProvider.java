package com.hanumoka.sado.common.tenant;

/**
 * 현재 요청의 테넌트 ID를 제공하는 인터페이스
 *
 * 구현체 예시:
 * - JwtTenantProvider: JWT에서 tenant_id claim 추출
 * - DefaultTenantProvider: 기본값(1) 반환 (Standalone 모드)
 */
public interface TenantProvider {

    /**
     * 현재 요청의 테넌트 ID 조회
     *
     * @return 테넌트 ID (NULL 불가)
     * @throws IllegalStateException 테넌트 ID를 찾을 수 없는 경우
     */
    Long getCurrentTenantId();
}
