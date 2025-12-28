package com.hanumoka.sado.minipacs.domain.repository;

import com.hanumoka.sado.minipacs.domain.entity.DicomMetadataRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * DicomMetadataRecord Repository
 *
 * WORM (Write Once Read Many) - 불변 메타데이터
 * 멀티테넌시: Hibernate Filter 자동 적용
 */
@Repository
public interface DicomMetadataRecordRepository extends JpaRepository<DicomMetadataRecord, Long> {

    /**
     * Instance ID로 메타데이터 조회 (1:1 관계)
     *
     * @param instanceId Instance PK
     * @return DICOM 메타데이터 (없으면 empty)
     */
    Optional<DicomMetadataRecord> findByInstanceId(Long instanceId);

    /**
     * DICOM SOP Instance UID로 조회
     *
     * @param sopInstanceUid DICOM SOP Instance UID
     * @return DICOM 메타데이터 (없으면 empty)
     */
    Optional<DicomMetadataRecord> findBySopInstanceUid(String sopInstanceUid);

    /**
     * 특정 Study의 모든 메타데이터 조회
     *
     * @param studyInstanceUid DICOM Study Instance UID
     * @return 메타데이터 목록
     */
    List<DicomMetadataRecord> findByStudyInstanceUid(String studyInstanceUid);

    /**
     * 특정 Series의 모든 메타데이터 조회
     *
     * @param seriesInstanceUid DICOM Series Instance UID
     * @return 메타데이터 목록
     */
    List<DicomMetadataRecord> findBySeriesInstanceUid(String seriesInstanceUid);
}
