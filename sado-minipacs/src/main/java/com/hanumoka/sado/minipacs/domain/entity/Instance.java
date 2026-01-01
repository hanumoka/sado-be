package com.hanumoka.sado.minipacs.domain.entity;

import com.hanumoka.sado.common.entity.TenantAwareEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Objects;

/**
 * Application Domain Layer - 영상 엔티티
 * 개별 DICOM 파일 (Instance) 관리
 */
@Entity
@Table(name = "instance", uniqueConstraints = @UniqueConstraint(
        name = "uk_sop_instance_uid", columnNames = {"tenant_id", "sop_instance_uid"}))
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
    private Integer imageRows;

    /**
     * Columns (0028,0011)
     * 이미지 너비 (픽셀)
     */
    @Column(name = "image_columns")
    private Integer imageColumns;

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

    // ========== Builder Pattern ==========

    /**
     * Instance Entity Builder
     *
     * <p>유창한(fluent) API를 제공하여 Instance 객체를 쉽게 생성할 수 있습니다.
     *
     * <p>사용 예시:
     * <pre>
     * {@code
     * Instance instance = Instance.builder()
     *     .series(series)
     *     .sopInstanceUid("1.2.840.113619.2.55.1.123456.789.1")
     *     .sopClassUid("1.2.840.10008.5.1.4.1.1.2")
     *     .instanceNumber(1)
     *     .build();
     * }
     * </pre>
     */
    public static class Builder {
        private final Instance instance = new Instance();

        public Builder series(Series series) {
            instance.series = series;
            return this;
        }

        public Builder sopInstanceUid(String sopInstanceUid) {
            instance.sopInstanceUid = sopInstanceUid;
            return this;
        }

        public Builder sopClassUid(String sopClassUid) {
            instance.sopClassUid = sopClassUid;
            return this;
        }

        public Builder imageRows(Integer imageRows) {
            instance.imageRows = imageRows;
            return this;
        }

        public Builder imageColumns(Integer imageColumns) {
            instance.imageColumns = imageColumns;
            return this;
        }

        public Builder numberOfFrames(Integer numberOfFrames) {
            instance.numberOfFrames = numberOfFrames;
            return this;
        }

        public Builder frameRate(Double frameRate) {
            instance.frameRate = frameRate;
            return this;
        }

        public Builder frameRateSource(String frameRateSource) {
            instance.frameRateSource = frameRateSource;
            return this;
        }

        public Builder instanceNumber(Integer instanceNumber) {
            instance.instanceNumber = instanceNumber;
            return this;
        }

        public Builder storagePath(String storagePath) {
            instance.storagePath = storagePath;
            return this;
        }

        public Builder fileSize(Long fileSize) {
            instance.fileSize = fileSize;
            return this;
        }

        public Builder transcodingStatus(TranscodingStatus transcodingStatus) {
            instance.transcodingStatus = transcodingStatus;
            return this;
        }

        public Builder thumbnailPath(String thumbnailPath) {
            instance.thumbnailPath = thumbnailPath;
            return this;
        }

        public Builder videoPath(String videoPath) {
            instance.videoPath = videoPath;
            return this;
        }

        public Builder storageTier(StorageTier storageTier) {
            instance.storageTier = storageTier;
            return this;
        }

        /**
         * Instance 객체 생성
         *
         * <p>필수 필드 검증을 수행합니다.
         *
         * @return 생성된 Instance 객체
         * @throws NullPointerException 필수 필드가 null인 경우
         */
        public Instance build() {
            Objects.requireNonNull(instance.series, "Series is required");
            Objects.requireNonNull(instance.sopInstanceUid, "SOP Instance UID is required");
            return instance;
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
