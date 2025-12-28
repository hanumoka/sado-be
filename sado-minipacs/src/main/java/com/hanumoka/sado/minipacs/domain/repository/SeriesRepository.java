package com.hanumoka.sado.minipacs.domain.repository;

import com.hanumoka.sado.minipacs.domain.entity.Series;
import org.springframework.data.jpa.repository.JpaRepository;
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
}
