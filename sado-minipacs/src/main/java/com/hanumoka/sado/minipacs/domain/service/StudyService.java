package com.hanumoka.sado.minipacs.domain.service;

import com.hanumoka.sado.common.exception.ResourceNotFoundException;
import com.hanumoka.sado.common.tenant.TenantProvider;
import com.hanumoka.sado.common.util.UuidV7Generator;
import com.hanumoka.sado.minipacs.domain.entity.Patient;
import com.hanumoka.sado.minipacs.domain.entity.Study;
import com.hanumoka.sado.minipacs.domain.repository.StudyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Study Service
 *
 * 검사(Study) 생성, 조회 기능 제공
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
@Slf4j
public class StudyService {

    private final StudyRepository studyRepository;
    private final PatientService patientService;
    private final TenantProvider tenantProvider;

    /**
     * Study PK로 조회
     *
     * @param id Study PK
     * @return Study 엔티티
     * @throws ResourceNotFoundException Study가 존재하지 않는 경우
     */
    public Study findById(Long id) {
        return studyRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Study not found with id: " + id));
    }

    /**
     * DICOM Study Instance UID로 조회
     *
     * @param studyInstanceUid DICOM Study Instance UID (0020,000D)
     * @return Study 엔티티 (Optional)
     */
    public Optional<Study> findByStudyInstanceUid(String studyInstanceUid) {
        return studyRepository.findByStudyInstanceUid(studyInstanceUid);
    }

    /**
     * 전체 Study 조회
     *
     * @return 모든 Study 목록
     */
    public List<Study> findAll() {
        return studyRepository.findAll();
    }

    /**
     * 특정 환자의 모든 검사 조회 (최신순)
     *
     * @param patientId Patient PK
     * @return Study 목록
     */
    public List<Study> findByPatientId(Long patientId) {
        // Patient 존재 여부 확인
        patientService.findById(patientId);

        return studyRepository.findByPatientIdOrderByStudyDateDesc(patientId);
    }

    /**
     * 특정 환자의 검사 개수 조회
     *
     * @param patientId Patient PK
     * @return 검사 개수
     */
    public long countByPatientId(Long patientId) {
        return studyRepository.countByPatientId(patientId);
    }

    /**
     * 특정 환자의 최신 검사 날짜 조회
     *
     * @param patientId Patient PK
     * @return 최신 검사 날짜 (없으면 null)
     */
    public LocalDate getLastStudyDate(Long patientId) {
        return studyRepository.findLatestStudyDateByPatientId(patientId);
    }

    /**
     * 여러 환자의 검사 통계 조회 (N+1 방지)
     *
     * <p>목록 조회 시 한 번의 쿼리로 모든 환자의 통계를 가져옴
     *
     * @param patientIds 환자 ID 목록
     * @return Map&lt;patientId, StudyStats&gt;
     */
    public java.util.Map<Long, StudyStats> getStudyStatsByPatientIds(java.util.List<Long> patientIds) {
        if (patientIds == null || patientIds.isEmpty()) {
            return java.util.Collections.emptyMap();
        }

        java.util.List<Object[]> results = studyRepository.findStudyStatsByPatientIds(patientIds);

        java.util.Map<Long, StudyStats> statsMap = new java.util.HashMap<>();
        for (Object[] row : results) {
            Long patientId = (Long) row[0];
            Long count = (Long) row[1];
            LocalDate lastStudyDate = (LocalDate) row[2];
            statsMap.put(patientId, new StudyStats(count.intValue(), lastStudyDate));
        }

        return statsMap;
    }

    /**
     * 환자별 검사 통계 DTO
     */
    public record StudyStats(int studiesCount, LocalDate lastStudyDate) {}

    /**
     * DICOM-Web QIDO-RS 필터링 조회
     *
     * <p>CRITICAL: OOM 방지
     * - findAll() + Stream 필터링 대신 DB 쿼리 활용
     * - null 파라미터는 무시 (동적 쿼리)
     * - JOIN FETCH로 N+1 문제 방지
     *
     * @param patientId DICOM PatientID (nullable)
     * @param patientName 환자 이름 (nullable, 부분 일치)
     * @param studyDate 검사 날짜 (nullable)
     * @return Study 목록 (Patient eager loading)
     */
    public List<Study> findByDicomWebFilters(String patientId, String patientName, LocalDate studyDate) {
        log.debug("Searching studies with filters: patientId={}, patientName={}, studyDate={}",
                patientId, patientName, studyDate);

        List<Study> studies = studyRepository.findByDicomWebFilters(patientId, patientName, studyDate);

        log.debug("Found {} studies matching filters", studies.size());
        return studies;
    }

