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
     * Backend Proxy 엔드포인트 반환
     *
     * <p>현재 상태 (Week 8 POC):
     * <ul>
     *   <li>Proxy 엔드포인트만 반환</li>
     *   <li>Audit Log는 FileProxyController에서 처리</li>
     *   <li>DLP는 Week 9+ 구현 예정</li>
     * </ul>
     *
     * <p>Week 9+ 추가 예정:
     * <ol>
     *   <li>Audit Log 기록 (ACCESS_REQUEST): tenantId, userId, fileId, IP</li>
     *   <li>DLP 검증:
     *     <ul>
     *       <li>Rate Limiting: 시간당 다운로드 횟수 제한 (예: 100건)</li>
     *       <li>Quota: 일일 총 다운로드 용량 제한 (예: 10GB)</li>
     *       <li>파일 크기 제한: 단일 파일 최대 크기 (예: 500MB)</li>
     *     </ul>
     *   </li>
     * </ol>
     *
     * <p>Frontend 처리:
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
     * @param fileId 파일 ID (FileAsset ID)
     * @param userId 요청 사용자 ID (현재 미사용, Week 9+ DLP 검증용)
     * @param tenantId 테넌트 ID (현재 미사용, Week 9+ Audit Log용)
     * @return Backend Proxy 엔드포인트
     */
    @Override
    public FileAccessResponse getFileAccess(String fileId, Long userId, Long tenantId) {
        log.debug("Backend Proxy URL requested: fileId={}, userId={}, tenantId={}", fileId, userId, tenantId);

        // Week 8 POC: Proxy 엔드포인트만 반환
        // Week 9+: Audit Log 기록 + DLP 검증 추가 예정

        // TODO(Week 9+): Audit Log 기록
        // auditLogService.logFileAccessRequest(
        //     tenantId,
        //     fileId,
        //     userId,
        //     "ACCESS_REQUEST"
        // );

        // TODO(Week 9+): DLP 검증
        // dlpService.validateDownloadRequest(userId, fileId);

        // Proxy 엔드포인트 반환
        String proxyEndpoint = "/api/files/" + fileId + "/proxy";
        return FileAccessResponse.proxyUrl(proxyEndpoint);
    }
}
