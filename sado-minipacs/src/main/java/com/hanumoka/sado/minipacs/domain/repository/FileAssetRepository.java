package com.hanumoka.sado.minipacs.domain.repository;

import com.hanumoka.sado.minipacs.domain.entity.FileAsset;
import com.hanumoka.sado.minipacs.domain.enums.FileCategory;
import com.hanumoka.sado.minipacs.domain.enums.FileStatus;
import com.hanumoka.sado.minipacs.domain.enums.ReferenceType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * FileAsset Repository
 *
 * <p>파일 자산 조회 및 관리
 *
 * <p>멀티테넌시: Hibernate Filter 자동 적용
 */
@Repository
public interface FileAssetRepository extends JpaRepository<FileAsset, Long> {

    /**
     * 참조 엔티티로 파일 목록 조회
     *
     * <p>특정 엔티티에 연결된 모든 파일을 조회합니다.
     *
     * <p>사용 예시:
     * <pre>
     * {@code
     * // Instance의 모든 파일 (썸네일, 비디오 등)
     * List<FileAsset> files = fileAssetRepository.findByReferenceTypeAndReferenceId(
     *     ReferenceType.INSTANCE,
     *     instance.getId()
     * );
     *
     * // AI 분석의 모든 결과 파일 (segmentation, 리포트 등)
     * List<FileAsset> aiResults = fileAssetRepository.findByReferenceTypeAndReferenceId(
     *     ReferenceType.AI_ANALYSIS,
     *     aiAnalysis.getId()
     * );
     * }
     * </pre>
     *
     * @param referenceType 참조 엔티티 타입
     * @param referenceId 참조 엔티티 ID
     * @return 파일 목록 (최신순)
     */
    List<FileAsset> findByReferenceTypeAndReferenceIdOrderByCreatedAtDesc(
        ReferenceType referenceType,
        Long referenceId
    );

    /**
     * 카테고리와 상태로 파일 목록 조회
     *
     * <p>사용 예시:
     * <pre>
     * {@code
     * // 활성 상태의 모든 AI 결과 파일
     * List<FileAsset> activeAiResults = fileAssetRepository.findByCategoryAndStatus(
     *     FileCategory.AI_RESULT,
     *     FileStatus.ACTIVE
     * );
     *
     * // 만료된 Export 파일 (정리 대상)
     * List<FileAsset> expiredExports = fileAssetRepository.findByCategoryAndStatus(
     *     FileCategory.EXPORT,
     *     FileStatus.EXPIRED
     * );
     * }
     * </pre>
     *
     * @param category 파일 카테고리
     * @param status 파일 상태
     * @return 파일 목록
     */
    List<FileAsset> findByCategoryAndStatus(FileCategory category, FileStatus status);

    /**
     * 상태로 파일 목록 조회
     *
     * <p>사용 예시:
     * <pre>
     * {@code
     * // 모든 만료된 파일 조회 (정리 작업용)
     * List<FileAsset> expiredFiles = fileAssetRepository.findByStatus(FileStatus.EXPIRED);
     *
     * // 모든 활성 파일 조회 (통계용)
     * List<FileAsset> activeFiles = fileAssetRepository.findByStatus(FileStatus.ACTIVE);
     * }
     * </pre>
     *
     * @param status 파일 상태
     * @return 파일 목록
     */
    List<FileAsset> findByStatus(FileStatus status);

    /**
     * TTL 만료된 파일 조회
     *
     * <p>현재 시각 기준으로 expiresAt이 과거인 활성 파일을 조회합니다.
     *
     * <p>사용 예시 (RetentionPolicyService):
     * <pre>
     * {@code
     * @Scheduled(cron = "0 0 * * * *") // 매 시간 실행
     * public void expireFiles() {
     *     LocalDateTime now = LocalDateTime.now();
     *     List<FileAsset> expiredFiles = fileAssetRepository.findExpiredFiles(now);
     *
     *     for (FileAsset file : expiredFiles) {
     *         file.markExpired();
     *         fileAssetRepository.save(file);
     *         log.info("File expired: {}", file.getFileName());
     *     }
     * }
     * }
     * </pre>
     *
     * @param now 현재 시각
     * @return 만료된 파일 목록
     */
    @Query("SELECT f FROM FileAsset f WHERE f.status = 'ACTIVE' AND f.expiresAt IS NOT NULL AND f.expiresAt < :now")
    List<FileAsset> findExpiredFiles(@Param("now") LocalDateTime now);

