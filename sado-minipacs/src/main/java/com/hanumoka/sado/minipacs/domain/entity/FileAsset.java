package com.hanumoka.sado.minipacs.domain.entity;

import com.hanumoka.sado.common.entity.TenantAwareEntity;
import com.hanumoka.sado.minipacs.domain.enums.FileCategory;
import com.hanumoka.sado.minipacs.domain.enums.FileStatus;
import com.hanumoka.sado.minipacs.domain.enums.ReferenceType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 파일 자산 Entity
 *
 * <p>DICOM 파일 외의 모든 파일(AI 결과, 리포트, 임시 파일 등)을 관리합니다.
 *
 * <p>주요 기능:
 * <ul>
 *   <li>다형성 연관 (Polymorphic Association): 다양한 엔티티와 연결 가능</li>
 *   <li>TTL 관리: expiresAt 기반 자동 만료</li>
 *   <li>Storage Tiering: 접근 빈도 기반 HOT/WARM/COLD 전환</li>
 *   <li>무결성 검증: checksum 기반 파일 손상 감지</li>
 * </ul>
 *
 * <p>사용 예시:
 * <pre>
 * {@code
 * // AI 분석 결과 저장
 * FileAsset segmentation = FileAsset.builder()
 *     .category(FileCategory.AI_RESULT)
 *     .referenceType(ReferenceType.AI_ANALYSIS)
 *     .referenceId(aiAnalysis.getId())
 *     .fileName("segmentation_frame_001.png")
 *     .storagePath("/ai_results/123/seg/frame_001.png")
 *     .fileSize(128000L)
 *     .mimeType("image/png")
 *     .checksum("abc123def456...")
 *     .status(FileStatus.ACTIVE)
 *     .build();
 * fileAssetRepository.save(segmentation);
 *
 * // Export 파일 (7일 TTL)
 * FileAsset exportZip = FileAsset.builder()
 *     .category(FileCategory.EXPORT)
 *     .referenceType(ReferenceType.STUDY)
 *     .referenceId(study.getId())
 *     .fileName("study_export.zip")
 *     .storagePath("/exports/789.zip")
 *     .fileSize(1234567890L)
 *     .mimeType("application/zip")
 *     .expiresAt(LocalDateTime.now().plusDays(7))
 *     .status(FileStatus.ACTIVE)
 *     .build();
 * fileAssetRepository.save(exportZip);
 * }
 * </pre>
 */
@Entity
@Table(
    name = "file_assets",
    indexes = {
        @Index(name = "idx_file_assets_reference", columnList = "reference_type, reference_id"),
        @Index(name = "idx_file_assets_category_status", columnList = "category, status"),
        @Index(name = "idx_file_assets_expires_at", columnList = "expires_at"),
        @Index(name = "idx_file_assets_tenant_id", columnList = "tenant_id")
    }
)
@Getter
@Setter
@NoArgsConstructor
public class FileAsset extends TenantAwareEntity {

    // ========== PK ==========
    // ID는 BaseEntity에서 상속받음 (Long id)
    // 컬럼명: id (다른 엔티티와 일관성 유지)

    // ========== 카테고리 및 참조 ==========

