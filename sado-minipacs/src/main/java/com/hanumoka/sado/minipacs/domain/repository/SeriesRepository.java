package com.hanumoka.sado.minipacs.domain.repository;

import com.hanumoka.sado.minipacs.domain.entity.Series;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Series Repository
 *
 * 멀티테넌시: Hibernate Filter 자동 적용
 */
@Repository
public interface SeriesRepository extends JpaRepository<Series, Long> {

    /**
     * DICOM Series Instance UID로 조회
     *
     * @param seriesInstanceUid DICOM Series UID
     * @return 시리즈 (없으면 empty)
     */
    Optional<Series> findBySeriesInstanceUid(String seriesInstanceUid);

    /**
     * 특정 검사의 모든 시리즈 조회
     *
     * @param studyId 검사 PK
     * @return 시리즈 목록 (시리즈 번호순)
     */
    List<Series> findByStudyIdOrderBySeriesNumber(Long studyId);

    /**
     * 특정 검사의 특정 Modality 시리즈 조회
     *
     * @param studyId 검사 PK
     * @param modality Modality (US, CT, MR 등)
     * @return 시리즈 목록
     */
    List<Series> findByStudyIdAndModality(Long studyId, String modality);

    /**
     * 검사의 시리즈 개수 조회
     *
     * @param studyId 검사 PK
     * @return 시리즈 개수
     */
    long countByStudyId(Long studyId);

    /**
     * ID로 조회 (Pessimistic Write Lock)
     *
     * <p><strong>@Deprecated</strong> - Optimistic Lock으로 변경됨
     *
     * <p>Series 엔티티에 @Version 필드가 추가되어
     * Optimistic Lock을 자동으로 사용합니다.
     * 이 메서드 대신 findById()를 사용하세요.
     *
     * <p>변경 이유:
     * <ul>
     *   <li>Pessimistic Lock: Deadlock 위험, 성능 오버헤드</li>
     *   <li>Optimistic Lock: OptimisticLockException 발생 시 재시도로 해결</li>
     * </ul>
     *
     * <p>마이그레이션 가이드:
     * <pre>{@code
     * // ❌ 기존 코드 (Pessimistic Lock)
     * Series series = seriesRepository.findByIdWithLock(id).orElseThrow();
     * series.incrementInstanceCount();
     * seriesRepository.save(series);
     *
     * // ✅ 새 코드 (Optimistic Lock + 재시도)
     * @Retryable(value = OptimisticLockingFailureException.class, maxAttempts = 3)
     * public void incrementInstance(Long id) {
     *     Series series = seriesRepository.findById(id).orElseThrow();
     *     series.incrementInstanceCount();
     *     seriesRepository.save(series);  // @Version 자동 증가
     * }
     * }</pre>
     *
     * @param id Series PK
     * @return Series (없으면 empty)
     * @deprecated Series 엔티티에 @Version 필드 추가됨, findById() 사용 권장
     */
    @Deprecated(since = "POC Week 8", forRemoval = true)
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM Series s WHERE s.id = :id")
    Optional<Series> findByIdWithLock(@Param("id") Long id);
}
