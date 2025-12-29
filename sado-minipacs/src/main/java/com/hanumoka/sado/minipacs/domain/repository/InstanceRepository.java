package com.hanumoka.sado.minipacs.domain.repository;

import com.hanumoka.sado.minipacs.domain.entity.Instance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

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
     * 스토리지 경로로 조회 (중복 방지용)
     *
     * @param storagePath DICOM 파일 저장 경로
     * @return Instance (없으면 empty)
     */
    Optional<Instance> findByStoragePath(String storagePath);
}
