package com.hanumoka.sado.minipacs.domain.entity;

import com.hanumoka.sado.common.entity.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Objects;


/**
 * DICOM Storage Layer - 원본 메타데이터 저장
 * WORM (Write Once Read Many) 정책 적용
 *
 * 설계 원칙:
 * 1. 무조건 저장 허용 (연구용/교육용 DICOM 포함)
 * 2. 필수 필드만 NOT NULL (metadata, immutable만)
 * 3. 누락 필드는 null 저장 (기본값 설정 금지)
 * 4. 길이 제약은 넉넉하게 (실패 방지)
 */
@Entity
@Table(name = "dicom_metadata_record")
@Getter
@Setter
@NoArgsConstructor
public class DicomMetadataRecord extends TenantAwareEntity {

    // ========== 필수 필드 (NOT NULL) ==========

    // 원본 DICOM 메타데이터 (JSON 형식, 절대 수정 금지)
    @Column(name = "metadata", columnDefinition = "JSON", nullable = false)
    private String metadata;

    // WORM 정책 플래그
    @Column(name = "is_immutable", nullable = false)
    private Boolean immutable = true;

    // ========== 선택 필드 (NULL 허용) ==========

    // Instance FK (간접 참조)
    // → DicomMetadataRecord ↔ Instance 1:1 관계
    @Column(name = "instance_id")
    private Long instanceId;

    // 파일 해시 (SHA-256, 무결성 검증용)
    // → 파일 업로드 후 비동기로 생성 가능
    @Column(name = "file_hash", length = 64, unique = true)
    private String fileHash;

    // DICOM Study UID (빠른 검색용)
    // → DICOM에 없을 수 있음 (연구용 데이터)
    @Column(name = "study_instance_uid", length = 256)
    private String studyInstanceUid;

    // DICOM Series Instance UID (0020,000E)
    // DicomMetadataRecord 조회 키 (간접 참조)
    // → DICOM에 없을 수 있음 (연구용 데이터)
    @Column(name = "series_instance_uid", length = 256)
    private String seriesInstanceUid;

    // DICOM SOP Instance UID (0008,0018)
    // DicomMetadataRecord 조회 키 (간접 참조)
    // → DICOM에 없을 수 있음 (연구용 데이터)
    @Column(name = "sop_instance_uid", length = 256)
    private String sopInstanceUid;

    // 파일 경로 (원본 DICOM 파일)
    // → 1024자 (Linux 긴 경로 대응)
    @Column(name = "file_path", length = 1024)
    private String filePath;

    // 파일명
    // → 512자 (안전 여유)
    @Column(name = "filename", length = 512)
    private String filename;

    // 파일 크기 (bytes)
    // → 원본 DICOM 파일 크기
    @Column(name = "file_size")
    private Long fileSize;

    // ========== 비즈니스 메서드 ==========

    /**
     * 파일 해시 설정 (업로드 후 호출)
     */
    public void setFileHashAfterUpload(String hash) {
        if (this.immutable && this.fileHash != null) {
            throw new IllegalStateException(
                    "Cannot modify file hash (WORM policy)"
            );
        }
        this.fileHash = hash;
    }

    // ========== Builder Pattern ==========

    /**
     * DicomMetadataRecord Entity Builder
     *
     * <p>유창한(fluent) API를 제공하여 DicomMetadataRecord 객체를 쉽게 생성할 수 있습니다.
     *
     * <p>사용 예시:
     * <pre>
     * {@code
     * DicomMetadataRecord record = DicomMetadataRecord.builder()
     *     .metadata(jsonMetadata)
     *     .sopInstanceUid("1.2.840.113619.2.55.1.123456.789.1")
     *     .filePath("/path/to/dicom/file.dcm")
     *     .fileSize(1024000L)
     *     .build();
     * }
     * </pre>
     */
    public static class Builder {
        private final DicomMetadataRecord record = new DicomMetadataRecord();

        public Builder metadata(String metadata) {
            record.metadata = metadata;
            return this;
        }

        public Builder immutable(Boolean immutable) {
            record.immutable = immutable;
            return this;
        }

        public Builder instanceId(Long instanceId) {
            record.instanceId = instanceId;
            return this;
        }

        public Builder fileHash(String fileHash) {
            record.fileHash = fileHash;
            return this;
        }

        public Builder studyInstanceUid(String studyInstanceUid) {
            record.studyInstanceUid = studyInstanceUid;
            return this;
        }

        public Builder seriesInstanceUid(String seriesInstanceUid) {
            record.seriesInstanceUid = seriesInstanceUid;
            return this;
        }

        public Builder sopInstanceUid(String sopInstanceUid) {
            record.sopInstanceUid = sopInstanceUid;
            return this;
        }

        public Builder filePath(String filePath) {
            record.filePath = filePath;
            return this;
        }

        public Builder filename(String filename) {
            record.filename = filename;
            return this;
        }

        public Builder fileSize(Long fileSize) {
            record.fileSize = fileSize;
            return this;
        }

        /**
         * DicomMetadataRecord 객체 생성
         *
         * <p>필수 필드 검증을 수행합니다.
         *
         * @return 생성된 DicomMetadataRecord 객체
         * @throws NullPointerException metadata가 null인 경우
         */
        public DicomMetadataRecord build() {
            Objects.requireNonNull(record.metadata, "Metadata is required");
            if (record.immutable == null) {
                record.immutable = true; // 기본값
            }
            return record;
        }
    }

    /**
     * Builder 인스턴스 생성
     *
     * @return 새로운 Builder 인스턴스
     */
    public static Builder builder() {
        return new Builder();
    }
}