    /**
     * Storage Tier 마이그레이션 대상 파일 조회
     *
     * <p>마지막 접근 일시가 특정 기간 이전인 활성 파일을 조회합니다.
     *
     * <p>사용 예시 (StorageTieringService):
     * <pre>
     * {@code
     * @Scheduled(cron = "0 0 3 * * *") // 매일 새벽 3시 실행
     * public void migrateToColdStorage() {
     *     LocalDateTime oneYearAgo = LocalDateTime.now().minusYears(1);
     *
     *     // 1년 이상 미접근 파일 → COLD Storage 이동
     *     List<FileAsset> coldCandidates = fileAssetRepository.findFilesForTierMigration(
     *         oneYearAgo,
     *         "HOT"
     *     );
     *
     *     for (FileAsset file : coldCandidates) {
     *         // S3 Standard → S3 Glacier 이동
     *         s3TieringService.moveToGlacier(file.getStoragePath());
     *         file.updateStorageTier("COLD");
     *         fileAssetRepository.save(file);
     *     }
     * }
     * }
     * </pre>
     *
     * @param threshold 기준 시각 (이 시각 이전에 마지막 접근한 파일)
     * @param currentTier 현재 Storage Tier
     * @return 마이그레이션 대상 파일 목록
     */
    @Query("SELECT f FROM FileAsset f WHERE f.status = 'ACTIVE' " +
           "AND f.lastAccessedAt IS NOT NULL " +
           "AND f.lastAccessedAt < :threshold " +
           "AND f.storageTier = :currentTier")
    List<FileAsset> findFilesForTierMigration(
        @Param("threshold") LocalDateTime threshold,
        @Param("currentTier") String currentTier
    );

    /**
     * 특정 기간 이전에 삭제된 파일 제거
     *
     * <p>감사 기록으로 보관 중인 DELETED 상태 파일의 메타데이터를 최종 제거합니다.
     *
     * <p>사용 예시:
     * <pre>
     * {@code
     * @Scheduled(cron = "0 0 2 * * SUN") // 매주 일요일 새벽 2시
     * public void purgeOldDeletedRecords() {
     *     LocalDateTime oneYearAgo = LocalDateTime.now().minusYears(1);
     *     int deleted = fileAssetRepository.deleteByStatusAndUpdatedAtBefore(
     *         FileStatus.DELETED,
     *         oneYearAgo
     *     );
     *     log.info("Purged {} old deleted file records", deleted);
     * }
     * }
     * </pre>
     *
     * @param status 파일 상태 (일반적으로 DELETED)
     * @param threshold 기준 시각 (이 시각 이전에 수정된 레코드)
     * @return 삭제된 레코드 수
     */
    int deleteByStatusAndUpdatedAtBefore(FileStatus status, LocalDateTime threshold);

    /**
     * 스토리지 경로로 파일 조회
     *
     * <p>물리 파일 경로를 알고 있을 때 메타데이터를 조회합니다.
     *
     * <p>사용 예시:
     * <pre>
     * {@code
     * // 파일 시스템에서 파일 발견 시 DB 정보 조회
     * String path = "/ai_results/123/seg/frame_001.png";
     * FileAsset file = fileAssetRepository.findByStoragePath(path)
     *     .orElseThrow(() -> new FileNotFoundException("No metadata for file: " + path));
     * }
     * </pre>
     *
     * @param storagePath 스토리지 경로
     * @return 파일 자산 (Optional)
     */
    java.util.Optional<FileAsset> findByStoragePath(String storagePath);

    /**
     * 체크섬으로 파일 조회 (중복 감지)
     *
     * <p>동일한 체크섬을 가진 파일을 찾아 중복 업로드를 방지합니다.
     *
     * <p>사용 예시:
     * <pre>
     * {@code
     * // 파일 업로드 전 중복 체크
     * String checksum = calculateChecksum(uploadedFile);
     * List<FileAsset> duplicates = fileAssetRepository.findByChecksum(checksum);
     *
     * if (!duplicates.isEmpty()) {
     *     // 중복 파일 발견 → 기존 파일 재사용
     *     return duplicates.get(0);
     * } else {
     *     // 새로운 파일 → 업로드 진행
     *     return uploadAndSave(uploadedFile);
     * }
     * }
     * </pre>
     *
     * @param checksum 파일 체크섬 (MD5 또는 SHA-256)
     * @return 동일 체크섬 파일 목록
     */
    List<FileAsset> findByChecksum(String checksum);

    /**
     * 특정 참조의 특정 카테고리 파일 조회
     *
     * <p>사용 예시:
     * <pre>
     * {@code
     * // AI 분석의 AI_RESULT 카테고리 파일만 조회
     * List<FileAsset> aiResults = fileAssetRepository.findByReferenceTypeAndReferenceIdAndCategory(
     *     ReferenceType.AI_ANALYSIS,
     *     aiAnalysis.getId(),
     *     FileCategory.AI_RESULT
     * );
     * }
     * </pre>
     *
     * @param referenceType 참조 엔티티 타입
     * @param referenceId 참조 엔티티 ID
     * @param category 파일 카테고리
     * @return 파일 목록
     */
    List<FileAsset> findByReferenceTypeAndReferenceIdAndCategory(
        ReferenceType referenceType,
        Long referenceId,
        FileCategory category
    );

