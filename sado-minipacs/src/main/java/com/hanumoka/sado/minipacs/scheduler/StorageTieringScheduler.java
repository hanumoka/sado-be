package com.hanumoka.sado.minipacs.scheduler;

import com.hanumoka.sado.minipacs.domain.entity.FileAsset;
import com.hanumoka.sado.minipacs.domain.repository.FileAssetRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Storage Tier 마이그레이션 스케줄러
 *
 * <p>파일 접근 시간 기반으로 Storage Tier를 자동 마이그레이션합니다.
 *
 * <p>Tier 마이그레이션 정책:
 * <ul>
 *   <li>HOT → WARM: 30일 이상 미접근</li>
 *   <li>WARM → COLD: 1년 이상 미접근 (ARCHIVED 상태로 전환)</li>
 * </ul>
 *
 * <p>스케줄:
 * <ul>
 *   <li>매일 새벽 3시: HOT → WARM 마이그레이션</li>
 *   <li>매일 새벽 4시: WARM → COLD 마이그레이션</li>
 * </ul>
 *
 * <p>참고:
 * <ul>
 *   <li>POC 범위에서는 DB의 storageTier 필드만 업데이트</li>
 *   <li>실제 S3 Storage Class 변경은 선택 사항</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StorageTieringScheduler {

    private final FileAssetRepository fileAssetRepository;

    /**
     * HOT → WARM Tier 마이그레이션
     *
     * <p>30일 이상 접근하지 않은 HOT Tier 파일을 WARM Tier로 이동합니다.
     *
     * <p>Cron 표현식: "0 0 3 * * *"
     * - 초: 0
     * - 분: 0
     * - 시: 3 (새벽 3시)
     * - 일/월/요일: 매일
     */
    @Scheduled(cron = "0 0 3 * * *")
    @Transactional
    public void migrateHotToWarm() {
        log.info("Starting HOT → WARM tier migration...");

        LocalDateTime cutoffDate = LocalDateTime.now().minusDays(30);
        List<FileAsset> files = fileAssetRepository.findFilesForTierMigration(cutoffDate, "HOT");

        if (files.isEmpty()) {
            log.info("No files found for HOT → WARM migration");
            return;
        }

        log.info("Found {} files for HOT → WARM migration", files.size());

        int successCount = 0;
        int failCount = 0;

        for (FileAsset file : files) {
            try {
                file.updateStorageTier("WARM");
                fileAssetRepository.save(file);

                log.debug("Migrated file to WARM: id={}, fileName={}, lastAccessed={}",
                        file.getId(), file.getFileName(), file.getLastAccessedAt());
                successCount++;
            } catch (Exception e) {
                log.error("Failed to migrate file to WARM: id={}", file.getId(), e);
                failCount++;
            }
        }

        log.info("HOT → WARM migration completed: success={}, fail={}", successCount, failCount);
    }

    /**
     * WARM → COLD Tier 마이그레이션
     *
     * <p>1년 이상 접근하지 않은 WARM Tier 파일을 COLD Tier로 이동하고 ARCHIVED 상태로 전환합니다.
     *
     * <p>Cron 표현식: "0 0 4 * * *"
     * - 초: 0
     * - 분: 0
     * - 시: 4 (새벽 4시)
     * - 일/월/요일: 매일
     */
    @Scheduled(cron = "0 0 4 * * *")
    @Transactional
    public void migrateWarmToCold() {
        log.info("Starting WARM → COLD tier migration...");

        LocalDateTime cutoffDate = LocalDateTime.now().minusYears(1);
        List<FileAsset> files = fileAssetRepository.findFilesForTierMigration(cutoffDate, "WARM");

        if (files.isEmpty()) {
            log.info("No files found for WARM → COLD migration");
            return;
        }

        log.info("Found {} files for WARM → COLD migration", files.size());

        int successCount = 0;
        int failCount = 0;

        for (FileAsset file : files) {
            try {
                file.updateStorageTier("COLD");
                file.markArchived();
                fileAssetRepository.save(file);

                log.debug("Migrated file to COLD: id={}, fileName={}, lastAccessed={}",
                        file.getId(), file.getFileName(), file.getLastAccessedAt());
                successCount++;
            } catch (Exception e) {
                log.error("Failed to migrate file to COLD: id={}", file.getId(), e);
                failCount++;
            }
        }

        log.info("WARM → COLD migration completed: success={}, fail={}", successCount, failCount);
    }
}
