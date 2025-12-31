package com.hanumoka.sado.minipacs.storage.strategy;

import com.hanumoka.sado.minipacs.storage.dto.FileAccessResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

import java.time.Duration;

/**
 * Pre-signed URL 파일 접근 전략
 *
 * <p>Frontend가 SeaweedFS에 직접 접근하는 방식
 *
 * <p>사용 시기: Week 4-8 POC (학습 목적)
 *
 * <p>활성화 조건: {@code storage.access-strategy = presigned-url} (기본값)
 *
 * <p>장점:
 * <ul>
 *   <li>Backend 부하 최소화 (URL 생성만, 다운로드 트래픽 없음)</li>
 *   <li>성능 우수 (Client ↔ Storage 직접 연결)</li>
 *   <li>AWS S3 표준 패턴 학습</li>
 *   <li>OHIF Viewer 등 DICOMweb 표준 클라이언트 호환</li>
 * </ul>
 *
 * <p>단점 (POC 한계):
 * <ul>
 *   <li>URL 유출 시 테넌트 격리 우회 가능</li>
 *   <li>감사 추적 불가 (Backend 로그 없음)</li>
 *   <li>만료 시간 관리 복잡 (의사 판독 중 만료 시 재요청 필요)</li>
 *   <li>HIPAA/GDPR 규제 준수 미충족</li>
 * </ul>
 *
 * <p>프로덕션 전환:
 * <ul>
 *   <li>Week 9+ KingArthur 통합 시 {@link BackendProxyAccessStrategy}로 전환</li>
 *   <li>설정만 변경: {@code storage.access-strategy = backend-proxy}</li>
 *   <li>코드 변경 없음 (Infrastructure 추상화 덕분)</li>
 * </ul>
 *
 * @see BackendProxyAccessStrategy
 * @see com.hanumoka.sado.minipacs.storage.dto.FileAccessResponse
 */
@Slf4j
@Component
@ConditionalOnProperty(
    name = "storage.access-strategy",
    havingValue = "presigned-url",
    matchIfMissing = true // POC 기본값
)
@RequiredArgsConstructor
public class PresignedUrlAccessStrategy implements StorageAccessStrategy {

    private final S3Presigner s3Presigner;

    @Value("${seaweedfs.s3.bucket}")
    private String bucket;

    /**
     * Pre-signed URL 만료 시간 (기본 1시간)
     *
     * <p>고려사항:
     * <ul>
     *   <li>너무 짧음 (15분): 의사 판독 중 만료 → 사용자 경험 저하</li>
     *   <li>너무 길음 (24시간): URL 유출 시 보안 위험 증가</li>
     *   <li>권장: 1시간 (의사 판독 완료 시간 고려)</li>
     * </ul>
     */
    private static final Duration PRESIGNED_URL_DURATION = Duration.ofHours(1);

    /**
     * Pre-signed URL 생성
     *
     * <p>흐름:
     * <ol>
     *   <li>S3 GetObject 요청 생성 (bucket + fileId)</li>
     *   <li>Presign 요청 생성 (만료 시간 1시간)</li>
     *   <li>S3Presigner로 서명된 URL 생성</li>
     *   <li>Frontend에 URL 반환</li>
     * </ol>
     *
     * <p>Frontend 처리:
     * <pre>{@code
     * // React - Cornerstone3D
     * const response = await api.get(`/api/v1/instances/${instanceId}/download`);
     *
     * if (response.data.method === 'PRESIGNED_URL') {
     *   const imageId = `wadouri:${response.data.url}`;
     *   await cornerstone.loadAndCacheImage(imageId);
     * }
     * }</pre>
     *
     * <p>보안 주의사항 (POC 한계):
     * <ul>
     *   <li>URL 유출 시: Tenant 격리 우회 가능 (1시간 동안 누구나 접근 가능)</li>
     *   <li>감사 추적: Backend는 URL 발급만 기록, 실제 다운로드 여부 불명</li>
     *   <li>SeaweedFS 로그: IP 주소만 기록, User ID 없음</li>
     * </ul>
     *
     * @param fileId 파일 ID (SeaweedFS S3 key)
     * @param userId 요청 사용자 ID (현재 미사용, Week 9+ Backend Proxy에서 사용)
     * @param tenantId 테넌트 ID (현재 미사용, Week 9+ Backend Proxy에서 사용)
     * @return Pre-signed URL 응답 (URL + 만료 시간)
     */
    @Override
    public FileAccessResponse getFileAccess(String fileId, Long userId, Long tenantId) {
        log.debug("Generating Pre-signed URL for fileId={}, userId={}, tenantId={}",
            fileId, userId, tenantId);

        // 1. S3 GetObject 요청 생성
        GetObjectRequest getObjectRequest = GetObjectRequest.builder()
            .bucket(bucket)
            .key(fileId)
            .build();

        // 2. Presign 요청 생성 (만료 시간 설정)
        GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
            .signatureDuration(PRESIGNED_URL_DURATION) // 1시간 유효
            .getObjectRequest(getObjectRequest)
            .build();

        // 3. Pre-signed URL 생성
        PresignedGetObjectRequest presignedRequest = s3Presigner.presignGetObject(presignRequest);
        String presignedUrl = presignedRequest.url().toString();

        log.info("Pre-signed URL generated: fileId={}, url={}, expiresIn={}",
            fileId, presignedUrl, PRESIGNED_URL_DURATION);

        // 4. Response 반환
        return FileAccessResponse.presignedUrl(presignedUrl, PRESIGNED_URL_DURATION);
    }
}
