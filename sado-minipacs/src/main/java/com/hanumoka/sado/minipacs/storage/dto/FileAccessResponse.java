package com.hanumoka.sado.minipacs.storage.dto;

import lombok.Value;

import java.time.Duration;

/**
 * 파일 접근 방법 응답 DTO
 *
 * <p>Pre-signed URL과 Backend Proxy 방식을 통합 표현
 *
 * <p>사용 예시 (Pre-signed URL):
 * <pre>{@code
 * FileAccessResponse response = FileAccessResponse.presignedUrl(
 *     "http://localhost:10405/minipacs/abc123.dcm?signature=...",
 *     Duration.ofHours(1)
 * );
 *
 * // Frontend
 * if (response.getMethod() == AccessMethod.PRESIGNED_URL) {
 *     // Cornerstone3D에서 직접 URL 사용
 *     const imageId = `wadouri:${response.url}`;
 * }
 * }</pre>
 *
 * <p>사용 예시 (Backend Proxy):
 * <pre>{@code
 * FileAccessResponse response = FileAccessResponse.proxyUrl(
 *     "/api/files/abc123/proxy"
 * );
 *
 * // Frontend
 * if (response.getMethod() == AccessMethod.BACKEND_PROXY) {
 *     // Backend Proxy 엔드포인트 사용
 *     const imageId = `wadouri:${API_BASE_URL}${response.url}`;
 * }
 * }</pre>
 */
@Value
public class FileAccessResponse {

    /**
     * 파일 접근 방법
     */
    AccessMethod method;

    /**
     * 접근 URL
     *
     * <ul>
     *   <li>Pre-signed URL: SeaweedFS 직접 접근 URL (완전한 URL)</li>
     *   <li>Backend Proxy: Backend Proxy 엔드포인트 (상대 경로)</li>
     * </ul>
     */
    String url;

    /**
     * URL 만료 시간 (Pre-signed URL만 사용)
     *
     * <p>Backend Proxy 방식은 null (만료 개념 없음)
     */
    Duration expiresIn;

    /**
     * 파일 접근 방법 열거형
     */
    public enum AccessMethod {
        /**
         * Pre-signed URL 방식
         *
         * <p>Frontend가 SeaweedFS에 직접 접근
         *
         * <p>장점:
         * <ul>
         *   <li>Backend 부하 최소화</li>
         *   <li>성능 우수 (Direct 연결)</li>
         * </ul>
         *
         * <p>단점:
         * <ul>
         *   <li>URL 유출 시 테넌트 격리 우회 가능</li>
         *   <li>감사 추적 불가 (Backend 로그 없음)</li>
         *   <li>만료 시간 관리 복잡</li>
         * </ul>
         *
         * <p>사용 시기: Week 4-8 POC (학습 목적)
         */
        PRESIGNED_URL,

        /**
         * Backend Proxy 방식
         *
         * <p>Frontend가 Backend Proxy 엔드포인트로 요청
         *
         * <p>장점:
         * <ul>
         *   <li>완벽한 테넌트 격리 (Per-Request JWT 검증)</li>
         *   <li>완벽한 감사 추적 (Audit Log)</li>
         *   <li>DLP 적용 가능 (Rate Limiting, Quota)</li>
         *   <li>HIPAA/GDPR 규제 준수</li>
         * </ul>
         *
         * <p>단점:
         * <ul>
         *   <li>Backend 부하 증가 (전체 파일 스트리밍)</li>
         *   <li>성능 30% 느림</li>
         * </ul>
         *
         * <p>사용 시기: Week 9+ KingArthur 통합 (프로덕션)
         */
        BACKEND_PROXY
    }

    /**
     * Pre-signed URL 응답 생성 (팩토리 메서드)
     *
     * @param presignedUrl SeaweedFS Pre-signed URL
     * @param expiresIn URL 만료 시간
     * @return Pre-signed URL 응답
     */
    public static FileAccessResponse presignedUrl(String presignedUrl, Duration expiresIn) {
        return new FileAccessResponse(AccessMethod.PRESIGNED_URL, presignedUrl, expiresIn);
    }

    /**
     * Backend Proxy URL 응답 생성 (팩토리 메서드)
     *
     * @param proxyEndpoint Backend Proxy 엔드포인트 (예: "/api/files/abc123/proxy")
     * @return Backend Proxy URL 응답
     */
    public static FileAccessResponse proxyUrl(String proxyEndpoint) {
        return new FileAccessResponse(AccessMethod.BACKEND_PROXY, proxyEndpoint, null);
    }
}
