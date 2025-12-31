package com.hanumoka.sado.minipacs.domain.parser;

import lombok.Builder;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.io.DicomInputStream;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * DICOM 파일에서 메타데이터를 추출하는 유틸리티 클래스
 * <p>
 * DCM4CHE 라이브러리를 사용하여 DICOM 파일을 파싱하고
 * Patient, Study, Series, Instance 정보를 추출합니다.
 */
@Slf4j
public class DicomMetadataExtractor {

    /**
     * DICOM 파일에서 메타데이터 추출
     *
     * @param inputStream DICOM 파일 InputStream
     * @return 추출된 DICOM 메타데이터
     * @throws IOException DICOM 파일 파싱 실패 시
     */
    public static DicomMetadata extract(InputStream inputStream) throws IOException {
        try (DicomInputStream dis = new DicomInputStream(inputStream)) {
            // DICOM 파일 파싱
            Attributes attributes = dis.readDataset();

            // 메타데이터 추출 및 반환
            return DicomMetadata.builder()
                    // Patient Level (0010,xxxx)
                    .patientId(attributes.getString(Tag.PatientID))
                    .issuerOfPatientId(attributes.getString(Tag.IssuerOfPatientID))
                    .patientName(attributes.getString(Tag.PatientName))
                    .patientBirthDate(parseDate(attributes.getString(Tag.PatientBirthDate)))
                    .patientSex(attributes.getString(Tag.PatientSex))

                    // Study Level (0020,000D)
                    .studyInstanceUid(attributes.getString(Tag.StudyInstanceUID))
                    .studyDate(parseDate(attributes.getString(Tag.StudyDate)))
                    .studyDescription(attributes.getString(Tag.StudyDescription))

                    // Series Level (0020,000E)
                    .seriesInstanceUid(attributes.getString(Tag.SeriesInstanceUID))
                    .seriesNumber(attributes.getInt(Tag.SeriesNumber, 0))
                    .modality(attributes.getString(Tag.Modality))
                    .seriesDescription(attributes.getString(Tag.SeriesDescription))
                    .bodyPartExamined(attributes.getString(Tag.BodyPartExamined))

                    // Instance Level (0008,0018)
                    .sopInstanceUid(attributes.getString(Tag.SOPInstanceUID))
                    .sopClassUid(attributes.getString(Tag.SOPClassUID))
                    .instanceNumber(attributes.getInt(Tag.InstanceNumber, 0))
                    .imageRows(attributes.getInt(Tag.Rows, 0))
                    .imageColumns(attributes.getInt(Tag.Columns, 0))
                    .numberOfFrames(attributes.getInt(Tag.NumberOfFrames, 1))
                    .build();
        }
    }

    /**
     * DICOM 날짜 형식 (YYYYMMDD) → LocalDate 변환
     *
     * @param dicomDate DICOM 날짜 문자열 (예: "19900315")
     * @return LocalDate 객체 (파싱 실패 시 null)
     */
    private static LocalDate parseDate(String dicomDate) {
        if (dicomDate == null || dicomDate.isEmpty()) {
            return null;
        }

        try {
            // DICOM 날짜 형식: YYYYMMDD
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMdd");
            return LocalDate.parse(dicomDate, formatter);
        } catch (Exception e) {
            log.warn("Failed to parse DICOM date: {}", dicomDate, e);
            return null;
        }
    }

    /**
     * DICOM 메타데이터 DTO
     * <p>
     * DICOM 파일에서 추출한 Patient, Study, Series, Instance 정보를 담는 불변 객체
     */
    @Getter
    @Builder
    public static class DicomMetadata {
        // Patient Level
        private final String patientId;
        private final String issuerOfPatientId;
        private final String patientName;
        private final LocalDate patientBirthDate;
        private final String patientSex;

        // Study Level
        private final String studyInstanceUid;
        private final LocalDate studyDate;
        private final String studyDescription;

        // Series Level
        private final String seriesInstanceUid;
        private final Integer seriesNumber;
        private final String modality;
        private final String seriesDescription;
        private final String bodyPartExamined;

        // Instance Level
        private final String sopInstanceUid;
        private final String sopClassUid;
        private final Integer instanceNumber;
        private final Integer imageRows;
        private final Integer imageColumns;
        private final Integer numberOfFrames;
    }
}
