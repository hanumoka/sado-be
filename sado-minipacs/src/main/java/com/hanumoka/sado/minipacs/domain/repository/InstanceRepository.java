package com.hanumoka.sado.minipacs.domain.repository;

import com.hanumoka.sado.minipacs.domain.entity.Instance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
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

    /**
     * Study Instance UID로 모든 Instance 조회 (N+1 쿼리 방지)
     *
     * <p>WADO-RS Study/Series 메타데이터 API에서 사용합니다.
     * 기존 N+1 쿼리 문제를 해결하기 위해 단일 쿼리로 모든 Instance를 조회합니다.
     *
     * <p>성능 최적화:
     * <ul>
     *   <li>JOIN FETCH로 Series, Study eager loading (N+1 방지)</li>
     *   <li>단일 쿼리로 Study의 모든 Instance 조회</li>
     *   <li>SeriesNumber, InstanceNumber 순으로 정렬</li>
     * </ul>
     *
     * <p>기존 방식 (N+1 쿼리):
     * <pre>
     * List&lt;Series&gt; seriesList = seriesService.findByStudyId(studyId);  // 1 쿼리
     * for (Series s : seriesList) {
     *     instanceService.findBySeriesId(s.getId());  // N 쿼리
     * }
     * // → Study 10 Series × 50 Instances = 511 쿼리
     * </pre>
     *
     * <p>개선된 방식 (단일 쿼리):
     * <pre>
     * instanceRepository.findAllByStudyInstanceUid(studyUid);  // 1 쿼리
     * // → 응답 시간 90% 단축 (5-10초 → 500ms)
     * </pre>
     *
     * @param studyInstanceUid Study Instance UID
     * @return Instance 목록 (Series, Study eager loading, SeriesNumber/InstanceNumber 정렬)
     */
    @Query("""
            SELECT DISTINCT i FROM Instance i
            JOIN FETCH i.series s
            JOIN FETCH s.study st
            WHERE st.studyInstanceUid = :studyInstanceUid
            ORDER BY s.seriesNumber, i.instanceNumber
            """)
    List<Instance> findAllByStudyInstanceUid(@Param("studyInstanceUid") String studyInstanceUid);

    /**
     * Series Instance UID로 모든 Instance 조회 (N+1 쿼리 방지)
     *
     * <p>WADO-RS Series 메타데이터 API에서 사용합니다.
     * JOIN FETCH로 Series, Study를 eager loading하여 N+1 쿼리를 방지합니다.
     *
     * @param seriesInstanceUid Series Instance UID
     * @return Instance 목록 (Series, Study eager loading, InstanceNumber 정렬)
     */
    @Query("""
            SELECT DISTINCT i FROM Instance i
            JOIN FETCH i.series s
            JOIN FETCH s.study st
            WHERE s.seriesInstanceUid = :seriesInstanceUid
            ORDER BY i.instanceNumber
            """)
    List<Instance> findAllBySeriesInstanceUid(@Param("seriesInstanceUid") String seriesInstanceUid);

    // ========== 모니터링 API 용 쿼리 ==========

    /**
     * 최근 업로드된 Instance 조회
     *
     * <p>지정된 시각 이후에 생성된 Instance 목록을 조회합니다.
     * Series, Study를 JOIN FETCH하여 N+1 문제를 방지합니다.
     *
     * @param since 조회 시작 시각
     * @param pageable 페이징 정보
     * @return 최근 업로드된 Instance 목록 (생성 시각 내림차순)
     */
    @Query(value = """
            SELECT DISTINCT i FROM Instance i
            JOIN FETCH i.series s
            JOIN FETCH s.study st
            WHERE i.createdAt >= :since
            ORDER BY i.createdAt DESC
            """,
            countQuery = """
            SELECT COUNT(i) FROM Instance i
            WHERE i.createdAt >= :since
            """)
    Page<Instance> findRecentUploads(
            @Param("since") LocalDateTime since,
            Pageable pageable
    );

    /**
     * 렌더링 대기/진행 중인 Instance 조회
     *
     * <p>TranscodingStatus가 PENDING 또는 PROCESSING인 Instance 목록을 조회합니다.
     *
     * @param statuses 조회할 TranscodingStatus 목록
     * @return 렌더링 대기/진행 중인 Instance 목록
     */
    @Query("""
            SELECT DISTINCT i FROM Instance i
            JOIN FETCH i.series s
            JOIN FETCH s.study st
            WHERE i.transcodingStatus IN :statuses
            ORDER BY i.createdAt ASC
            """)
    List<Instance> findByTranscodingStatusIn(
            @Param("statuses") List<Instance.TranscodingStatus> statuses
    );

    /**
     * TranscodingStatus별 Instance 개수 조회
     *
     * @return [TranscodingStatus, count] 배열 목록
     */
    @Query("""
            SELECT i.transcodingStatus, COUNT(i)
            FROM Instance i
            WHERE i.transcodingStatus IS NOT NULL
            GROUP BY i.transcodingStatus
            """)
    List<Object[]> countByTranscodingStatus();

    /**
     * 특정 시각 이후 특정 상태로 완료된 Instance 개수 조회
     *
     * @param since 조회 시작 시각
     * @param status TranscodingStatus (COMPLETED 또는 FAILED)
     * @return 해당 조건의 Instance 개수
     */
    @Query("""
            SELECT COUNT(i) FROM Instance i
            WHERE i.prerenderedAt >= :since
              AND i.transcodingStatus = :status
            """)
    long countByPrerenderedAtAfterAndTranscodingStatus(
            @Param("since") LocalDateTime since,
            @Param("status") Instance.TranscodingStatus status
    );

    // ========== 테넌트별 사전렌더링 용량 조회 ==========

    /**
     * 테넌트별 사전렌더링 용량 조회
     *
     * <p>사전렌더링 완료된 Instance의 prerenderedTotalSize를 테넌트별로 합산합니다.
     * FileAsset 테이블이 아닌 Instance 테이블에서 조회합니다.
     *
     * @return [tenantId, instanceCount, totalPrerenderedSize] 배열 목록
     */
    @Query("""
            SELECT i.tenantId, COUNT(i), COALESCE(SUM(i.prerenderedTotalSize), 0)
            FROM Instance i
            WHERE i.transcodingStatus = 'COMPLETED'
            GROUP BY i.tenantId
            ORDER BY i.tenantId ASC
            """)
    List<Object[]> findPrerenderedSizeByTenant();

}
