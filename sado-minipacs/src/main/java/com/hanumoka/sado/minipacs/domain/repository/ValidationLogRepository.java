package com.hanumoka.sado.minipacs.domain.repository;

import com.hanumoka.sado.minipacs.domain.entity.ValidationLog;
import com.hanumoka.sado.minipacs.domain.enums.ValidationResult;
import com.hanumoka.sado.minipacs.domain.enums.ValidationType;
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
     * Instance ID로 검증 로그 조회 (최신순)
     *
     * @param instanceId Instance PK
     * @return 검증 로그 목록 (최신순)
     */
    List<ValidationLog> findByInstanceIdOrderByCreatedAtDesc(Long instanceId);

    /**
     * 검증 결과로 필터링
     *
     * @param result 검증 결과 (SUCCESS, WARNING, ERROR)
     * @return 검증 로그 목록
     */
    List<ValidationLog> findByValidationResult(ValidationResult result);

    /**
     * 특정 검증 타입의 특정 결과 로그 조회
     *
     * @param type 검증 타입 (DICOM_CONFORMANCE, MANDATORY_TAGS 등)
     * @param result 검증 결과 (SUCCESS, WARNING, ERROR)
     * @return 검증 로그 목록
     */
    List<ValidationLog> findByValidationTypeAndValidationResult(
            ValidationType type,
            ValidationResult result
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