    /**
     * 카테고리별 총 파일 크기 통계
     *
     * <p>사용 예시:
     * <pre>
     * {@code
     * // AI 결과 파일의 총 크기
     * Long totalSize = fileAssetRepository.sumFileSizeByCategory(FileCategory.AI_RESULT);
     * log.info("Total AI result storage: {} bytes", totalSize);
     * }
     * </pre>
     *
     * @param category 파일 카테고리
     * @return 총 파일 크기 (bytes), null이면 0
     */
    @Query("SELECT COALESCE(SUM(f.fileSize), 0) FROM FileAsset f WHERE f.category = :category AND f.status = 'ACTIVE'")
    Long sumFileSizeByCategory(@Param("category") FileCategory category);

    /**
     * Storage Tier별 총 파일 크기 통계
     *
     * <p>사용 예시:
     * <pre>
     * {@code
     * // HOT Storage 사용량
     * Long hotStorageSize = fileAssetRepository.sumFileSizeByStorageTier("HOT");
     * log.info("HOT storage usage: {} bytes", hotStorageSize);
     * }
     * </pre>
     *
     * @param tier Storage Tier (HOT, WARM, COLD)
     * @return 총 파일 크기 (bytes), null이면 0
     */
    @Query("SELECT COALESCE(SUM(f.fileSize), 0) FROM FileAsset f WHERE f.storageTier = :tier AND f.status = 'ACTIVE'")
    Long sumFileSizeByStorageTier(@Param("tier") String tier);

    /**
     * Storage Tier별 파일 목록 페이징 조회 (Admin용)
     *
     * <p>특정 Tier의 활성 파일을 마지막 접근 시간 내림차순으로 조회합니다.
     *
     * <p>사용 예시:
     * <pre>
     * {@code
     * // HOT Storage 파일 목록 (첫 페이지, 20개)
     * Pageable pageable = PageRequest.of(0, 20);
     * Page<FileAsset> hotFiles = fileAssetRepository.findByStorageTierAndStatusOrderByLastAccessedAtDesc(
     *     "HOT",
     *     FileStatus.ACTIVE,
     *     pageable
     * );
     * }
     * </pre>
     *
     * @param tier Storage Tier (HOT, WARM, COLD)
     * @param status 파일 상태 (일반적으로 ACTIVE)
     * @param pageable 페이징 정보
     * @return 파일 목록 페이지
     */
    org.springframework.data.domain.Page<FileAsset> findByStorageTierAndStatusOrderByLastAccessedAtDesc(
        String tier,
        FileStatus status,
        org.springframework.data.domain.Pageable pageable
    );

    /**
     * Storage Tier별 파일 개수 통계
     *
     * <p>사용 예시:
     * <pre>
     * {@code
     * // HOT Storage 파일 개수
     * Long hotFileCount = fileAssetRepository.countByStorageTierAndStatus("HOT", FileStatus.ACTIVE);
     * log.info("HOT storage file count: {}", hotFileCount);
     * }
     * </pre>
     *
     * @param tier Storage Tier (HOT, WARM, COLD)
     * @param status 파일 상태
     * @return 파일 개수
     */
    Long countByStorageTierAndStatus(String tier, FileStatus status);

    /**
     * 카테고리별 파일 개수 통계
     *
     * <p>사용 예시:
     * <pre>
     * {@code
     * // DICOM 파일 개수
     * Long dicomCount = fileAssetRepository.countByCategoryAndStatus(FileCategory.DICOM, FileStatus.ACTIVE);
     * log.info("DICOM file count: {}", dicomCount);
     * }
     * </pre>
     *
     * @param category 파일 카테고리
     * @param status 파일 상태
     * @return 파일 개수
     */
    Long countByCategoryAndStatus(FileCategory category, FileStatus status);

    /**
     * 모든 카테고리별 스토리지 메트릭 조회 (단일 쿼리)
     *
     * <p>N+1 문제를 방지하기 위해 GROUP BY를 사용한 단일 쿼리로 모든 카테고리의 통계를 조회합니다.
     *
     * <p>사용 예시:
     * <pre>
     * {@code
     * // 모든 카테고리 통계를 1개 쿼리로 조회
     * List<CategoryStorageMetrics> metrics =
     *     fileAssetRepository.findStorageMetricsByAllCategories();
     *
     * // 결과: [
     * //   {category: "DICOM", fileCount: 100, totalSize: 1024000},
     * //   {category: "AI_RESULT", fileCount: 50, totalSize: 512000},
     * //   ...
     * // ]
     * }
     * </pre>
     *
     * @return 카테고리별 메트릭 목록 (카테고리 오름차순)
     */
    @Query("""
        SELECT new com.hanumoka.sado.minipacs.dto.response.admin.CategoryStorageMetrics(
            f.category,
            COUNT(f),
            COALESCE(SUM(f.fileSize), 0)
        )
        FROM FileAsset f
        WHERE f.status = 'ACTIVE'
        GROUP BY f.category
        ORDER BY f.category ASC
        """)
    List<com.hanumoka.sado.minipacs.dto.response.admin.CategoryStorageMetrics> findStorageMetricsByAllCategories();
}
