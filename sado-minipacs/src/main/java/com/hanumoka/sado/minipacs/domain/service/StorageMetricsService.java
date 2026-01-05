package com.hanumoka.sado.minipacs.domain.service;

import com.hanumoka.sado.minipacs.domain.entity.StorageMetricsSnapshot;
import com.hanumoka.sado.minipacs.domain.enums.FileStatus;
import com.hanumoka.sado.minipacs.domain.repository.FileAssetRepository;
import com.hanumoka.sado.minipacs.domain.repository.StorageMetricsSnapshotRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 스토리지 메트릭 서비스
 *
 * <p>스토리지 사용량 스냅샷 생성 및 조회를 담당합니다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class StorageMetricsService {

    private final FileAssetRepository fileAssetRepository;
    private final StorageMetricsSnapshotRepository snapshotRepository;

    /**
     * 스토리지 메트릭 스냅샷 생성
     *
     * <p>현재 스토리지 사용량을 캡처하여 저장합니다.
     * 매일 00:00에 자동 실행되거나 수동으로 호출할 수 있습니다.
     *
     * @return 생성된 스냅샷
     */
    @Transactional
    public StorageMetricsSnapshot captureSnapshot() {
        log.info("Capturing storage metrics snapshot");

        // 1. Tier별 사용량 조회
        Long hotSize = fileAssetRepository.sumFileSizeByStorageTier("HOT");
        Long warmSize = fileAssetRepository.sumFileSizeByStorageTier("WARM");
        Long coldSize = fileAssetRepository.sumFileSizeByStorageTier("COLD");
        Long totalSize = hotSize + warmSize + coldSize;

        // 2. Tier별 파일 개수 조회 (ACTIVE 파일만)
        Long hotFileCount = fileAssetRepository.countByStorageTierAndStatus("HOT", FileStatus.ACTIVE);
        Long warmFileCount = fileAssetRepository.countByStorageTierAndStatus("WARM", FileStatus.ACTIVE);
        Long coldFileCount = fileAssetRepository.countByStorageTierAndStatus("COLD", FileStatus.ACTIVE);
        Long totalFileCount = hotFileCount + warmFileCount + coldFileCount;

        // 3. 스냅샷 생성
        StorageMetricsSnapshot snapshot = StorageMetricsSnapshot.builder()
            .snapshotTime(LocalDateTime.now())
            .totalSize(totalSize)
            .hotSize(hotSize)
            .warmSize(warmSize)
            .coldSize(coldSize)
            .fileCount(totalFileCount)
            .hotFileCount(hotFileCount)
            .warmFileCount(warmFileCount)
            .coldFileCount(coldFileCount)
            .build();

        StorageMetricsSnapshot saved = snapshotRepository.save(snapshot);

        log.info("Snapshot captured: total={} bytes ({} files), hot={}, warm={}, cold={}",
                 totalSize, totalFileCount, hotSize, warmSize, coldSize);

        return saved;
    }

    /**
     * 매일 자동 스냅샷 생성
     *
     * <p>매일 00:00에 실행되어 스토리지 메트릭을 기록합니다.
     */
    @Scheduled(cron = "0 0 0 * * *") // 매일 00:00
    @Transactional
    public void dailySnapshotCapture() {
        log.info("Scheduled daily snapshot capture started");

        try {
            LocalDateTime today = LocalDateTime.now().withHour(0).withMinute(0).withSecond(0).withNano(0);

            // 중복 방지: 오늘 날짜의 스냅샷이 이미 있으면 스킵
            if (snapshotRepository.existsBySnapshotTime(today)) {
                log.info("Snapshot for today already exists, skipping");
                return;
            }

            captureSnapshot();
            log.info("Scheduled daily snapshot capture completed");
        } catch (Exception e) {
            log.error("Failed to capture daily snapshot", e);
            // 스냅샷 실패가 시스템 전체에 영향을 주지 않도록 예외를 삼킴
        }
    }

    /**
     * 최근 N일간의 스냅샷 조회
     *
     * @param days 조회할 일수 (7, 30, 90 등)
     * @return 스냅샷 목록 (시간순 정렬)
     */
    public List<StorageMetricsSnapshot> getRecentSnapshots(int days) {
        LocalDateTime since = LocalDateTime.now().minusDays(days);
        return snapshotRepository.findBySnapshotTimeAfterOrderBySnapshotTimeAsc(since);
    }

    /**
     * 오래된 스냅샷 정리
     *
     * <p>90일 이상 된 스냅샷을 삭제합니다.
     * 매월 1일 01:00에 자동 실행됩니다.
     */
    @Scheduled(cron = "0 0 1 1 * *") // 매월 1일 01:00
    @Transactional
    public void cleanupOldSnapshots() {
        log.info("Scheduled snapshot cleanup started");

        try {
            LocalDateTime cutoffDate = LocalDateTime.now().minusDays(90);
            int deletedCount = snapshotRepository.deleteBySnapshotTimeBefore(cutoffDate);

            log.info("Snapshot cleanup completed: deleted {} old snapshots", deletedCount);
        } catch (Exception e) {
            log.error("Failed to cleanup old snapshots", e);
        }
    }

    /**
     * 가장 최근 스냅샷 조회
     *
     * @return 최근 스냅샷 (없으면 null)
     */
    public StorageMetricsSnapshot getLatestSnapshot() {
        return snapshotRepository.findLatestSnapshot();
    }

    /**
     * 특정 기간의 스냅샷 조회
     *
     * @param startTime 시작 시각
     * @param endTime 종료 시각
     * @return 스냅샷 목록
     */
    public List<StorageMetricsSnapshot> getSnapshotsBetween(LocalDateTime startTime, LocalDateTime endTime) {
        return snapshotRepository.findBySnapshotTimeBetweenOrderBySnapshotTimeAsc(startTime, endTime);
    }
}
