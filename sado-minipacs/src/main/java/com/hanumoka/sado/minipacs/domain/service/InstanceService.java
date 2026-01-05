package com.hanumoka.sado.minipacs.domain.service;

import com.hanumoka.sado.common.exception.ResourceNotFoundException;
import com.hanumoka.sado.minipacs.domain.entity.Instance;
import com.hanumoka.sado.minipacs.domain.entity.Series;
import com.hanumoka.sado.minipacs.domain.repository.InstanceRepository;
import com.hanumoka.sado.minipacs.storage.service.DicomStorageService;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Instance Service
 *
 * DICOM Instance 생성, 조회 기능 제공
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
@Slf4j
public class InstanceService {

    private final InstanceRepository instanceRepository;
    private final SeriesService seriesService;
    private final DicomStorageService dicomStorageService;
    private final EntityManager entityManager;

    /**
     * Instance PK로 조회
     *
     * @param id Instance PK
     * @return Instance 엔티티
     * @throws ResourceNotFoundException Instance가 존재하지 않는 경우
     */
    public Instance findById(Long id) {
        return instanceRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Instance not found with id: " + id));
    }

    /**
     * DICOM SOP Instance UID로 조회
     *
     * @param sopInstanceUid DICOM SOP Instance UID (0008,0018)
     * @return Instance 엔티티 (Optional)
     */
    public Optional<Instance> findBySopInstanceUid(String sopInstanceUid) {
        return instanceRepository.findBySopInstanceUid(sopInstanceUid);
    }

    /**
     * Storage Path로 Instance 조회
     * 파일 중복 업로드 방지용 (같은 경로에 이미 저장된 파일이 있는지 확인)
     *
     * @param storagePath Storage Path (예: /data/dicom/2025/01/15/abc123.dcm)
     * @return Instance 엔티티 (Optional)
     */
    public Optional<Instance> findByStoragePath(String storagePath) {
        return instanceRepository.findByStoragePath(storagePath);
    }

    /**
     * 특정 시리즈의 모든 Instance 조회 (Instance Number 순)
     *
     * @param seriesId Series PK
     * @return Instance 목록
     */
    public List<Instance> findBySeriesId(Long seriesId) {
        // Series 존재 여부 확인
        seriesService.findById(seriesId);

        return instanceRepository.findBySeriesIdOrderByInstanceNumber(seriesId);
    }

    /**
     * 특정 시리즈의 특정 Instance Number 조회
     *
     * @param seriesId Series PK
     * @param instanceNumber Instance Number
     * @return Instance 엔티티 (Optional)
     */
    public Optional<Instance> findBySeriesIdAndInstanceNumber(Long seriesId, Integer instanceNumber) {
        return instanceRepository.findBySeriesIdAndInstanceNumber(seriesId, instanceNumber);
    }

    /**
     * 특정 시리즈의 Instance 개수 조회
     *
     * @param seriesId Series PK
     * @return Instance 개수
     */
    public long countBySeriesId(Long seriesId) {
        return instanceRepository.countBySeriesId(seriesId);
    }

    /**
     * Instance 생성
     *
     * @param instance Instance 엔티티 (series 관계 설정 필요)
     * @return 저장된 Instance
     */
    @Transactional
    public Instance createInstance(Instance instance) {
        // Series 존재 여부 확인
        if (instance.getSeries() == null || instance.getSeries().getId() == null) {
            throw new IllegalArgumentException("Instance must have a series");
        }

        // Series 조회 (관리되는 엔티티)
        Series series = seriesService.findById(instance.getSeries().getId());

        // 파일 경로 중복 확인
        if (instance.getStoragePath() != null) {
            Optional<Instance> existingInstance = findByStoragePath(instance.getStoragePath());
            if (existingInstance.isPresent()) {
                throw new IllegalArgumentException("Instance with storagePath already exists: " + instance.getStoragePath());
            }
        }

        log.info("Creating new instance: sopInstanceUid={}, seriesId={}",
                instance.getSopInstanceUid(),
                series.getId());

        // 비즈니스 메서드 호출 (역정규화 필드 자동 업데이트)
        series.addInstance(instance);

        return instanceRepository.save(instance);
    }

