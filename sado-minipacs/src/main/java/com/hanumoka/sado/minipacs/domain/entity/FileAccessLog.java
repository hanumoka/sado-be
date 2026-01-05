package com.hanumoka.sado.minipacs.domain.entity;

import com.hanumoka.sado.common.entity.TenantAwareEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 파일 접근 감사 로그 엔티티
 *
 * <p>모든 파일 다운로드 및 접근 이벤트를 기록하여 HIPAA/GDPR 준수를 지원합니다.
 *
 * <p>주요 용도:
 * <ul>
 *   <li>HIPAA 감사: 모든 PHI(Protected Health Information) 접근 추적</li>
 *   <li>GDPR 준수: 개인정보 접근 로그 보관</li>
 *   <li>보안 모니터링: 비정상적인 다운로드 패턴 탐지</li>
 *   <li>사용량 분석: 파일별 접근 빈도, 대역폭 사용량</li>
 * </ul>
 *
 * <p>보관 정책:
 * <ul>
 *   <li>최소 3년 보관 (HIPAA 요구사항)</li>
 *   <li>Partitioning: 년도별 테이블 분리 권장</li>
 * </ul>
 */
@Entity
@Table(name = "file_access_log", indexes = {
    @Index(name = "idx_file_access_resource", columnList = "resource_type, resource_id"),
    @Index(name = "idx_file_access_user", columnList = "user_id, accessed_at"),
    @Index(name = "idx_file_access_event_type", columnList = "event_type"),
    @Index(name = "idx_file_access_ip", columnList = "ip_address"),
    @Index(name = "idx_file_access_time", columnList = "accessed_at")
})
@Getter
@Setter
@NoArgsConstructor
public class FileAccessLog extends TenantAwareEntity {

    /**
     * 이벤트 타입
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", length = 32, nullable = false)
    private EventType eventType;

    /**
     * 리소스 타입
     * 예: FILE_ASSET, DICOM_INSTANCE
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "resource_type", length = 32, nullable = false)
    private ResourceType resourceType;

    /**
     * 리소스 ID
     * 예: FileAsset.id, Instance.id
     */
    @Column(name = "resource_id", nullable = false)
    private Long resourceId;

    /**
     * 사용자 ID
     * POC: "admin" (하드코딩)
     * Week 9+: JWT에서 추출한 실제 userId
     */
    @Column(name = "user_id", length = 128, nullable = false)
    private String userId;

    /**
     * 클라이언트 IP 주소
     */
    @Column(name = "ip_address", length = 64)
    private String ipAddress;

    /**
     * User-Agent 헤더
     */
    @Column(name = "user_agent", length = 512)
    private String userAgent;

    /**
     * 접근 시각
     */
    @Column(name = "accessed_at", nullable = false)
    private LocalDateTime accessedAt;

    /**
     * 전송된 바이트 수
     * 다운로드 성공 시에만 기록
     */
    @Column(name = "bytes_transferred")
    private Long bytesTransferred;

    /**
     * 소요 시간 (밀리초)
     * 다운로드 완료 시에만 기록
     */
    @Column(name = "duration_ms")
    private Long durationMs;

    /**
     * 에러 메시지
     * 실패 시에만 기록
     */
    @Column(name = "error_message", length = 1024)
    private String errorMessage;

    /**
     * 추가 컨텍스트 (JSON)
     * 예: {"referrer": "http://...", "downloadReason": "Medical Review"}
     */
    @Column(name = "context", columnDefinition = "TEXT")
    private String context;

    // ========== Enum 정의 ==========

    /**
     * 파일 접근 이벤트 타입
     */
    public enum EventType {
        /**
         * 다운로드 시작
         */
        DOWNLOAD_START,

        /**
         * 다운로드 성공
         */
        DOWNLOAD_SUCCESS,

        /**
         * 다운로드 실패
         */
        DOWNLOAD_FAILED,

        /**
         * 접근 거부 (권한 없음)
         */
        ACCESS_DENIED,

        /**
         * 파일 조회 (메타데이터만)
         */
        METADATA_VIEW
    }

    /**
     * 리소스 타입
     */
    public enum ResourceType {
        /**
         * FileAsset (범용 파일)
         */
        FILE_ASSET,

        /**
         * DICOM Instance
         */
        DICOM_INSTANCE,

        /**
         * AI 분석 결과 파일
         */
        AI_RESULT,

        /**
         * Export 파일
         */
        EXPORT
    }

    // ========== Builder Pattern ==========

    public static class Builder {
        private final FileAccessLog log = new FileAccessLog();

        public Builder eventType(EventType eventType) {
            log.eventType = eventType;
            return this;
        }

        public Builder resourceType(ResourceType resourceType) {
            log.resourceType = resourceType;
            return this;
        }

        public Builder resourceId(Long resourceId) {
            log.resourceId = resourceId;
            return this;
        }

        public Builder userId(String userId) {
            log.userId = userId;
            return this;
        }

        public Builder ipAddress(String ipAddress) {
            log.ipAddress = ipAddress;
            return this;
        }

        public Builder userAgent(String userAgent) {
            log.userAgent = userAgent;
            return this;
        }

        public Builder accessedAt(LocalDateTime accessedAt) {
            log.accessedAt = accessedAt;
            return this;
        }

        public Builder bytesTransferred(Long bytesTransferred) {
            log.bytesTransferred = bytesTransferred;
            return this;
        }

        public Builder durationMs(Long durationMs) {
            log.durationMs = durationMs;
            return this;
        }

        public Builder errorMessage(String errorMessage) {
            log.errorMessage = errorMessage;
            return this;
        }

        public Builder context(String context) {
            log.context = context;
            return this;
        }

        public FileAccessLog build() {
            if (log.eventType == null) {
                throw new IllegalArgumentException("eventType is required");
            }
            if (log.resourceType == null) {
                throw new IllegalArgumentException("resourceType is required");
            }
            if (log.resourceId == null) {
                throw new IllegalArgumentException("resourceId is required");
            }
            if (log.userId == null || log.userId.isEmpty()) {
                throw new IllegalArgumentException("userId is required");
            }
            if (log.accessedAt == null) {
                log.accessedAt = LocalDateTime.now();
            }
            return log;
        }
    }

    public static Builder builder() {
        return new Builder();
    }
}
