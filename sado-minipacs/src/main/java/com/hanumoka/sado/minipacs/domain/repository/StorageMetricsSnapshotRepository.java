package com.hanumoka.sado.minipacs.domain.repository;

import com.hanumoka.sado.minipacs.domain.entity.StorageMetricsSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * StorageMetricsSnapshot Repository
 *
 * <p>스토리지 메트릭 스냅샷 조회 및 관리를 제공합니다.
 */
@Repository
public interface StorageMetricsSnapshotRepository extends JpaRepository<StorageMetricsSnapshot, Long> {

    /**
     * 최근 N일간의 스냅샷 조회
     *
     * @param since 조회 시작 시점
     * @return 스냅샷 목록 (시간순 정렬)
     */
    List<StorageMetricsSnapshot> findBySnapshotTimeAfterOrderBySnapshotTimeAsc(
        LocalDateTime since
    );

    /**
     * 특정 기간의 스냅샷 조회
     *
     * @param startTime 시작 시각
     * @param endTime 종료 시각
     * @return 스냅샷 목록 (시간순 정렬)
     */
    List<StorageMetricsSnapshot> findBySnapshotTimeBetweenOrderBySnapshotTimeAsc(
        LocalDateTime startTime,
        LocalDateTime endTime
    );

    /**
     * 가장 최근 스냅샷 조회
     *
     * @return 최근 스냅샷
     */
    @Query("SELECT s FROM StorageMetricsSnapshot s " +
           "ORDER BY s.snapshotTime DESC " +
           "LIMIT 1")
    StorageMetricsSnapshot findLatestSnapshot();

    /**
     * N일 이전의 오래된 스냅샷 삭제
     *
     * <p>데이터 보관 정책: 90일 초과 데이터 자동 삭제
     *
     * @param before 삭제 기준 시각 (이전 데이터 모두 삭제)
     * @return 삭제된 레코드 수
     */
    @Modifying
    @Query("DELETE FROM StorageMetricsSnapshot s WHERE s.snapshotTime < :before")
    int deleteBySnapshotTimeBefore(@Param("before") LocalDateTime before);

    /**
     * 특정 날짜의 스냅샷 존재 여부 확인
     *
     * @param snapshotTime 확인할 시각
     * @return 존재 여부
     */
    boolean existsBySnapshotTime(LocalDateTime snapshotTime);

    /**
     * 스냅샷 총 개수 조회
     *
     * @return 스냅샷 개수
     */
    @Query("SELECT COUNT(s) FROM StorageMetricsSnapshot s")
    long countAllSnapshots();
}
