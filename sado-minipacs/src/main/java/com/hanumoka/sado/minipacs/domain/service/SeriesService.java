package com.hanumoka.sado.minipacs.domain.service;

import com.hanumoka.sado.common.exception.ResourceNotFoundException;
import com.hanumoka.sado.minipacs.domain.entity.Series;
import com.hanumoka.sado.minipacs.domain.entity.Study;
import com.hanumoka.sado.minipacs.domain.repository.SeriesRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Series Service
 *
 * 시리즈(Series) 생성, 조회 기능 제공
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
@Slf4j
public class SeriesService {

    private final SeriesRepository seriesRepository;
    private final StudyService studyService;

    /**
     * Series PK로 조회
     *
     * @param id Series PK
     * @return Series 엔티티
     * @throws ResourceNotFoundException Series가 존재하지 않는 경우
     */
    public Series findById(Long id) {
        return seriesRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Series not found with id: " + id));
    }

    /**
     * DICOM Series Instance UID로 조회
     *
     * @param seriesInstanceUid DICOM Series Instance UID (0020,000E)
     * @return Series 엔티티 (Optional)
     */
    public Optional<Series> findBySeriesInstanceUid(String seriesInstanceUid) {
        return seriesRepository.findBySeriesInstanceUid(seriesInstanceUid);
    }

    /**
     * 특정 검사(Study)의 모든 시리즈 조회 (시리즈 번호순)
     *
     * @param studyId Study PK
     * @return Series 목록
     */
    public List<Series> findByStudyId(Long studyId) {
        // Study 존재 여부 확인
        studyService.findById(studyId);

        return seriesRepository.findByStudyIdOrderBySeriesNumber(studyId);
    }

    /**
     * 특정 검사의 특정 Modality 시리즈 조회
     *
     * @param studyId Study PK
     * @param modality Modality (US, CT, MR 등)
     * @return Series 목록
     */
    public List<Series> findByStudyIdAndModality(Long studyId, String modality) {
        // Study 존재 여부 확인
        studyService.findById(studyId);

        return seriesRepository.findByStudyIdAndModality(studyId, modality);
    }

    /**
     * 특정 검사의 시리즈 개수 조회
     *
     * @param studyId Study PK
     * @return 시리즈 개수
     */
    public long countByStudyId(Long studyId) {
        return seriesRepository.countByStudyId(studyId);
    }

    /**
     * Series 생성
     *
     * @param series Series 엔티티 (study 관계 설정 필요)
     * @return 저장된 Series
     */
    @Transactional
    public Series createSeries(Series series) {
        // Study 존재 여부 확인
        if (series.getStudy() == null || series.getStudy().getId() == null) {
            throw new IllegalArgumentException("Series must have a study");
        }

        // Study 조회 (관리되는 엔티티)
        Study study = studyService.findById(series.getStudy().getId());

        log.info("Creating new series: seriesInstanceUid={}, studyId={}",
                series.getSeriesInstanceUid(),
                study.getId());

        // 비즈니스 메서드 호출 (역정규화 필드 자동 업데이트)
        study.addSeries(series);

        return seriesRepository.save(series);
    }

    /**
     * Series 찾기 또는 생성
     *
     * DICOM C-STORE 수신 시 사용:
     * 1. Series Instance UID로 기존 Series 검색
     * 2. 없으면 새로운 Series 생성
     *
     * @param seriesInstanceUid DICOM Series Instance UID
     * @param study 소속 검사
     * @param seriesNumber 시리즈 번호
     * @param modality Modality (US, CT, MR 등)
     * @param seriesDescription 시리즈 설명
     * @param bodyPartExamined 검사 부위
     * @return 찾거나 생성된 Series
     */
    @Transactional
    public Series findOrCreateSeries(
            String seriesInstanceUid,
            Study study,
            Integer seriesNumber,
            String modality,
            String seriesDescription,
            String bodyPartExamined) {

        // 1. 기존 Series 검색
        Optional<Series> existingSeries = findBySeriesInstanceUid(seriesInstanceUid);

        if (existingSeries.isPresent()) {
            log.info("Found existing series: id={}, seriesInstanceUid={}",
                    existingSeries.get().getId(),
                    seriesInstanceUid);
            return existingSeries.get();
        }

        // 2. 새 Series 생성 (Builder 패턴)
        Series newSeries = Series.builder()
                .study(study)
                .seriesInstanceUid(seriesInstanceUid)
                .seriesNumber(seriesNumber)
                .modality(modality)
                .seriesDescription(seriesDescription)
                .bodyPartExamined(bodyPartExamined)
                .build();

        log.info("Creating new series from DICOM: seriesInstanceUid={}, studyId={}, modality={}",
                seriesInstanceUid,
                study.getId(),
                modality);

        // 비즈니스 메서드 호출 (역정규화 필드 자동 업데이트)
        study.addSeries(newSeries);

        return seriesRepository.save(newSeries);
    }

    /**
     * Series 업데이트
     *
     * @param series Series 엔티티
     * @return 업데이트된 Series
     */
    @Transactional
    public Series updateSeries(Series series) {
        // 존재 여부 확인
        findById(series.getId());

        log.info("Updating series: id={}", series.getId());
        return seriesRepository.save(series);
    }

    /**
     * Series 삭제
     *
     * @param id Series PK
     */
    @Transactional
    public void deleteSeries(Long id) {
        Series series = findById(id);
        Study study = series.getStudy();

        log.info("Deleting series: id={}, seriesInstanceUid={}",
                id,
                series.getSeriesInstanceUid());

        // 비즈니스 메서드 호출 (역정규화 필드 자동 업데이트)
        if (study != null) {
            study.removeSeries(series);
        }

        seriesRepository.delete(series);
    }
}