    /**
     * Instance 찾기 또는 생성
     *
     * DICOM C-STORE 수신 시 사용:
     * 1. SOP Instance UID로 기존 Instance 검색
     * 2. 없으면 새로운 Instance 생성
     *
     * @param sopInstanceUid DICOM SOP Instance UID
     * @param series 소속 시리즈
     * @param instanceNumber Instance Number
     * @param sopClassUid SOP Class UID
     * @param storagePath DICOM 파일 저장 경로 (SeaweedFS/S3)
     * @param fileSize 파일 크기 (bytes)
     * @return 찾거나 생성된 Instance
     */
    @Transactional
    public Instance findOrCreateInstance(
            String sopInstanceUid,
            Series series,
            Integer instanceNumber,
            String sopClassUid,
            String storagePath,
            Long fileSize) {

        // CRITICAL: Race Condition 해결
        // - 초기 검사 제거: TOCTOU (Time-of-Check Time-of-Use) 취약점 방지
        // - Insert-first 전략: DB Unique Constraint를 활용한 동시성 제어

        try {
            // 1. Instance 생성
            Instance newInstance = Instance.builder()
                    .series(series)
                    .sopInstanceUid(sopInstanceUid)
                    .sopClassUid(sopClassUid)
                    .instanceNumber(instanceNumber)
                    .storagePath(storagePath)
                    .fileSize(fileSize)
                    .build();

            log.info("Creating new instance from DICOM: sopInstanceUid={}, seriesId={}, storagePath={}",
                    sopInstanceUid,
                    series.getId(),
                    storagePath);

            // 2. 양방향 관계 설정 + 역정규화 필드 자동 업데이트
            series.addInstance(newInstance);

            // 3. DB 저장 (Unique Constraint 위반 시 예외 발생)
            return instanceRepository.saveAndFlush(newInstance);

        } catch (DataIntegrityViolationException e) {
            // Race condition 발생: 다른 스레드가 먼저 저장함
            log.warn("Race condition detected for instance: {}, refreshing series state", sopInstanceUid);

            // CRITICAL: series의 메모리 상태를 DB 상태로 되돌림
            // - series.addInstance()로 인한 numberOfInstances 증가를 원복
            // - 트랜잭션이 커밋되면 잘못된 값이 DB에 저장되는 것을 방지
            entityManager.refresh(series);

            // 이미 존재하는 Instance 조회 후 반환
            return instanceRepository.findBySopInstanceUid(sopInstanceUid)
                    .orElseThrow(() -> new IllegalStateException(
                            "Instance should exist after DataIntegrityViolationException"));
        }
    }

    /**
     * Instance 업데이트
     *
     * @param instance Instance 엔티티
     * @return 업데이트된 Instance
     */
    @Transactional
    public Instance updateInstance(Instance instance) {
        // 존재 여부 확인
        findById(instance.getId());

        log.info("Updating instance: id={}", instance.getId());
        return instanceRepository.save(instance);
    }

    /**
     * Instance 삭제
     *
     * CRITICAL: 트랜잭션 일관성 보장을 위한 삭제 순서 변경
     * <ol>
     *   <li>S3에서 DICOM 파일 먼저 삭제 (실패 시 예외 발생 → 트랜잭션 롤백)</li>
     *   <li>S3 삭제 성공 시에만 DB에서 Instance 삭제</li>
     * </ol>
     *
     * <p>이전 문제점:
     * - DB 먼저 삭제 → S3 삭제 실패 시 고아 파일 발생
     * - DB는 이미 커밋되어 복구 불가능
     *
     * <p>개선 효과:
     * - S3 삭제 실패 시 트랜잭션 롤백으로 DB 보존
     * - 데이터 일관성 보장
     *
     * @param id Instance PK
     */
    @Transactional
    public void deleteInstance(Long id) {
        Instance instance = findById(id);
        Series series = instance.getSeries();
        String storagePath = instance.getStoragePath();

        log.info("Deleting instance: id={}, sopInstanceUid={}, storagePath={}",
                id,
                instance.getSopInstanceUid(),
                storagePath);

        // 1. S3에서 DICOM 파일 먼저 삭제 (실패 시 예외 발생 → 트랜잭션 롤백)
        if (storagePath != null && !storagePath.isEmpty()) {
            dicomStorageService.deleteDicomFile(storagePath);
            log.info("Deleted S3 file: {}", storagePath);
        }

        // 2. S3 삭제 성공 시에만 DB에서 Instance 삭제
        // 비즈니스 메서드 호출 (역정규화 필드 자동 업데이트)
        if (series != null) {
            series.removeInstance(instance);
        }

        instanceRepository.delete(instance);
        log.info("Deleted instance from DB: id={}", id);
    }
}
