package com.hanumoka.sado.minipacs.domain.util;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.io.DicomInputStream;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/**
 * DICOM 메타데이터 추출 유틸리티
 *
 * <p>DCM4CHE 라이브러리를 사용하여 DICOM 파일에서 메타데이터를 추출합니다.
 *
 * <p>추출 항목:
 * <ul>
 *   <li>Patient: PatientID, PatientName, BirthDate, Sex</li>
 *   <li>Study: StudyInstanceUID, StudyDate, StudyDescription</li>
 *   <li>Series: SeriesInstanceUID, SeriesNumber, Modality, SeriesDescription, BodyPartExamined</li>
 *   <li>Instance: SOPInstanceUID, SOPClassUID, Rows, Columns, NumberOfFrames, InstanceNumber</li>
 * </ul>
 *
 * <p>사용 예시:
 * <pre>{@code
 * InputStream inputStream = multipartFile.getInputStream();
 * DicomMetadata metadata = DicomMetadataExtractor.extract(inputStream);
 *
 * Patient patient = patientService.findOrCreatePatient(
 *     metadata.getPatientId(),
 *     metadata.getIssuerOfPatientId(),
 *     metadata.getPatientName(),
 *     metadata.getPatientBirthDate(),
 *     metadata.getPatientSex()
 * );
 * }</pre>
 */
@Slf4j
public class DicomMetadataExtractor {

    /**
     * DICOM 파일에서 메타데이터 추출
     *
     * @param inputStream DICOM 파일 입력 스트림
     * @return 추출된 DICOM 메타데이터
     * @throws IOException DICOM 파일 읽기 실패 시
     */
    public static DicomMetadata extract(InputStream inputStream) throws IOException {
        try (DicomInputStream dis = new DicomInputStream(inputStream)) {
            // DICOM 파일 파싱
            Attributes attributes = dis.readDataset();

            // 메타데이터 추출
            DicomMetadata metadata = new DicomMetadata();

            // ========== Patient Level ==========
            metadata.patientId = attributes.getString(Tag.PatientID);
            metadata.issuerOfPatientId = attributes.getString(Tag.IssuerOfPatientID);
            metadata.patientName = attributes.getString(Tag.PatientName);
            metadata.patientBirthDate = parseDicomDate(attributes.getString(Tag.PatientBirthDate));
            metadata.patientSex = attributes.getString(Tag.PatientSex);

            // ========== Study Level ==========
            metadata.studyInstanceUid = attributes.getString(Tag.StudyInstanceUID);
            metadata.studyDate = parseDicomDate(attributes.getString(Tag.StudyDate));
            metadata.studyDescription = attributes.getString(Tag.StudyDescription);

            // ========== Series Level ==========
            metadata.seriesInstanceUid = attributes.getString(Tag.SeriesInstanceUID);
            metadata.seriesNumber = attributes.getInt(Tag.SeriesNumber, 0);
            metadata.modality = attributes.getString(Tag.Modality);
            metadata.seriesDescription = attributes.getString(Tag.SeriesDescription);
            metadata.bodyPartExamined = attributes.getString(Tag.BodyPartExamined);
            metadata.manufacturer = attributes.getString(Tag.Manufacturer);
            metadata.manufacturerModelName = attributes.getString(Tag.ManufacturerModelName);

            // ========== Instance Level ==========
            metadata.sopInstanceUid = attributes.getString(Tag.SOPInstanceUID);
            metadata.sopClassUid = attributes.getString(Tag.SOPClassUID);
            metadata.instanceNumber = attributes.getInt(Tag.InstanceNumber, 0);
            metadata.imageRows = attributes.getInt(Tag.Rows, 0);
            metadata.imageColumns = attributes.getInt(Tag.Columns, 0);
            metadata.numberOfFrames = attributes.getInt(Tag.NumberOfFrames, 0);

            log.debug("DICOM metadata extracted: PatientID={}, StudyUID={}, SeriesUID={}, SOPInstanceUID={}",
                    metadata.patientId,
                    metadata.studyInstanceUid,
                    metadata.seriesInstanceUid,
                    metadata.sopInstanceUid);

            return metadata;

        } catch (IOException e) {
            log.error("Failed to extract DICOM metadata", e);
            throw e;
        }
    }

    /**
     * DICOM Date 형식을 LocalDate로 변환
     *
     * <p>DICOM Date 형식: YYYYMMDD (예: 19900101)
     *
     * @param dicomDate DICOM Date 문자열
     * @return LocalDate (파싱 실패 시 null)
     */
    private static LocalDate parseDicomDate(String dicomDate) {
        if (dicomDate == null || dicomDate.isEmpty()) {
            return null;
        }

        try {
            // DICOM 날짜 형식: YYYYMMDD
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMdd");
            return LocalDate.parse(dicomDate, formatter);
        } catch (DateTimeParseException e) {
            log.warn("Failed to parse DICOM date: {}", dicomDate);
            return null;
        }
    }

    /**
     * DICOM 메타데이터 DTO
     *
     * <p>추출된 DICOM 메타데이터를 담는 컨테이너 클래스
     */
    @Getter
    public static class DicomMetadata {
        // Patient Level
        private String patientId;
        private String issuerOfPatientId;
        private String patientName;
        private LocalDate patientBirthDate;
        private String patientSex;

        // Study Level
        private String studyInstanceUid;
        private LocalDate studyDate;
        private String studyDescription;

        // Series Level
        private String seriesInstanceUid;
        private Integer seriesNumber;
        private String modality;
        private String seriesDescription;
        private String bodyPartExamined;
        private String manufacturer;
        private String manufacturerModelName;

        // Instance Level
        private String sopInstanceUid;
        private String sopClassUid;
        private Integer instanceNumber;
        private Integer imageRows;
        private Integer imageColumns;
        private Integer numberOfFrames;
    }
}