    /**
     * 파일 카테고리
     *
     * <p>파일의 종류 (AI_RESULT, CLINICAL_DOC, SYSTEM, EXPORT)
     *
     * <p>보관 정책과 TTL은 카테고리에 따라 결정됩니다:
     * <ul>
     *   <li>AI_RESULT: 영구 보관</li>
     *   <li>CLINICAL_DOC: 영구 보관</li>
     *   <li>SYSTEM: 90일 TTL</li>
     *   <li>EXPORT: 7일 TTL</li>
     * </ul>
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 32)
    private FileCategory category;

    /**
     * 참조 엔티티 타입
     *
     * <p>이 파일이 어떤 엔티티와 연결되어 있는지를 나타냅니다.
     *
     * <p>예시:
     * <ul>
     *   <li>ReferenceType.INSTANCE → Instance 엔티티의 thumbnailPath</li>
     *   <li>ReferenceType.AI_ANALYSIS → AiAnalysis 엔티티의 결과 파일</li>
     *   <li>ReferenceType.STUDY → Study 전체 내보내기 ZIP</li>
     * </ul>
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "reference_type", nullable = false, length = 32)
    private ReferenceType referenceType;

    /**
     * 참조 엔티티 ID
     *
     * <p>referenceType에 해당하는 엔티티의 PK 값
     *
     * <p>예시:
     * <pre>
     * {@code
     * // Instance 썸네일
     * referenceType = ReferenceType.INSTANCE
     * referenceId = 123L  // instance.getId()
     *
     * // AI 분석 결과
     * referenceType = ReferenceType.AI_ANALYSIS
     * referenceId = 456L  // aiAnalysis.getId()
     * }
     * </pre>
     *
     * <p>주의: JPA에서 @ManyToOne처럼 자동 CASCADE가 없으므로,
     * 참조 대상 삭제 시 수동으로 FileAsset도 삭제해야 합니다.
     */
    @Column(name = "reference_id", nullable = false)
    private Long referenceId;

    // ========== 파일 상태 ==========

    /**
     * 파일 상태
     *
     * <p>파일의 생명주기 상태 (ACTIVE, EXPIRED, DELETED, ARCHIVED)
     *
     * <p>상태 전이:
     * <pre>
     * ACTIVE → EXPIRED → DELETED
     *   ↓
     * ARCHIVED
     * </pre>
     *
     * <p>기본값: ACTIVE
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private FileStatus status = FileStatus.ACTIVE;

    // ========== 파일 메타데이터 ==========

    /**
     * 원본 파일명
     *
     * <p>사용자가 업로드한 파일의 원본 이름 (확장자 포함)
     *
     * <p>예시:
     * <ul>
     *   <li>"segmentation_frame_001.png"</li>
     *   <li>"ef_analysis_report.pdf"</li>
     *   <li>"study_export.zip"</li>
     * </ul>
     *
     * <p>주의: 파일명 중복 가능 (storagePath는 유니크)
     */
    @Column(name = "file_name", nullable = false, length = 255)
    private String fileName;

    /**
     * 스토리지 경로
     *
     * <p>실제 파일이 저장된 물리 경로 (절대 경로 또는 S3 Key)
     *
     * <p>경로 규칙:
     * <pre>
     * /{category}/{referenceId}/{fileName}
     *
     * 예시:
     * /ai_results/123/seg/frame_001.png
     * /exports/789.zip
     * /thumbnails/instance_456.jpg
     * </pre>
     *
     * <p>S3 사용 시:
     * <pre>
     * s3://sado-minipacs/ai_results/123/seg/frame_001.png
     * </pre>
     */
    @Column(name = "storage_path", nullable = false, length = 512, unique = true)
    private String storagePath;

    /**
     * 파일 크기 (bytes)
     *
     * <p>물리 파일의 크기 (바이트 단위)
     *
     * <p>용도:
     * <ul>
     *   <li>스토리지 사용량 통계</li>
     *   <li>다운로드 진행률 표시</li>
     *   <li>무결성 검증 (다운로드 후 크기 비교)</li>
     * </ul>
     */
    @Column(name = "file_size", nullable = false)
    private Long fileSize;

    /**
     * MIME 타입
     *
     * <p>파일의 Content-Type (MIME Type)
     *
     * <p>예시:
     * <ul>
     *   <li>"image/png" - Segmentation 이미지</li>
     *   <li>"application/pdf" - 리포트 문서</li>
     *   <li>"application/zip" - Export 압축 파일</li>
     *   <li>"video/mp4" - 비디오 변환 결과</li>
     * </ul>
     *
     * <p>기본값: "application/octet-stream" (알 수 없는 타입)
     */
    @Column(name = "mime_type", nullable = false, length = 128)
    private String mimeType = "application/octet-stream";

