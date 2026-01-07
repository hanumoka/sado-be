package com.hanumoka.sado.minipacs.domain.repository;

import com.hanumoka.sado.minipacs.domain.entity.Instance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

/**
 * Instance Repository
 *
 * 멀티테넌시: Hibernate Filter 자동 적용
 */
@Repository
public interface InstanceRepository extends JpaRepository<Instance, Long> {

    /**
     * DICOM SOP Instance UID로 조회
     *
     * @param sopInstanceUid DICOM SOP Instance UID
     * @return Instance (없으면 empty)
     */
    Optional<Instance> findBySopInstanceUid(String sopInstanceUid);

    /**
     * 특정 시리즈의 모든 Instance 조회
     *
     * @param seriesId 시리즈 PK
     * @return Instance 목록 (Instance Number 순)
     */
    List<Instance> findBySeriesIdOrderByInstanceNumber(Long seriesId);

    /**
     * 특정 시리즈의 특정 Instance Number 조회
     *
     * @param seriesId 시리즈 PK
     * @param instanceNumber Instance Number
     * @return Instance (없으면 empty)
     */
    Optional<Instance> findBySeriesIdAndInstanceNumber(Long seriesId, Integer instanceNumber);

    /**
     * 시리즈의 Instance 개수 조회
     *
     * @param seriesId 시리즈 PK
     * @return Instance 개수
     */
    long countBySeriesId(Long seriesId);

    /**
     * 검사(Study)의 모든 Instance 개수 조회
     *
     * <p>Study는 Instance와 직접 관계가 없으므로 Series를 통해 JOIN하여 COUNT합니다.
     * <pre>
     * Study (1) → (N) Series (1) → (N) Instance
     * </pre>
     *
     * <p>성능 최적화:
     * <ul>
     *   <li>인덱스 활용: instance 테이블의 series_id 인덱스 사용</li>
     *   <li>실행 계획: INDEX SCAN → NESTED LOOP JOIN</li>
     * </ul>
     *
     * @param studyId 검사 PK
     * @return Study에 속한 모든 Instance 개수
     */
    @Query("SELECT COUNT(i) FROM Instance i WHERE i.series.study.id = :studyId")
    long countByStudyId(@Param("studyId") Long studyId);

    /**
     * 스토리지 경로로 조회 (중복 방지용)
     *
     * @param storagePath DICOM 파일 저장 경로
     * @return Instance (없으면 empty)
     */
    Optional<Instance> findByStoragePath(String storagePath);

    /**
     * Instance 필터링 조회
     *
     * <p>동적 쿼리 (null 파라미터는 무시):
     * <ul>
     *   <li>seriesId: 시리즈 ID 완전 일치</li>
     *   <li>studyId: Study ID 완전 일치 (Series JOIN 통해)</li>
     *   <li>sopInstanceUid: SOP Instance UID 부분 일치</li>
     *   <li>storageTier: Storage Tier 완전 일치</li>
     * </ul>
     *
     * <p>JOIN FETCH로 N+1 문제 방지
     *
     * @param seriesId Series ID (nullable)
     * @param studyId Study ID (nullable)
     * @param sopInstanceUid SOP Instance UID (nullable, 부분 일치)
     * @param storageTier Storage Tier (nullable)
     * @return Instance 목록 (Series eager loading)
     */
    @Query("""
            SELECT DISTINCT i FROM Instance i
            JOIN FETCH i.series s
            JOIN FETCH s.study st
            WHERE (:seriesId IS NULL OR i.series.id = :seriesId)
            AND (:studyId IS NULL OR s.study.id = :studyId)
            AND (:sopInstanceUid IS NULL OR i.sopInstanceUid LIKE CONCAT('%', :sopInstanceUid, '%'))
            AND (:storageTier IS NULL OR i.storageTier = :storageTier)
            ORDER BY i.createdAt DESC
            """)
    List<Instance> findByFilters(
            @Param("seriesId") Long seriesId,
            @Param("studyId") Long studyId,
            @Param("sopInstanceUid") String sopInstanceUid,
            @Param("storageTier") Instance.StorageTier storageTier
    );

    /**
     * Instance 필터링 조회 (페이지네이션)
     *
     * <p>동적 쿼리 (null 파라미터는 무시):
     * <ul>
     *   <li>seriesId: 시리즈 ID 완전 일치</li>
     *   <li>studyId: Study ID 완전 일치 (Series JOIN 통해)</li>
     *   <li>sopInstanceUid: SOP Instance UID 부분 일치</li>
     *   <li>storageTier: Storage Tier 완전 일치</li>
     * </ul>
     *
     * <p>JOIN FETCH로 N+1 문제 방지
     * <p>countQuery는 별도로 정의하여 페이지네이션 지원
     *
     * @param seriesId Series ID (nullable)
     * @param studyId Study ID (nullable)
     * @param sopInstanceUid SOP Instance UID (nullable, 부분 일치)
     * @param storageTier Storage Tier (nullable)
     * @param pageable 페이지네이션 정보
     * @return Instance 페이지 (Series eager loading)
     */
    @Query(value = """
            SELECT DISTINCT i FROM Instance i
            JOIN FETCH i.series s
            JOIN FETCH s.study st
            WHERE (:seriesId IS NULL OR i.series.id = :seriesId)
            AND (:studyId IS NULL OR s.study.id = :studyId)
            AND (:sopInstanceUid IS NULL OR i.sopInstanceUid LIKE CONCAT('%', :sopInstanceUid, '%'))
            AND (:storageTier IS NULL OR i.storageTier = :storageTier)
            """,
            countQuery = """
            SELECT COUNT(DISTINCT i) FROM Instance i
            JOIN i.series s
            JOIN s.study st
            WHERE (:seriesId IS NULL OR i.series.id = :seriesId)
            AND (:studyId IS NULL OR s.study.id = :studyId)
            AND (:sopInstanceUid IS NULL OR i.sopInstanceUid LIKE CONCAT('%', :sopInstanceUid, '%'))
            AND (:storageTier IS NULL OR i.storageTier = :storageTier)
            """)
    Page<Instance> findByFiltersWithPagination(
            @Param("seriesId") Long seriesId,
            @Param("studyId") Long studyId,
            @Param("sopInstanceUid") String sopInstanceUid,
            @Param("storageTier") Instance.StorageTier storageTier,
            Pageable pageable
    );
}
