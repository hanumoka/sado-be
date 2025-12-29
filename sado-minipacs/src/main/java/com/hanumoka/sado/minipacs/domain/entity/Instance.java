package com.hanumoka.sado.minipacs.domain.entity;

import com.hanumoka.sado.common.entity.TenantAwareEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Application Domain Layer - 영상 엔티티
 * 개별 DICOM 파일 (Instance) 관리
 */
@Entity
@Table(name = "instance")
@Getter
@Setter
@NoArgsConstructor
public class Instance extends TenantAwareEntity {

    // ========== DICOM UID (간접 참조) ==========

    /**
     * DICOM SOPInstanceUID (0008,0018)
     * DicomMetadataRecord 조회 키 (간접 참조)
     */
    @Column(name = "sop_instance_uid", length = 256)
    private String sopInstanceUid;

    /**
     * SOP Class UID (0008,0016)
     * DICOM 객체 유형 (예: Ultrasound Image Storage)
     */
    @Column(name = "sop_class_uid", length = 128)
    private String sopClassUid;

    // ========== Series 관계 (Application Layer 내 직접 FK) ==========

    /**
     * 소속 시리즈
     * N개의 영상 → 1개의 시리즈
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "series_id", nullable = false)
    private Series series;

    // ========== 이미지 정보 ==========

    /**
     * Rows (0028,0010)
     * 이미지 높이 (픽셀)
     */
    @Column(name = "image_rows")
    private Integer rows;

    /**
     * Columns (0028,0011)
     * 이미지 너비 (픽셀)
     */
    @Column(name = "image_columns")
    private Integer columns;

    /**
     * Number of Frames (0028,0008)
     * 멀티프레임 영상의 프레임 수
     * null: 단일 프레임
     */
    @Column(name = "number_of_frames")
    private Integer numberOfFrames;

    // ========== 계산값 (Application Layer) ==========

    /**
     * Frame Rate (fps)
     * 계산값: FrameTime 또는 CineRate에서 파생
     *
     * 중요: DICOM 원본에는 저장하지 않음 (Application Layer만)
     */
    @Column(name = "frame_rate")
    private Double frameRate;

    /**
     * Frame Rate Source
     * 계산 출처 기록 (추적성)
     * - FRAME_TIME: FrameTime 태그에서 계산 (1000.0 / frameTime)
     * - CINE_RATE: CineRate 태그에서 직접 사용
     */
    @Column(name = "frame_rate_source", length = 32)
    private String frameRateSource;

    /**
     * Instance Number (0020,0013)
     * 시리즈 내 영상 번호
     */
    @Column(name = "instance_number")
    private Integer instanceNumber;

    // ========== 스토리지 ==========

    /**
     * Storage Path
     * SeaweedFS 또는 S3 경로
     * 예: /seaweedfs/3/01/fid12345
     */
    @Column(name = "storage_path", length = 512)
    private String storagePath;

    /**
     * File Size (bytes)
     * 원본 DICOM 파일 크기
     */
    @Column(name = "file_size")
    private Long fileSize;

    // ========== 트랜스코딩 ==========

    /**
     * Transcoding Status
     * 썸네일/비디오 생성 상태
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "transcoding_status", length = 32)
    private TranscodingStatus transcodingStatus;

    /**
     * Thumbnail Path
     * 썸네일 이미지 경로 (JPEG, 256x256)
     */
    @Column(name = "thumbnail_path", length = 512)
    private String thumbnailPath;

    /**
     * Video Path
     * 비디오 파일 경로 (MP4, 멀티프레임용)
     */
    @Column(name = "video_path", length = 512)
    private String videoPath;

    // ========== 스토리지 티어링 ==========

    /**
     * Storage Tier
     * 스토리지 계층
     * - HOT: 로컬 SSD (생성 후 30일)
     * - WARM: 표준 HDD (30일~1년)
     * - COLD: Archive (1년 이후)
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "storage_tier", length = 16)
    private StorageTier storageTier;

    // ========== Enum 정의 ==========

    /**
     * 트랜스코딩 상태
     */
    public enum TranscodingStatus {
        /**
         * 트랜스코딩 불필요 (단일 프레임)
         */
        NONE,

        /**
         * 트랜스코딩 대기 중
         */
        PENDING,

        /**
         * 트랜스코딩 진행 중
         */
        PROCESSING,

        /**
         * 트랜스코딩 완료
         */
        COMPLETED,

        /**
         * 트랜스코딩 실패
         */
        FAILED
    }

    /**
     * 스토리지 티어
     */
    public enum StorageTier {
        /**
         * HOT: 로컬 SSD (빠른 접근)
         */
        HOT,

        /**
         * WARM: 표준 HDD (중간 접근)
         */
        WARM,

        /**
         * COLD: Archive (드문 접근)
         */
        COLD
    }
}