    /**
     * 파일 체크섬 (무결성 검증)
     *
     * <p>파일의 MD5 또는 SHA-256 해시값
     *
     * <p>용도:
     * <ul>
     *   <li>파일 무결성 검증 (업로드/다운로드 시)</li>
     *   <li>중복 파일 감지</li>
     *   <li>데이터 손상 탐지</li>
     * </ul>
     *
     * <p>형식:
     * <pre>
     * MD5: 32자 hex (예: "abc123def456...")
     * SHA-256: 64자 hex (예: "a1b2c3d4e5f6...")
     * </pre>
     *
     * <p>nullable: 체크섬 계산이 불가능한 경우 (대용량 파일 등)
     */
    @Column(name = "checksum", length = 128)
    private String checksum;

    // ========== TTL 및 접근 관리 ==========

    /**
     * 만료 일시 (TTL)
     *
     * <p>이 시각 이후 파일은 만료되어 EXPIRED 상태로 전환됩니다.
     *
     * <p>TTL 정책:
     * <ul>
     *   <li>AI_RESULT: null (영구 보관)</li>
     *   <li>CLINICAL_DOC: null (영구 보관)</li>
     *   <li>SYSTEM: 생성일 + 90일</li>
     *   <li>EXPORT: 생성일 + 7일</li>
     * </ul>
     *
     * <p>null인 경우: 만료 없음 (영구 보관)
     */
    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    /**
     * 마지막 접근 일시
     *
     * <p>파일이 마지막으로 다운로드/조회된 시각
     *
     * <p>용도:
     * <ul>
     *   <li>Storage Tiering 결정 (HOT → WARM → COLD)</li>
     *   <li>접근 패턴 분석</li>
     *   <li>사용하지 않는 파일 식별</li>
     * </ul>
     *
     * <p>Tiering 기준:
     * <ul>
     *   <li>최근 30일 접근: HOT Storage</li>
     *   <li>30일~1년 접근: WARM Storage</li>
     *   <li>1년 이상 미접근: COLD Storage (아카이브 대상)</li>
     * </ul>
     */
    @Column(name = "last_accessed_at")
    private LocalDateTime lastAccessedAt;

    /**
     * Storage Tier
     *
     * <p>파일이 저장된 스토리지 계층 (HOT, WARM, COLD)
     *
     * <p>계층별 특징:
     * <ul>
     *   <li>HOT: 즉시 접근 가능, 비용 높음 (S3 Standard)</li>
     *   <li>WARM: 밀리초 접근, 비용 중간 (S3 Infrequent Access)</li>
     *   <li>COLD: 복원 필요 (3-5시간), 비용 낮음 (S3 Glacier)</li>
     * </ul>
     *
     * <p>기본값: "HOT"
     */
    @Column(name = "storage_tier", length = 16)
    private String storageTier = "HOT";

    // ========== 메타데이터 (확장성) ==========

    /**
     * 추가 메타데이터 (JSON)
     *
     * <p>카테고리별 커스텀 메타데이터를 JSON 형식으로 저장
     *
     * <p>예시:
     * <pre>
     * {@code
     * // AI 분석 결과 메타데이터
     * {
     *   "frameIndex": 15,
     *   "ef": 65.5,
     *   "edv": 120.3,
     *   "esv": 45.2,
     *   "modelVersion": "EchoNet-Dynamic-v1.0"
     * }
     *
     * // Export 파일 메타데이터
     * {
     *   "includeImages": true,
     *   "includeReports": true,
     *   "format": "DICOM",
     *   "requestedBy": "user@example.com"
     * }
     * }
     * </pre>
     *
     * <p>JPA 2.1+에서는 @Convert를 사용하여 JSON ↔ Map 자동 변환 가능
     */
    @Column(name = "metadata", columnDefinition = "TEXT")
    private String metadata;

    // ========== Audit Fields (TenantAwareEntity에서 상속) ==========
    // - tenantId: Long (멀티테넌시)
    // - createdAt: LocalDateTime (생성 일시)
    // - updatedAt: LocalDateTime (수정 일시)
    // - createdBy: String (생성자)
    // - updatedBy: String (수정자)

