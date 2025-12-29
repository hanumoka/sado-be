package com.hanumoka.sado.minipacs.domain.repository;

import com.hanumoka.sado.minipacs.domain.entity.ValidationLog;
import com.hanumoka.sado.minipacs.domain.enums.DicomValidationLevel;
import com.hanumoka.sado.minipacs.domain.enums.DicomValidationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * ValidationLog Repository
 *
 * DICOM 검증 로그 조회
 * 멀티테넌시: Hibernate Filter 자동 적용
 */
@Repository
public interface ValidationLogRepository extends JpaRepository<ValidationLog, Long> {

    /**
     * DicomMetadataRecord ID로 검증 로그 조회 (최신순)
     *
     * @param dicomMetadataRecordId DicomMetadataRecord PK
     * @return 검증 로그 목록 (최신순)
     */
    List<ValidationLog> findByDicomMetadataRecordIdOrderByCreatedAtDesc(Long dicomMetadataRecordId);

    /**
     * 검증 상태로 필터링
     *
     * @param status 검증 상태 (SUCCESS, WARNING, ERROR)
     * @return 검증 로그 목록
     */
    List<ValidationLog> findByStatus(DicomValidationStatus status);

    /**
     * 특정 검증 레벨의 특정 상태 로그 조회
     *
     * @param level 검증 레벨 (LEVEL_1, LEVEL_2, LEVEL_3)
     * @param status 검증 상태 (SUCCESS, WARNING, ERROR)
     * @return 검증 로그 목록
     */
    List<ValidationLog> findByValidationLevelAndStatus(
            DicomValidationLevel level,
            DicomValidationStatus status
    );

    /**
     * 특정 기간의 검증 로그 조회
     *
     * @param startDate 시작 일시
     * @param endDate 종료 일시
     * @return 검증 로그 목록
     */
    List<ValidationLog> findByCreatedAtBetween(
            LocalDateTime startDate,
            LocalDateTime endDate
    );
}
