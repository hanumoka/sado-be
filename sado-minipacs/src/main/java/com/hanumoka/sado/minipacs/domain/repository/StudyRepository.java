package com.hanumoka.sado.minipacs.domain.repository;

import com.hanumoka.sado.minipacs.domain.entity.Study;
import org.springframework.data.jpa.repository.JpaRepository;
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
}
