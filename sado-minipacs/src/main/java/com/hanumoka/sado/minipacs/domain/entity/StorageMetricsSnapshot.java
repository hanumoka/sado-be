package com.hanumoka.sado.minipacs.domain.entity;

import com.hanumoka.sado.common.entity.TenantAwareEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 스토리지 메트릭 스냅샷 엔티티
 *
 * <p>일별 스토리지 사용량을 기록하여 시계열 트렌드 분석을 지원합니다.
 *
 * <p>주요 용도:
 * <ul>
 *   <li>트렌드 차트: 7일/30일/90일 스토리지 사용량 추이</li>
 *   <li>용량 계획: 증가율 기반 용량 확장 시점 예측</li>
 *   <li>비용 최적화: Tier별 데이터 분포 변화 추적</li>
 *   <li>이상 탐지: 급격한 용량 증가/감소 알림</li>
 * </ul>
 *
 * <p>데이터 보관 정책:
 * <ul>
 *   <li>90일 이내: 원본 데이터 보관 (일별)</li>
 *   <li>90일 초과: 자동 삭제 (스케줄러)</li>
 *   <li>향후 확장: 주별/월별 집계 테이블 추가 가능</li>
 * </ul>
 */
@Entity
@Table(name = "storage_metrics_snapshot", indexes = {
    @Index(name = "idx_storage_snapshot_time", columnList = "snapshot_time"),
    @Index(name = "idx_storage_snapshot_tenant", columnList = "tenant_id, snapshot_time")
})
@Getter
@Setter
@NoArgsConstructor
public class StorageMetricsSnapshot extends TenantAwareEntity {

    /**
     * 스냅샷 생성 시각
     * 매일 00:00에 자동 생성
     */
    @Column(name = "snapshot_time", nullable = false)
    private LocalDateTime snapshotTime;

    /**
     * 전체 스토리지 사용량 (bytes)
     * totalSize = hotSize + warmSize + coldSize
     */
    @Column(name = "total_size", nullable = false)
    private Long totalSize;

    /**
     * HOT Tier 사용량 (bytes)
     */
    @Column(name = "hot_size", nullable = false)
    private Long hotSize;

    /**
     * WARM Tier 사용량 (bytes)
     */
    @Column(name = "warm_size", nullable = false)
    private Long warmSize;

    /**
     * COLD Tier 사용량 (bytes)
     */
    @Column(name = "cold_size", nullable = false)
    private Long coldSize;

    /**
     * 전체 파일 개수
     */
    @Column(name = "file_count", nullable = false)
    private Long fileCount;

    /**
     * HOT Tier 파일 개수
     */
    @Column(name = "hot_file_count")
    private Long hotFileCount;

    /**
     * WARM Tier 파일 개수
     */
    @Column(name = "warm_file_count")
    private Long warmFileCount;

    /**
     * COLD Tier 파일 개수
     */
    @Column(name = "cold_file_count")
    private Long coldFileCount;

    // ========== Builder Pattern ==========

    public static class Builder {
        private final StorageMetricsSnapshot snapshot = new StorageMetricsSnapshot();

        public Builder snapshotTime(LocalDateTime snapshotTime) {
            snapshot.snapshotTime = snapshotTime;
            return this;
        }

        public Builder totalSize(Long totalSize) {
            snapshot.totalSize = totalSize;
            return this;
        }

        public Builder hotSize(Long hotSize) {
            snapshot.hotSize = hotSize;
            return this;
        }

        public Builder warmSize(Long warmSize) {
            snapshot.warmSize = warmSize;
            return this;
        }

        public Builder coldSize(Long coldSize) {
            snapshot.coldSize = coldSize;
            return this;
        }

        public Builder fileCount(Long fileCount) {
            snapshot.fileCount = fileCount;
            return this;
        }

        public Builder hotFileCount(Long hotFileCount) {
            snapshot.hotFileCount = hotFileCount;
            return this;
        }

        public Builder warmFileCount(Long warmFileCount) {
            snapshot.warmFileCount = warmFileCount;
            return this;
        }

        public Builder coldFileCount(Long coldFileCount) {
            snapshot.coldFileCount = coldFileCount;
            return this;
        }

        public StorageMetricsSnapshot build() {
            if (snapshot.snapshotTime == null) {
                snapshot.snapshotTime = LocalDateTime.now();
            }
            if (snapshot.totalSize == null) {
                throw new IllegalArgumentException("totalSize is required");
            }
            if (snapshot.hotSize == null || snapshot.warmSize == null || snapshot.coldSize == null) {
                throw new IllegalArgumentException("hotSize, warmSize, coldSize are required");
            }
            if (snapshot.fileCount == null) {
                throw new IllegalArgumentException("fileCount is required");
            }
            return snapshot;
        }
    }

    public static Builder builder() {
        return new Builder();
    }
}
