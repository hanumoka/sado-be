package com.hanumoka.sado.minipacs.infrastructure.filter;

import com.hanumoka.sado.common.tenant.TenantContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 테넌트 ID 추출 필터
 *
 * <p>HTTP 요청에서 테넌트 식별자를 추출하여 TenantContext에 설정합니다.
 *
 * <h3>테넌트 ID 추출 우선순위</h3>
 * <ol>
 *   <li>HTTP Header: X-Tenant-Id (권장)</li>
 *   <li>Query Parameter: tenantId (테스트용)</li>
 * </ol>
 *
 * <h3>지원 형식</h3>
 * <ul>
 *   <li>Long ID: X-Tenant-Id: 1</li>
 *   <li>UUID: X-Tenant-Id: 550e8400-e29b-41d4-a716-446655440000 (향후 지원)</li>
 * </ul>
 *
 * <h3>사용 예시</h3>
 * <pre>
 * curl -X POST http://localhost:10201/dicomweb/studies \
 *      -H "X-Tenant-Id: 1" \
 *      -F "file=@sample.dcm"
 * </pre>
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)  // CORS 필터 다음에 실행
public class TenantFilter extends OncePerRequestFilter {

    public static final String TENANT_HEADER = "X-Tenant-Id";
    public static final String TENANT_PARAM = "tenantId";

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        try {
            // 1. 테넌트 ID 추출
            Long tenantId = extractTenantId(request);

            // 2. TenantContext에 설정
            if (tenantId != null) {
                TenantContext.setCurrentTenantId(tenantId);
                log.debug("TenantContext set: tenantId={}, uri={}", tenantId, request.getRequestURI());
            }

            // 3. 다음 필터 실행
            filterChain.doFilter(request, response);

        } finally {
            // 4. 요청 종료 시 반드시 clear (메모리 누수 방지)
            TenantContext.clear();
        }
    }

    /**
     * 요청에서 테넌트 ID 추출
     *
     * @param request HTTP 요청
     * @return 테넌트 ID (없으면 null)
     */
    private Long extractTenantId(HttpServletRequest request) {
        // 1순위: HTTP Header
        String headerValue = request.getHeader(TENANT_HEADER);
        if (headerValue != null && !headerValue.isBlank()) {
            return parseTenantId(headerValue, "header");
        }

        // 2순위: Query Parameter (테스트 편의용)
        String paramValue = request.getParameter(TENANT_PARAM);
        if (paramValue != null && !paramValue.isBlank()) {
            return parseTenantId(paramValue, "param");
        }

        return null;
    }

    /**
     * 테넌트 ID 문자열을 Long으로 파싱
     *
     * @param value 테넌트 ID 문자열
     * @param source 출처 (header/param) - 로깅용
     * @return 파싱된 테넌트 ID (파싱 실패 시 null)
     */
    private Long parseTenantId(String value, String source) {
        try {
            // UUID 형식 체크 (향후 확장)
            if (value.contains("-") && value.length() == 36) {
                // TODO: UUID → Long 변환 로직 (Tenant 테이블 조회 또는 해시)
                log.warn("UUID tenant ID not yet supported: {}", value);
                return null;
            }

            Long tenantId = Long.parseLong(value.trim());
            log.debug("Parsed tenantId from {}: {}", source, tenantId);
            return tenantId;

        } catch (NumberFormatException e) {
            log.warn("Invalid tenant ID format from {}: '{}'", source, value);
            return null;
        }
    }

    /**
     * 필터 적용 제외 경로
     */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();

        // 정적 리소스, 헬스체크 등 제외
        return path.startsWith("/swagger-ui")
                || path.startsWith("/api-docs")
                || path.startsWith("/actuator")
                || path.equals("/favicon.ico");
    }
}
