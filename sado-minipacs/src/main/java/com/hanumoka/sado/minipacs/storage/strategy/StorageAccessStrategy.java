package com.hanumoka.sado.minipacs.storage.strategy;

import com.hanumoka.sado.minipacs.storage.dto.FileAccessResponse;

/**
 * 파일 접근 전략 인터페이스
 *
 * <p>Pre-signed URL vs Backend Proxy 추상화
 *
 * <p>Week 4-8 POC: {@link PresignedUrlAccessStrategy} (S3 Pre-signed URL)
 * <br>Week 9+ KingArthur 통합: {@link BackendProxyAccessStrategy} (Backend Proxy)
 *
 * <p>설정 기반 전환: {@code storage.access-strategy} 프로퍼티
 *
 * @see com.hanumoka.sado.minipacs.storage.dto.FileAccessResponse
 */
public interface StorageAccessStrategy {

    /**
     * 파일 접근 방법 반환
     *
     * <p>Pre-signed URL 방식:
     * <ul>
     *   <li>Frontend가 반환된 URL로 SeaweedFS에 직접 접근</li>
     *   <li>Backend 트래픽 최소화 (URL 생성만)</li>
     *   <li>만료 시간 관리 필요 (일반적으로 1시간)</li>
     *   <li>보안 수준: POC 환경 (학습 목적)</li>
     * </ul>
     *
     * <p>Backend Proxy 방식:
     * <ul>
     *   <li>Frontend가 Backend Proxy 엔드포인트로 요청</li>
     *   <li>Backend가 모든 파일 다운로드 중계 (Chunked Transfer)</li>
     *   <li>만료 시간 개념 없음 (요청마다 JWT 검증)</li>
     *   <li>보안 수준: HIPAA/GDPR 준수 (Audit Log, DLP 적용)</li>
     * </ul>
     *
     * @param fileId 파일 ID (SeaweedFS key)
     * @param userId 요청 사용자 ID (Audit Log 및 DLP 검증용)
     * @param tenantId 테넌트 ID (멀티테넌시 격리)
     * @return 파일 접근 방법 (Pre-signed URL or Backend Proxy 엔드포인트)
     * @throws com.hanumoka.sado.minipacs.exception.ResourceNotFoundException 파일이 존재하지 않는 경우
     * @throws com.hanumoka.sado.minipacs.exception.RateLimitException DLP: 다운로드 횟수 제한 초과 (Backend Proxy만)
     * @throws com.hanumoka.sado.minipacs.exception.QuotaExceededException DLP: 일일 다운로드 용량 초과 (Backend Proxy만)
     */
    FileAccessResponse getFileAccess(String fileId, Long userId, Long tenantId);
}
