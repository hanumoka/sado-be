package com.hanumoka.sado.minipacs.domain.parser;

import com.hanumoka.sado.common.exception.BusinessException;
import com.hanumoka.sado.minipacs.code.MiniPacsErrorCode;
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
     * DICOM 날짜 파싱 패턴 (우선순위 순)
     * <p>재사용을 위해 static final로 선언하여 매번 배열 생성을 방지합니다.
     */
    private static final String[] DATE_PATTERNS = {
        "yyyyMMdd",      // DICOM 표준: 19900315
        "yyyy.MM.dd",    // 비표준 (점): 1990.03.15
        "yyyy-MM-dd",    // 비표준 (하이픈): 1990-03-15
        "yyyy/MM/dd"     // 비표준 (슬래시): 1990/03/15
    };

    /**
     * DICOM 파일에서 메타데이터 추출
     *
     * <p><strong>CRITICAL - 리소스 관리 책임</strong>:
     * 이 메서드는 inputStream의 소유권을 가져가지 않습니다.
     * 호출자는 반드시 inputStream을 닫아야 합니다.
     *
     * <p>예시:
     * <pre>{@code
     * try (InputStream is = new FileInputStream(file)) {
     *     DicomMetadata metadata = DicomMetadataExtractor.extract(is);
     *     // is는 try-with-resources로 자동 닫힘
     * }
     * }</pre>
     *
     * @apiNote 호출자는 반드시 try-with-resources 또는 finally 블록에서
     *          inputStream을 닫아야 합니다. 그렇지 않으면 파일 디스크립터가 누수됩니다.
     * @param inputStream DICOM 파일 InputStream (호출자가 닫아야 함)
     * @return 추출된 DICOM 메타데이터
     * @throws IOException DICOM 파일 파싱 실패 시
     */
    public static DicomMetadata extract(InputStream inputStream) throws IOException {
        try (DicomInputStream dis = new DicomInputStream(inputStream)) {
            // DICOM 파일 파싱
            Attributes attributes = dis.readDataset();

            // 메타데이터 추출
            DicomMetadata metadata = DicomMetadata.builder()
                    // Patient Level (0010,xxxx)
                    .patientId(attributes.getString(Tag.PatientID))
                    .issuerOfPatientId(getStringOrEmpty(attributes, Tag.IssuerOfPatientID))
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

            // 필수 DICOM 태그 검증
            validateRequiredTags(metadata);

            return metadata;
        }
    }

    /**
     * DICOM 태그에서 문자열 값을 가져오되, null이면 빈 문자열 반환
     * <p>
     * MySQL의 unique constraint는 NULL 값에 대해 중복을 허용하므로,
     * issuerOfPatientId가 NULL일 때 빈 문자열을 반환하여 unique constraint가 작동하도록 합니다.
     *
     * @param attributes DICOM attributes
     * @param tag 추출할 태그
     * @return 태그 값 또는 빈 문자열 (never null)
     */
    private static String getStringOrEmpty(Attributes attributes, int tag) {
        String value = attributes.getString(tag);
        return value != null ? value : "";
    }

    /**
     * DICOM 날짜 형식 (YYYYMMDD) → LocalDate 변환
     * <p>
     * 표준 DICOM 형식(YYYYMMDD)과 비표준 형식(yyyy.MM.dd, yyyy-MM-dd)을 모두 지원합니다.
     *
     * @param dicomDate DICOM 날짜 문자열 (예: "19900315", "1990.03.15", "1990-03-15")
     * @return LocalDate 객체 (파싱 실패 시 null)
     */
    private static LocalDate parseDate(String dicomDate) {
        if (dicomDate == null || dicomDate.isEmpty()) {
            return null;
        }

        // static final DATE_PATTERNS 사용 (매번 배열 생성 방지)
        // Note: yyyyMM, yyyy 패턴은 LocalDate.parse()로 파싱 불가 (YearMonth, Year 필요)
        for (String pattern : DATE_PATTERNS) {
            try {
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern(pattern);
                LocalDate parsedDate = LocalDate.parse(dicomDate, formatter);

                // 성공 시 로그 (DEBUG 레벨)
                log.debug("Successfully parsed DICOM date: {} with pattern: {}",
                          dicomDate, pattern);
                return parsedDate;

            } catch (Exception e) {
                // 다음 패턴 시도 (로그 없음 - 정상 흐름)
                continue;
            }
        }

        // 모든 패턴 실패 시
        log.warn("Failed to parse DICOM date with all patterns: {}", dicomDate);
        return null;
    }

    /**
     * 필수 DICOM 태그 검증
     * <p>
     * DICOM Storage 필수 태그 (Type 1):
     * - StudyInstanceUID (0020,000D)
     * - SeriesInstanceUID (0020,000E)
     * - SOPInstanceUID (0008,0018)
     * <p>
     * PatientID (0010,0020)는 연구용 DICOM에서 누락 가능하므로 검증하지 않음
     *
     * @param metadata 검증할 DICOM 메타데이터
     * @throws BusinessException 필수 태그 누락 시
     */
    private static void validateRequiredTags(DicomMetadata metadata) {
        StringBuilder missingTags = new StringBuilder();

        // StudyInstanceUID (0020,000D) - Type 1
        if (isEmpty(metadata.getStudyInstanceUid())) {
            missingTags.append("StudyInstanceUID (0020,000D), ");
        }

        // SeriesInstanceUID (0020,000E) - Type 1
        if (isEmpty(metadata.getSeriesInstanceUid())) {
            missingTags.append("SeriesInstanceUID (0020,000E), ");
        }

        // SOPInstanceUID (0008,0018) - Type 1
        if (isEmpty(metadata.getSopInstanceUid())) {
            missingTags.append("SOPInstanceUID (0008,0018), ");
        }

        // Modality (0008,0060) - Type 1
        if (isEmpty(metadata.getModality())) {
            missingTags.append("Modality (0008,0060), ");
        }

        if (missingTags.length() > 0) {
            // 마지막 ", " 제거
            missingTags.setLength(missingTags.length() - 2);
            String errorMessage = "Required DICOM tags are missing: " + missingTags;
            log.error(errorMessage);
            throw new BusinessException(MiniPacsErrorCode.DICOM_MISSING_REQUIRED_TAG, errorMessage);
        }
    }

    /**
     * 문자열이 null이거나 빈 문자열인지 검사
     *
     * @param str 검사할 문자열
     * @return null이거나 빈 문자열이면 true
     */
    private static boolean isEmpty(String str) {
        return str == null || str.trim().isEmpty();
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
