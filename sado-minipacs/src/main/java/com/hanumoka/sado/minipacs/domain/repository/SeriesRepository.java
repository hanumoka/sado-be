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
     * <p>카운터 증감 시 동시성 문제를 해결하기 위해 사용합니다.
     * Instance 추가/삭제 시 numberOfInstances 카운터를
     * 안전하게 업데이트하기 위해 행 수준 잠금을 획득합니다.
     *
     * <p>사용 예시:
     * <pre>
     * {@code
     * @Transactional
     * public Instance createInstance(Instance instance) {
     *     // Series를 락과 함께 조회 (다른 트랜잭션은 대기)
     *     Series series = seriesRepository.findByIdWithLock(instance.getSeries().getId())
     *         .orElseThrow(() -> new ResourceNotFoundException("Series not found"));
     *
     *     // 카운터 안전하게 증가
     *     series.incrementInstanceCount();
     *     return instanceRepository.save(instance);
     * }
     * }
     * </pre>
     *
     * <p>주의사항:
     * <ul>
     *   <li>락 획득 중 다른 트랜잭션은 대기합니다 (성능 오버헤드)</li>
     *   <li>데드락 방지를 위해 짧은 트랜잭션 사용 권장</li>
     *   <li>읽기 전용 작업에서는 사용하지 마세요</li>
     * </ul>
     *
     * @param id Series PK
     * @return Series (없으면 empty)
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM Series s WHERE s.id = :id")
    Optional<Series> findByIdWithLock(@Param("id") Long id);
}
