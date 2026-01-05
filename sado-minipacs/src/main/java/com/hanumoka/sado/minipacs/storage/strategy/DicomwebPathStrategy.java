package com.hanumoka.sado.minipacs.storage.strategy;

import com.hanumoka.sado.minipacs.storage.dto.DicomFileMetadata;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * DICOMweb 표준 경로 생성 전략
 *
 * <p>경로 구조:
 * <pre>
 * studies/{studyInstanceUid}/series/{seriesInstanceUid}/instances/{sopInstanceUid}.dcm
 * </pre>
 *
 * <p>예시:
 * <pre>
 * studies/1.2.840.113619.2.55.1.123/series/1.2.840.113619.2.55.1.456/instances/1.2.840.113619.2.55.1.789.dcm
 * </pre>
 *
 * <p>DICOMweb 표준 (DICOM PS3.18):
 * - WADO-RS (Web Access to DICOM Objects - RESTful Services)
 * - 계층적 URL 구조: studies → series → instances
 * - 참고: <a href="https://www.dicomstandard.org/using/dicomweb">DICOMweb Standard</a>
 *
 * <p>장점:
 * <ul>
 *   <li>DICOM 표준 준수</li>
 *   <li>계층 구조 명확 (Study → Series → Instance)</li>
 *   <li>DICOMweb API와 호환</li>
 * </ul>
 *
 * <p>단점:
 * <ul>
 *   <li>경로 길이가 김 (UID가 길어서)</li>
 *   <li>날짜별 정리 어려움</li>
 * </ul>
 */
@Slf4j
@Component("dicomwebPathStrategy")
public class DicomwebPathStrategy implements StoragePathStrategy {

    @Override
    public String generatePath(DicomFileMetadata metadata) {
        // Null 체크
        if (metadata == null) {
            throw new IllegalArgumentException("DicomFileMetadata cannot be null");
        }

        if (metadata.getStudyInstanceUid() == null || metadata.getStudyInstanceUid().isEmpty()) {
            throw new IllegalArgumentException("StudyInstanceUid cannot be null or empty");
        }

        if (metadata.getSeriesInstanceUid() == null || metadata.getSeriesInstanceUid().isEmpty()) {
            throw new IllegalArgumentException("SeriesInstanceUid cannot be null or empty");
        }

        if (metadata.getSopInstanceUid() == null || metadata.getSopInstanceUid().isEmpty()) {
            throw new IllegalArgumentException("SopInstanceUid cannot be null or empty");
        }

        // DICOMweb 표준 경로 생성
        String path = String.format("studies/%s/series/%s/instances/%s.dcm",
                metadata.getStudyInstanceUid(),
                metadata.getSeriesInstanceUid(),
                metadata.getSopInstanceUid());

        log.debug("Generated DICOMweb path: {}", path);

        return path;
    }

    @Override
    public String getStrategyName() {
        return "DICOMweb Standard Path Strategy";
    }
}