    /**
     * Study 생성
     *
     * @param study Study 엔티티 (patient 관계 설정 필요)
     * @return 저장된 Study
     */
    @Transactional
    public Study createStudy(Study study) {
        // Patient 존재 여부 확인
        if (study.getPatient() == null || study.getPatient().getId() == null) {
            throw new IllegalArgumentException("Study must have a patient");
        }

        patientService.findById(study.getPatient().getId());

        log.info("Creating new study: studyInstanceUid={}, patientId={}",
                study.getStudyInstanceUid(),
                study.getPatient().getId());

        return studyRepository.save(study);
    }

    /**
     * Study 찾기 또는 생성
     *
     * <p>변경 사항 (2026-01-05): MySQL Native Query Upsert 패턴
     * <ul>
     *   <li>Insert-First + Exception Flow Control 제거</li>
     *   <li>MySQL ON DUPLICATE KEY UPDATE로 원자적 Upsert</li>
     *   <li>LAST_INSERT_ID(id) 트릭으로 기존/신규 ID 모두 반환</li>
     *   <li>Exception 없음 → Hibernate Session 오염 없음</li>
     *   <li>REQUIRES_NEW 불필요 → 상위 트랜잭션과 자연스럽게 통합</li>
     * </ul>
     *
     * <p>동작 원리:
     * <ol>
     *   <li>MySQL Upsert 실행 (INSERT 또는 UPDATE)</li>
     *   <li>LAST_INSERT_ID()로 ID 조회 (신규: auto_increment, 기존: 설정된 id)</li>
     *   <li>findById()로 엔티티 반환 (1차 캐시 활용)</li>
     * </ol>
     *
     * <p>이전 방식의 문제점 (Insert-First):
     * <ul>
     *   <li>DataIntegrityViolationException 발생 시 Session 오염</li>
     *   <li>REQUIRES_NEW가 EntityManager를 격리하지 못함 (ThreadLocal 공유)</li>
     *   <li>detach()/clear() 후에도 "null identifier" 오류 발생</li>
     * </ul>
     *
     * @param studyInstanceUid DICOM Study Instance UID
     * @param patient 소속 환자
     * @param studyDate 검사 날짜
     * @param studyDescription 검사 설명
     * @return 찾거나 생성된 Study
     */
    @Transactional
    public Study findOrCreateStudy(
            String studyInstanceUid,
            Patient patient,
            java.time.LocalDate studyDate,
            String studyDescription) {

        // 1. MySQL Upsert 실행 (Exception 없음, 완전 원자적)
        Long tenantId = tenantProvider.getCurrentTenantId();
        String uuid = UuidV7Generator.generateString();
        studyRepository.upsertStudy(
            tenantId,
            uuid,
            studyInstanceUid,
            patient.getId(),
            studyDate,
            studyDescription
        );

        // 2. LAST_INSERT_ID() 조회 (같은 커넥션 내에서 유효)
        Long studyId = studyRepository.getLastInsertId();

        log.debug("Upsert study completed: studyInstanceUid={}, resultId={}",
                studyInstanceUid, studyId);

        // 3. 엔티티 반환 (1차 캐시 활용)
        return studyRepository.findById(studyId)
                .orElseThrow(() -> new IllegalStateException(
                        "Study should exist after upsert: " + studyInstanceUid));
    }

    /**
     * Study 업데이트
     *
     * @param study Study 엔티티
     * @return 업데이트된 Study
     */
    @Transactional
    public Study updateStudy(Study study) {
        // 존재 여부 확인
        findById(study.getId());

        log.info("Updating study: id={}", study.getId());
        return studyRepository.save(study);
    }

    /**
     * Study 삭제
     *
     * @param id Study PK
     */
    @Transactional
    public void deleteStudy(Long id) {
        Study study = findById(id);

        log.info("Deleting study: id={}, studyInstanceUid={}",
                id,
                study.getStudyInstanceUid());

        studyRepository.delete(study);
    }
}
