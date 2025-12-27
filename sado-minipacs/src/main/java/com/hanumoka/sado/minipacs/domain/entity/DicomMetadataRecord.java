package com.hanumoka.sado.minipacs.domain.entity;

import com.hanumoka.sado.common.entity.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;


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

    // 파일 해시 (SHA-256, 무결성 검증용)
    // → 파일 업로드 후 비동기로 생성 가능
    @Column(name = "file_hash", length = 64, unique = true)
    private String fileHash;

    // DICOM Study UID (빠른 검색용)
    // → DICOM에 없을 수 있음 (연구용 데이터)
    @Column(name = "study_instance_uid", length = 256)
    private String studyInstanceUid;

    // 파일 경로 (원본 DICOM 파일)
    // → 1024자 (Linux 긴 경로 대응)
    @Column(name = "file_path", length = 1024)
    private String filePath;

    // 파일명
    // → 512자 (안전 여유)
    @Column(name = "filename", length = 512)
    private String filename;

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
}
