package com.hanumoka.sado.minipacs.domain.listener;

import com.hanumoka.sado.minipacs.domain.entity.Patient;
import com.hanumoka.sado.minipacs.domain.entity.Study;
import com.hanumoka.sado.minipacs.domain.util.CounterManager;
import jakarta.persistence.*;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDate;
import java.util.List;

/**
 * Patient 통계 필드 자동 유지 Listener
 *
 * <p>Study 생성/수정/삭제 시 Patient의 studiesCount와 lastStudyDate를 자동 업데이트합니다.
 *
 * <p>동작 원리:
 * <ul>
 *   <li>@PostPersist: Study 생성 → studiesCount++, lastStudyDate 업데이트</li>
 *   <li>@PostUpdate: Study.studyDate 변경 → lastStudyDate 재계산</li>
 *   <li>@PostRemove: Study 삭제 → studiesCount--, lastStudyDate 재계산</li>
 * </ul>
 *
 * <p>주의사항:
 * <ul>
 *   <li>JPA Lifecycle callback은 동일 Transaction 내에서 실행됨 (원자성 보장)</li>
 *   <li>Repository 쿼리 대신 Patient.getStudies() 사용 (ConcurrentModificationException 방지)</li>
 *   <li>Reentry Guard로 재귀 호출 방지</li>
 * </ul>
 */
@Slf4j
public class PatientStatisticsListener {

    // Reentry Guard: 재귀 호출 방지
    private static final ThreadLocal<Boolean> processing = ThreadLocal.withInitial(() -> false);

    /**
     * Study 생성 후: 환자 통계 업데이트
     *
     * @param study 생성된 Study
     */
    @PostPersist
    public void afterStudyCreated(Study study) {
        if (processing.get()) {
            return; // 재귀 호출 방지
        }

        try {
            processing.set(true);

            if (study.getPatient() == null) {
                log.warn("Study created without Patient: studyInstanceUid={}", study.getStudyInstanceUid());
                return;
            }

            Patient patient = study.getPatient();
            log.debug("Study created: updating Patient statistics for patientId={}", patient.getId());

            // studiesCount 증가
            patient.setStudiesCount(CounterManager.increment(patient.getStudiesCount()));

            // lastStudyDate 업데이트
            updateLastStudyDate(patient, study.getStudyDate());

            log.debug("Patient statistics updated: studiesCount={}, lastStudyDate={}",
                    patient.getStudiesCount(), patient.getLastStudyDate());
        } finally {
            processing.set(false);
        }
    }

    /**
     * Study 수정 후: studyDate 변경 시 lastStudyDate 재계산
     *
     * @param study 수정된 Study
     */
    @PostUpdate
    public void afterStudyUpdated(Study study) {
        if (processing.get()) {
            return; // 재귀 호출 방지
        }

        try {
            processing.set(true);

            if (study.getPatient() == null) {
                return;
            }

            Patient patient = study.getPatient();
            log.debug("Study updated: recalculating lastStudyDate for patientId={}", patient.getId());

            // studyDate가 변경되었을 수 있으므로 lastStudyDate 재계산
            recalculateLastStudyDate(patient);
        } finally {
            processing.set(false);
        }
    }

    /**
     * Study 삭제 후: 환자 통계 재계산
     *
     * @param study 삭제된 Study
     */
    @PostRemove
    public void afterStudyDeleted(Study study) {
        if (processing.get()) {
            return; // 재귀 호출 방지
        }

        try {
            processing.set(true);

            if (study.getPatient() == null) {
                return;
            }

            Patient patient = study.getPatient();
            log.debug("Study deleted: updating Patient statistics for patientId={}", patient.getId());

            // studiesCount 감소
            patient.setStudiesCount(CounterManager.decrement(patient.getStudiesCount()));

            // lastStudyDate 재계산 (삭제된 Study가 가장 최근이었을 수 있음)
            recalculateLastStudyDate(patient);

            log.debug("Patient statistics updated: studiesCount={}, lastStudyDate={}",
                    patient.getStudiesCount(), patient.getLastStudyDate());
        } finally {
            processing.set(false);
        }
    }

    /**
     * lastStudyDate 업데이트 (새로운 studyDate와 비교)
     *
     * @param patient   환자
     * @param studyDate 새로운 Study 날짜
     */
    private void updateLastStudyDate(Patient patient, LocalDate studyDate) {
        if (studyDate == null) {
            return;
        }

        LocalDate currentLastDate = patient.getLastStudyDate();
        if (currentLastDate == null || studyDate.isAfter(currentLastDate)) {
            patient.setLastStudyDate(studyDate);
        }
    }

    /**
     * lastStudyDate 재계산 (Patient의 studies 컬렉션 사용)
     *
     * <p>Study 삭제 또는 studyDate 변경 시 사용됩니다.
     * Repository 조회 대신 이미 로드된 patient.getStudies()를 사용하여
     * ConcurrentModificationException을 방지합니다.
     *
     * @param patient 환자
     */
    private void recalculateLastStudyDate(Patient patient) {
        // Patient의 studies 컬렉션에서 직접 계산 (repository 쿼리 방지)
        List<Study> studies = patient.getStudies();

        if (studies == null || studies.isEmpty()) {
            // 환자에게 Study가 없으면 null
            patient.setLastStudyDate(null);
            return;
        }

        // 모든 Study의 studyDate 중 최대값 찾기
        LocalDate maxDate = null;
        for (Study study : studies) {
            LocalDate studyDate = study.getStudyDate();
            if (studyDate != null) {
                if (maxDate == null || studyDate.isAfter(maxDate)) {
                    maxDate = studyDate;
                }
            }
        }

        patient.setLastStudyDate(maxDate);
    }
}
