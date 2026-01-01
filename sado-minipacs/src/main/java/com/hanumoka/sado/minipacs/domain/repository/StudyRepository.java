package com.hanumoka.sado.minipacs.domain.repository;

import com.hanumoka.sado.minipacs.domain.entity.Study;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Study Repository
 *
 * 멀티테넌시: Hibernate Filter 자동 적용
 */
@Repository
public interface StudyRepository extends JpaRepository<Study, Long> {

    /**
     * DICOM Study Instance UID로 조회
     *
     * @param studyInstanceUid DICOM Study UID
     * @return 검사 (없으면 empty)
     */
    Optional<Study> findByStudyInstanceUid(String studyInstanceUid);

    /**
     * 특정 환자의 모든 검사 조회
     *
     * @param patientId 환자 PK
     * @return 검사 목록 (최신순)
     */
    List<Study> findByPatientIdOrderByStudyDateDesc(Long patientId);

    /**
     * 환자의 검사 개수 조회
     *
     * @param patientId 환자 PK
     * @return 검사 개수
     */
    long countByPatientId(Long patientId);

    /**
     * ID로 조회 (Pessimistic Write Lock)
     *
     * <p>카운터 증감 시 동시성 문제를 해결하기 위해 사용합니다.
     * Series 추가/삭제 시 numberOfSeries, numberOfInstances 카운터를
     * 안전하게 업데이트하기 위해 행 수준 잠금을 획득합니다.
     *
     * <p>사용 예시:
     * <pre>
     * {@code
     * @Transactional
     * public Series createSeries(Series series) {
     *     // Study를 락과 함께 조회 (다른 트랜잭션은 대기)
     *     Study study = studyRepository.findByIdWithLock(series.getStudy().getId())
     *         .orElseThrow(() -> new ResourceNotFoundException("Study not found"));
     *
     *     // 카운터 안전하게 증가
     *     study.incrementSeriesCount();
     *     return seriesRepository.save(series);
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
     * @param id Study PK
     * @return Study (없으면 empty)
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM Study s WHERE s.id = :id")
    Optional<Study> findByIdWithLock(@Param("id") Long id);
}
