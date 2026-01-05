package com.hanumoka.sado.minipacs.domain.repository;

import com.hanumoka.sado.minipacs.domain.entity.TierTransitionLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * TierTransitionLog Repository
 *
 * <p>Tier 전환 이력 조회 및 통계를 제공합니다.
 */
@Repository
public interface TierTransitionLogRepository extends JpaRepository<TierTransitionLog, Long> {

    /**
     * 특정 FileAsset의 Tier 전환 이력 조회
     *
     * @param fileAssetId FileAsset ID
     * @param pageable 페이징 정보
     * @return Tier 전환 이력 (최신순)
     */
    Page<TierTransitionLog> findByFileAssetIdOrderByTransitionedAtDesc(
        Long fileAssetId,
        Pageable pageable
    );

    /**
     * 최근 N일간의 Tier 전환 이력 조회 (N+1 방지용 JOIN FETCH)
     *
     * <p>CRITICAL: N+1 문제 해결
     * - AdminController에서 페이지당 20개 조회 시 20번 추가 쿼리 발생 문제 해결
     * - JOIN FETCH로 fileAsset 정보를 한 번에 로드
     * - COUNT 쿼리와 데이터 쿼리 분리 (Spring Data JPA가 자동 처리)
     *
     * @param since 조회 시작 시점
     * @param pageable 페이징 정보
     * @return Tier 전환 이력 (fileAsset eager loading, 최신순)
     */
    @Query(value = "SELECT t FROM TierTransitionLog t " +
           "JOIN FETCH t.fileAsset fa " +
           "WHERE t.transitionedAt >= :since " +
           "ORDER BY t.transitionedAt DESC",
           countQuery = "SELECT COUNT(t) FROM TierTransitionLog t " +
           "WHERE t.transitionedAt >= :since")
    Page<TierTransitionLog> findByTransitionedAtAfterWithFileAsset(
        @Param("since") LocalDateTime since,
        Pageable pageable
    );

    /**
     * 최근 N일간의 Tier 전환 이력 조회 (Legacy - N+1 문제 있음)
     *
     * @param since 조회 시작 시점
     * @param pageable 페이징 정보
     * @return Tier 전환 이력 (최신순)
     * @deprecated {@link #findByTransitionedAtAfterWithFileAsset(LocalDateTime, Pageable)} 사용 권장
     */
    @Deprecated
    Page<TierTransitionLog> findByTransitionedAtAfterOrderByTransitionedAtDesc(
        LocalDateTime since,
        Pageable pageable
    );

    /**
     * 특정 Tier로의 전환 이력 조회
     *
     * @param toTier 목표 Tier (HOT/WARM/COLD)
     * @param pageable 페이징 정보
     * @return Tier 전환 이력
     */
    Page<TierTransitionLog> findByToTierOrderByTransitionedAtDesc(
        String toTier,
        Pageable pageable
    );

    /**
     * 전환 유형별 이력 조회
     *
     * @param transitionType 전환 유형 (MANUAL/AUTO)
     * @param pageable 페이징 정보
     * @return Tier 전환 이력
     */
    Page<TierTransitionLog> findByTransitionTypeOrderByTransitionedAtDesc(
        TierTransitionLog.TransitionType transitionType,
        Pageable pageable
    );

    /**
     * 최근 N일간의 Tier 전환 통계 (toTier별 카운트)
     *
     * @param since 조회 시작 시점
     * @return Tier별 전환 횟수
     */
    @Query("SELECT t.toTier, COUNT(t) FROM TierTransitionLog t " +
           "WHERE t.transitionedAt >= :since " +
           "GROUP BY t.toTier")
    List<Object[]> countTransitionsByToTierSince(@Param("since") LocalDateTime since);

    /**
     * 최근 N일간의 전환 유형별 통계
     *
     * @param since 조회 시작 시점
     * @return 전환 유형별 횟수
     */
    @Query("SELECT t.transitionType, COUNT(t) FROM TierTransitionLog t " +
           "WHERE t.transitionedAt >= :since " +
           "GROUP BY t.transitionType")
    List<Object[]> countTransitionsByTypeSince(@Param("since") LocalDateTime since);
}
