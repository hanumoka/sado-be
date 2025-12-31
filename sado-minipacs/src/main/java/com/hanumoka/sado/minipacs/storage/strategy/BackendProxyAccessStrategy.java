package com.hanumoka.sado.minipacs.storage.strategy;

import com.hanumoka.sado.minipacs.storage.dto.FileAccessResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Backend Proxy 파일 접근 전략 (Week 9+ 구현 예정)
 *
 * <p>Frontend가 Backend Proxy 엔드포인트로 요청하는 방식
 *
 * <p>사용 시기: Week 9+ KingArthur EMR 통합 (프로덕션)
 *
 * <p>활성화 조건: {@code storage.access-strategy = backend-proxy}
 *
 * <p>장점 (프로덕션 필수):
 * <ul>
 *   <li>완벽한 테넌트 격리 (Per-Request JWT 검증)</li>
 *   <li>완벽한 감사 추적 (Audit Log: User ID, IP, 시간 기록)</li>
 *   <li>DLP 적용 (Rate Limiting, Quota, 파일 크기 제한)</li>
 *   <li>HIPAA/GDPR 규제 준수 (의료 데이터 접근 로그 3년 보관)</li>
 *   <li>네트워크 격리 (SeaweedFS backend-net만 접근, Public IP 없음)</li>
 *   <li>URL 만료 문제 해결 (만료 개념 없음)</li>
 * </ul>
 *
 * <p>단점:
 * <ul>
 *   <li>Backend 부하 증가 (전체 파일 스트리밍)</li>
 *   <li>성능 30% 느림 (800ms vs 500ms, Chunked Transfer 오버헤드)</li>
 *   <li>구현 복잡도 증가 (Audit Log, DLP, Streaming)</li>
 * </ul>
 *
 * <p>Week 9+ 구현 완성 항목:
 * <ul>
 *   <li>TODO 1: AuditLogService 주입 및 로그 기록</li>
 *   <li>TODO 2: DlpService 주입 및 Rate Limiting/Quota 검증</li>
 *   <li>TODO 3: FileProxyController Chunked Transfer 구현</li>
 *   <li>TODO 4: Audit Log 스키마 설계 및 마이그레이션</li>
 *   <li>TODO 5: Docker Compose 네트워크 격리 (backend-net)</li>
 *   <li>TODO 6: HTTPS 강제 (Let's Encrypt)</li>
 *   <li>TODO 7: Keycloak Token Exchange (KingArthur Token → SADO Token)</li>
 * </ul>
 *
 * <p>POC vs 프로덕션 전환:
 * <pre>
 * # POC (Week 4-8)
 * storage:
 *   access-strategy: presigned-url
 *
 * # 프로덕션 (Week 9+)
 * storage:
 *   access-strategy: backend-proxy  # 설정만 변경, 코드 변경 0줄!
 * </pre>
 *
 * @see PresignedUrlAccessStrategy
 * @see com.hanumoka.sado.minipacs.controller.FileProxyController
 */
@Slf4j
@Component
@ConditionalOnProperty(
    name = "storage.access-strategy",
    havingValue = "backend-proxy"
)
@RequiredArgsConstructor
public class BackendProxyAccessStrategy implements StorageAccessStrategy {

    // TODO(Week 9+): AuditLogService 주입
    // private final AuditLogService auditLogService;

    // TODO(Week 9+): DlpService 주입
    // private final DlpService dlpService;

    /**
     * Backend Proxy 엔드포인트 반환 (Week 9+ 구현 완성 예정)
     *
     * <p>현재 상태: 골격만 (UnsupportedOperationException)
     *
     * <p>Week 9+ 완성 흐름:
     * <ol>
     *   <li>Audit Log 기록 (ACCESS_REQUEST): tenantId, userId, fileId, IP</li>
     *   <li>DLP 검증:
     *     <ul>
     *       <li>Rate Limiting: 시간당 다운로드 횟수 제한 (예: 100건)</li>
     *       <li>Quota: 일일 총 다운로드 용량 제한 (예: 10GB)</li>
     *       <li>파일 크기 제한: 단일 파일 최대 크기 (예: 500MB)</li>
     *     </ul>
     *   </li>
     *   <li>Proxy 엔드포인트 반환: {@code /api/files/{fileId}/proxy}</li>
     * </ol>
     *
     * <p>Frontend 처리 (Week 9+):
     * <pre>{@code
     * // React - Cornerstone3D
     * const response = await api.get(`/api/v1/instances/${instanceId}/download`);
     *
     * if (response.data.method === 'BACKEND_PROXY') {
     *   const imageId = `wadouri:${API_BASE_URL}${response.data.url}`;
     *   // JWT 자동 포함 (axios interceptor)
     *   await cornerstone.loadAndCacheImage(imageId);
     * }
     * }</pre>
     *
     * <p>Audit Log 스키마 (Week 9+ 구현):
     * <pre>{@code
     * CREATE TABLE audit_logs (
     *   id BIGINT AUTO_INCREMENT PRIMARY KEY,
     *   tenant_id BIGINT NOT NULL,
     *   event_type VARCHAR(50) NOT NULL, -- 'FILE_DOWNLOAD'
     *   resource_type VARCHAR(50) NOT NULL, -- 'DICOM_INSTANCE'
     *   resource_id VARCHAR(255) NOT NULL, -- fileId
     *   user_id BIGINT NOT NULL,
     *   user_name VARCHAR(255),
     *   ip_address VARCHAR(45), -- IPv6 지원
     *   user_agent TEXT,
     *   timestamp DATETIME(6) NOT NULL,
     *   status VARCHAR(20) NOT NULL, -- 'SUCCESS', 'DENIED', 'FAILED'
     *   metadata JSON, -- 파일 크기, 다운로드 시간 등
     *   INDEX idx_tenant_time (tenant_id, timestamp),
     *   INDEX idx_user_time (user_id, timestamp)
     * );
     * }</pre>
     *
     * @param fileId 파일 ID (SeaweedFS S3 key)
     * @param userId 요청 사용자 ID (Audit Log 및 DLP 검증용)
     * @param tenantId 테넌트 ID (멀티테넌시 격리)
     * @return Backend Proxy 엔드포인트 (Week 9+ 구현 시)
     * @throws UnsupportedOperationException Week 4-8 POC에서 호출 시 (골격만 존재)
     * @throws com.hanumoka.sado.minipacs.exception.RateLimitException Week 9+: DLP 다운로드 횟수 제한 초과
     * @throws com.hanumoka.sado.minipacs.exception.QuotaExceededException Week 9+: DLP 일일 다운로드 용량 초과
     */
    @Override
    public FileAccessResponse getFileAccess(String fileId, Long userId, Long tenantId) {
        log.warn("BackendProxyAccessStrategy called but not implemented yet. " +
            "Current phase: Week 4-8 POC. Implementation planned for Week 9+ KingArthur integration.");

        // TODO(Week 9+): 구현 완성
        // Step 1: Audit Log 기록
        // auditLogService.logFileAccessRequest(
        //     tenantId,
        //     fileId,
        //     userId,
        //     "ACCESS_REQUEST"
        // );

        // Step 2: DLP 검증
        // dlpService.validateDownloadRequest(userId, fileId);

        // Step 3: Proxy 엔드포인트 반환
        // return FileAccessResponse.proxyUrl("/api/files/" + fileId + "/proxy");

        throw new UnsupportedOperationException(
            "Backend Proxy not implemented yet. " +
            "Use Pre-signed URL in POC (storage.access-strategy=presigned-url). " +
            "Implementation planned for Week 9+ KingArthur integration."
        );
    }
}