    // ========== 비즈니스 메서드 ==========

    /**
     * 파일이 만료되었는지 확인
     *
     * @return 만료 여부 (true: 만료됨, false: 유효함)
     */
    public boolean isExpired() {
        if (expiresAt == null) {
            return false; // TTL이 없으면 만료 없음
        }
        return LocalDateTime.now().isAfter(expiresAt);
    }

    /**
     * 파일 접근 시 마지막 접근 시각 갱신
     *
     * <p>다운로드 또는 조회 시 호출하여 Storage Tiering 기준 시각 업데이트
     */
    public void markAccessed() {
        this.lastAccessedAt = LocalDateTime.now();
    }

    /**
     * 파일을 만료 상태로 전환
     *
     * <p>TTL 만료 시 RetentionPolicyService에서 호출
     */
    public void markExpired() {
        if (this.status == FileStatus.ACTIVE) {
            this.status = FileStatus.EXPIRED;
        }
    }

    /**
     * 파일을 삭제 상태로 전환
     *
     * <p>물리 파일 삭제 후 호출 (메타데이터는 유지)
     */
    public void markDeleted() {
        this.status = FileStatus.DELETED;
    }

    /**
     * 파일을 아카이브 상태로 전환
     *
     * <p>COLD Storage로 이동 시 호출
     */
    public void markArchived() {
        this.status = FileStatus.ARCHIVED;
        this.storageTier = "COLD";
    }

    /**
     * Storage Tier 업데이트
     *
     * @param tier 새로운 Tier (HOT, WARM, COLD)
     */
    public void updateStorageTier(String tier) {
        Objects.requireNonNull(tier, "Storage tier cannot be null");
        this.storageTier = tier;
    }

    // ========== Builder 패턴 ==========

    /**
     * FileAsset Entity Builder
     */
    public static class Builder {
        private final FileAsset fileAsset = new FileAsset();

        public Builder category(FileCategory category) {
            fileAsset.category = category;
            return this;
        }

        public Builder referenceType(ReferenceType referenceType) {
            fileAsset.referenceType = referenceType;
            return this;
        }

        public Builder referenceId(Long referenceId) {
            fileAsset.referenceId = referenceId;
            return this;
        }

        public Builder status(FileStatus status) {
            fileAsset.status = status;
            return this;
        }

        public Builder fileName(String fileName) {
            fileAsset.fileName = fileName;
            return this;
        }

        public Builder storagePath(String storagePath) {
            fileAsset.storagePath = storagePath;
            return this;
        }

        public Builder fileSize(Long fileSize) {
            fileAsset.fileSize = fileSize;
            return this;
        }

        public Builder mimeType(String mimeType) {
            fileAsset.mimeType = mimeType;
            return this;
        }

        public Builder checksum(String checksum) {
            fileAsset.checksum = checksum;
            return this;
        }

        public Builder expiresAt(LocalDateTime expiresAt) {
            fileAsset.expiresAt = expiresAt;
            return this;
        }

        public Builder lastAccessedAt(LocalDateTime lastAccessedAt) {
            fileAsset.lastAccessedAt = lastAccessedAt;
            return this;
        }

        public Builder storageTier(String storageTier) {
            fileAsset.storageTier = storageTier;
            return this;
        }

        public Builder metadata(String metadata) {
            fileAsset.metadata = metadata;
            return this;
        }

        public FileAsset build() {
            // 필수 필드 검증
            Objects.requireNonNull(fileAsset.category, "Category is required");
            Objects.requireNonNull(fileAsset.referenceType, "Reference type is required");
            Objects.requireNonNull(fileAsset.referenceId, "Reference ID is required");
            Objects.requireNonNull(fileAsset.fileName, "File name is required");
            Objects.requireNonNull(fileAsset.storagePath, "Storage path is required");
            Objects.requireNonNull(fileAsset.fileSize, "File size is required");

            return fileAsset;
        }
    }

    public static Builder builder() {
        return new Builder();
    }
}
