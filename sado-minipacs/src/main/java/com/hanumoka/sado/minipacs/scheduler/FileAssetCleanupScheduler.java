package com.hanumoka.sado.minipacs.scheduler;

import static com.hanumoka.sado.common.util.StringUtils.hasText;
import com.hanumoka.sado.minipacs.domain.entity.FileAsset;
import com.hanumoka.sado.minipacs.domain.enums.FileStatus;
import com.hanumoka.sado.minipacs.domain.repository.FileAssetRepository;
import com.hanumoka.sado.minipacs.storage.service.DicomStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * FileAsset 정리 스케줄러
 *
 * <p>TTL이 만료된 FileAsset을 처리하고 정리합니다.
 *
 * <p>스케줄:
 * <ul>
 *   <li>매 시간: 만료된 파일 상태를 EXPIRED로 변경</li>
 *   <li>매일 새벽 2시: 7일 이상 EXPIRED 상태인 파일 메타데이터 삭제</li>
 * </ul>
 *
 * <p>참고:
 * <ul>
 *   <li>DICOM 카테고리 파일은 TTL이 없어 만료되지 않습니다 (영구 보관)</li>
 *   <li>AI_RESULT, CLINICAL_DOC도 영구 보관됩니다</li>
 *   <li>SYSTEM 카테고리는 90일 TTL</li>
 *   <li>EXPORT 카테고리는 7일 TTL</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FileAssetCleanupScheduler {

    private final FileAssetRepository fileAssetRepository;
    private final DicomStorageService dicomStorageService;

    /**
     * 매 시간 만료된 FileAsset 처리
     *
     * <p>expiresAt이 현재 시각 이전인 ACTIVE 파일을 EXPIRED 상태로 변경합니다.
     *
     * <p>Cron 표현식: "0 0 * * * *"
     * - 초: 0
     * - 분: 0
     * - 시: 매 시간
     * - 일/월/요일: 매일
     */
    @Scheduled(cron = "0 0 * * * *")
    @Transactional
    public void markExpiredFiles() {
        log.info("Starting FileAsset expiration check...");

        LocalDateTime now = LocalDateTime.now();
        List<FileAsset> expiredFiles = fileAssetRepository.findExpiredFiles(now);

        if (expiredFiles.isEmpty()) {
            log.info("No expired files found");
            return;
        }

        log.info("Found {} expired files to process", expiredFiles.size());

        int successCount = 0;
        int failCount = 0;

        for (FileAsset fileAsset : expiredFiles) {
            try {
                fileAsset.markExpired();
                log.debug("Marked file as expired: id={}, fileName={}, storagePath={}",
                        fileAsset.getId(), fileAsset.getFileName(), fileAsset.getStoragePath());
                successCount++;
            } catch (Exception e) {
                log.error("Failed to mark file as expired: id={}", fileAsset.getId(), e);
                failCount++;
            }
        }

        fileAssetRepository.saveAll(expiredFiles);

        log.info("FileAsset expiration completed: success={}, failed={}", successCount, failCount);
    }

    /**
     * 매일 새벽 2시: EXPIRED 상태 파일 정리
     *
     * <p>7일 이상 EXPIRED 상태인 파일의 메타데이터를 삭제합니다.
     * 물리 파일은 이미 삭제되었거나 별도로 처리됩니다.
     *
     * <p>Cron 표현식: "0 0 2 * * *"
     * - 초: 0
     * - 분: 0
     * - 시: 2시
     * - 일/월/요일: 매일
     */
    @Scheduled(cron = "0 0 2 * * *")
    @Transactional
    public void purgeExpiredFileMetadata() {
        log.info("Starting EXPIRED file metadata purge...");

        // 7일 이상 EXPIRED 상태인 파일 삭제
        LocalDateTime threshold = LocalDateTime.now().minusDays(7);
        int deletedCount = fileAssetRepository.deleteByStatusAndUpdatedAtBefore(
                FileStatus.EXPIRED, threshold);

        log.info("EXPIRED file metadata purge completed: deleted={} records", deletedCount);
    }

    /**
     * 매일 새벽 3시: 고아 파일 정리 (물리 파일)
     *
     * <p>DELETED 상태 파일의 물리 파일을 S3에서 삭제합니다.
     *
     * <p>Cron 표현식: "0 0 3 * * *"
     * - 초: 0
     * - 분: 0
     * - 시: 3시
     * - 일/월/요일: 매일
     */
    @Scheduled(cron = "0 0 3 * * *")
    @Transactional
    public void cleanupDeletedFiles() {
        log.info("Starting physical file cleanup for DELETED files...");

        List<FileAsset> deletedFiles = fileAssetRepository.findByStatus(FileStatus.DELETED);

        if (deletedFiles.isEmpty()) {
            log.info("No DELETED files to clean up");
            return;
        }

        log.info("Found {} DELETED files to clean up", deletedFiles.size());

        int successCount = 0;
        int failCount = 0;

        for (FileAsset fileAsset : deletedFiles) {
            try {
                String storagePath = fileAsset.getStoragePath();

                if (hasText(storagePath)) {
                    dicomStorageService.deleteDicomFile(storagePath);
                    log.debug("Deleted physical file: {}", storagePath);
                }

                successCount++;
            } catch (Exception e) {
                log.error("Failed to delete physical file: id={}, path={}",
                        fileAsset.getId(), fileAsset.getStoragePath(), e);
                failCount++;
            }
        }

        log.info("Physical file cleanup completed: success={}, failed={}", successCount, failCount);
    }
}
