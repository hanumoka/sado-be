package com.hanumoka.sado.minipacs.controller;

import com.hanumoka.sado.common.exception.BusinessException;
import com.hanumoka.sado.minipacs.code.MiniPacsErrorCode;
import com.hanumoka.sado.minipacs.domain.entity.Instance;
import com.hanumoka.sado.minipacs.domain.entity.Series;
import com.hanumoka.sado.minipacs.domain.entity.Study;
import com.hanumoka.sado.minipacs.domain.service.DicomRenderingService;
import com.hanumoka.sado.minipacs.domain.service.InstanceService;
import com.hanumoka.sado.minipacs.domain.service.SeriesService;
import com.hanumoka.sado.minipacs.domain.service.StudyService;
import com.hanumoka.sado.minipacs.infrastructure.config.UploadLimiter;
import com.hanumoka.sado.minipacs.storage.service.DicomStorageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.validation.annotation.Validated;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.time.Duration;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Duration;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * DICOMweb REST API Controller
 *
 * <p>DICOMweb 표준 API를 구현합니다 (OHIF Viewer 호환).
 *
 * <p>지원 서비스:
 * <ul>
 *   <li>QIDO-RS: Query based on ID for DICOM Objects</li>
 *   <li>WADO-RS: Web Access to DICOM Objects</li>
 *   <li>STOW-RS: Store Over the Web (기존 InstanceController.uploadDicom 사용)</li>
 * </ul>
 *
 * <p>엔드포인트:
 * <ul>
 *   <li>GET /dicomweb/studies - Study 검색 (QIDO-RS)</li>
 *   <li>GET /dicomweb/studies/{studyUID}/series - Series 검색 (QIDO-RS)</li>
 *   <li>GET /dicomweb/studies/{studyUID}/series/{seriesUID}/instances - Instance 검색 (QIDO-RS)</li>
 *   <li>GET /dicomweb/studies/{studyUID}/metadata - Study 메타데이터 (WADO-RS)</li>
 *   <li>GET /dicomweb/studies/{studyUID}/series/{seriesUID}/instances/{sopInstanceUID} - Instance 다운로드 (WADO-RS)</li>
 *   <li>GET /dicomweb/studies/{studyUID}/series/{seriesUID}/instances/{sopInstanceUID}/frames/{frameNumber} - 프레임 조회 (WADO-RS)</li>
 * </ul>
 *
 * @see <a href="https://www.dicomstandard.org/using/dicomweb">DICOMweb Standard</a>
 */
@RestController
@RequestMapping("/dicomweb")
@RequiredArgsConstructor
@Slf4j
@Validated
@Tag(name = "DICOMweb", description = "DICOMweb 표준 API (QIDO-RS, WADO-RS)")
public class DicomWebController {

    private static final String DICOM_JSON_MEDIA_TYPE = "application/dicom+json";
    private static final String DICOM_MEDIA_TYPE = "application/dicom";

    private final StudyService studyService;
    private final SeriesService seriesService;
    private final InstanceService instanceService;
    private final DicomStorageService dicomStorageService;
    private final DicomRenderingService dicomRenderingService;
    private final UploadLimiter uploadLimiter;

    // ========== QIDO-RS: Query Services ==========

    /**
     * QIDO-RS: Study 검색
     *
     * <p>지원 쿼리 파라미터:
     * <ul>
     *   <li>PatientID - 환자 ID</li>
     *   <li>PatientName - 환자 이름 (와일드카드 지원)</li>
     *   <li>StudyDate - 검사 날짜 (YYYYMMDD 또는 범위)</li>
     *   <li>StudyInstanceUID - Study Instance UID</li>
     *   <li>limit - 결과 개수 제한</li>
     *   <li>offset - 결과 오프셋</li>
     * </ul>
     *
     * @return Study 메타데이터 목록 (DICOM JSON)
     */
    @GetMapping(value = "/studies", produces = DICOM_JSON_MEDIA_TYPE)
    @Operation(summary = "QIDO-RS: Study 검색", description = "Study를 검색합니다.")
    public ResponseEntity<List<Map<String, Object>>> searchStudies(
            @Parameter(description = "환자 ID") @RequestParam(required = false) String PatientID,
            @Parameter(description = "환자 이름") @RequestParam(required = false) String PatientName,
            @Parameter(description = "검사 날짜 (YYYYMMDD)") @RequestParam(required = false) String StudyDate,
            @Parameter(description = "Study Instance UID") @RequestParam(required = false) String StudyInstanceUID,
            @Parameter(description = "결과 개수 제한") @RequestParam(defaultValue = "100") int limit,
            @Parameter(description = "결과 오프셋") @RequestParam(defaultValue = "0") int offset
    ) {
        log.info("QIDO-RS: Search studies - PatientID={}, PatientName={}, StudyDate={}, StudyInstanceUID={}",
                PatientID, PatientName, StudyDate, StudyInstanceUID);

        // Study 조회
        List<Study> studies;

        if (StudyInstanceUID != null && !StudyInstanceUID.isEmpty()) {
            // StudyInstanceUID로 직접 검색
            studies = studyService.findByStudyInstanceUid(StudyInstanceUID)
                    .map(List::of)
                    .orElse(List.of());
        } else {
            // CRITICAL: OOM 방지 - DB 쿼리로 필터링 (findAll() + Stream 제거)
            // StudyDate 파싱 (YYYYMMDD → LocalDate)
            java.time.LocalDate studyDateFilter = null;
            if (StudyDate != null && !StudyDate.isEmpty()) {
                try {
                    studyDateFilter = java.time.LocalDate.parse(StudyDate,
                            java.time.format.DateTimeFormatter.BASIC_ISO_DATE);
                } catch (java.time.format.DateTimeParseException e) {
                    log.warn("Invalid StudyDate format: {}, expected YYYYMMDD", StudyDate);
                }
            }

            // DB 쿼리로 필터링 (인덱스 활용 + JOIN FETCH)
            studies = studyService.findByDicomWebFilters(
                    PatientID,
                    PatientName,
                    studyDateFilter
            );
        }

        // 페이징 적용
        int fromIndex = Math.min(offset, studies.size());
        int toIndex = Math.min(offset + limit, studies.size());
        studies = studies.subList(fromIndex, toIndex);

        // DICOM JSON 형식으로 변환
        List<Map<String, Object>> result = studies.stream()
                .map(this::studyToDicomJson)
                .toList();

        log.info("QIDO-RS: Found {} studies", result.size());

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(DICOM_JSON_MEDIA_TYPE))
                .body(result);
    }

    /**
     * QIDO-RS: Series 검색
     *
     * @param studyUID Study Instance UID
     * @return Series 메타데이터 목록 (DICOM JSON)
     */
    @GetMapping(value = "/studies/{studyUID}/series", produces = DICOM_JSON_MEDIA_TYPE)
    @Operation(summary = "QIDO-RS: Series 검색", description = "특정 Study의 Series를 검색합니다.")
    public ResponseEntity<List<Map<String, Object>>> searchSeries(
            @Parameter(description = "Study Instance UID") @PathVariable String studyUID,
            @Parameter(description = "Modality") @RequestParam(required = false) String Modality,
            @Parameter(description = "Series Instance UID") @RequestParam(required = false) String SeriesInstanceUID
    ) {
        log.info("QIDO-RS: Search series - studyUID={}, Modality={}, SeriesInstanceUID={}",
                studyUID, Modality, SeriesInstanceUID);

        // Study 조회
        Study study = studyService.findByStudyInstanceUid(studyUID)
                .orElse(null);

        if (study == null) {
            return ResponseEntity.notFound().build();
        }

        // Series 조회
        List<Series> seriesList = seriesService.findByStudyId(study.getId());

        // 필터링
        if (SeriesInstanceUID != null && !SeriesInstanceUID.isEmpty()) {
            seriesList = seriesList.stream()
                    .filter(s -> SeriesInstanceUID.equals(s.getSeriesInstanceUid()))
                    .toList();
        }

        if (Modality != null && !Modality.isEmpty()) {
            seriesList = seriesList.stream()
                    .filter(s -> Modality.equals(s.getModality()))
                    .toList();
        }

        // DICOM JSON 형식으로 변환
        List<Map<String, Object>> result = seriesList.stream()
                .map(s -> seriesToDicomJson(s, studyUID))
                .toList();

        log.info("QIDO-RS: Found {} series", result.size());

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(DICOM_JSON_MEDIA_TYPE))
                .body(result);
    }

    /**
     * QIDO-RS: Instance 검색
     *
     * @param studyUID Study Instance UID
     * @param seriesUID Series Instance UID
     * @return Instance 메타데이터 목록 (DICOM JSON)
     */
    @GetMapping(value = "/studies/{studyUID}/series/{seriesUID}/instances", produces = DICOM_JSON_MEDIA_TYPE)
    @Operation(summary = "QIDO-RS: Instance 검색", description = "특정 Series의 Instance를 검색합니다.")
    public ResponseEntity<List<Map<String, Object>>> searchInstances(
            @Parameter(description = "Study Instance UID") @PathVariable String studyUID,
            @Parameter(description = "Series Instance UID") @PathVariable String seriesUID,
            @Parameter(description = "SOP Instance UID") @RequestParam(required = false) String SOPInstanceUID
    ) {
        log.info("QIDO-RS: Search instances - studyUID={}, seriesUID={}, SOPInstanceUID={}",
                studyUID, seriesUID, SOPInstanceUID);

        // Series 조회
        Series series = seriesService.findBySeriesInstanceUid(seriesUID)
                .orElse(null);

        if (series == null) {
            return ResponseEntity.notFound().build();
        }

        // Study UID 검증
        if (!studyUID.equals(series.getStudy().getStudyInstanceUid())) {
            return ResponseEntity.notFound().build();
        }

        // Instance 조회
        List<Instance> instances = instanceService.findBySeriesId(series.getId());

        // 필터링
        if (SOPInstanceUID != null && !SOPInstanceUID.isEmpty()) {
            instances = instances.stream()
                    .filter(i -> SOPInstanceUID.equals(i.getSopInstanceUid()))
                    .toList();
        }

        // DICOM JSON 형식으로 변환
        List<Map<String, Object>> result = instances.stream()
                .map(i -> instanceToDicomJson(i, studyUID, seriesUID))
                .toList();

        log.info("QIDO-RS: Found {} instances", result.size());

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(DICOM_JSON_MEDIA_TYPE))
                .body(result);
    }

    // ========== WADO-RS: Retrieve Services ==========

    /**
     * WADO-RS: Study 메타데이터 조회
     *
     * @param studyUID Study Instance UID
     * @return Study 메타데이터 (DICOM JSON)
     */
    @GetMapping(value = "/studies/{studyUID}/metadata", produces = DICOM_JSON_MEDIA_TYPE)
    @Operation(summary = "WADO-RS: Study 메타데이터", description = "Study의 전체 메타데이터를 조회합니다.")
    public ResponseEntity<List<Map<String, Object>>> getStudyMetadata(
            @Parameter(description = "Study Instance UID") @PathVariable String studyUID
    ) {
        log.info("WADO-RS: Get study metadata - studyUID={}", studyUID);

        Study study = studyService.findByStudyInstanceUid(studyUID)
                .orElse(null);

        if (study == null) {
            return ResponseEntity.notFound().build();
        }

        // Study의 모든 Instance 메타데이터 조회
        List<Series> seriesList = seriesService.findByStudyId(study.getId());
        List<Map<String, Object>> result = new ArrayList<>();

        for (Series series : seriesList) {
            List<Instance> instances = instanceService.findBySeriesId(series.getId());
            for (Instance instance : instances) {
                result.add(instanceToDicomJson(instance, studyUID, series.getSeriesInstanceUid()));
            }
        }

        log.info("WADO-RS: Returning {} instance metadata for study", result.size());

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(DICOM_JSON_MEDIA_TYPE))
                .body(result);
    }

    /**
     * WADO-RS: Series 메타데이터 조회
     *
     * @param studyUID Study Instance UID
     * @param seriesUID Series Instance UID
     * @return Series 메타데이터 (DICOM JSON)
     */
    @GetMapping(value = "/studies/{studyUID}/series/{seriesUID}/metadata", produces = DICOM_JSON_MEDIA_TYPE)
    @Operation(summary = "WADO-RS: Series 메타데이터", description = "Series의 전체 메타데이터를 조회합니다.")
    public ResponseEntity<List<Map<String, Object>>> getSeriesMetadata(
            @Parameter(description = "Study Instance UID") @PathVariable String studyUID,
            @Parameter(description = "Series Instance UID") @PathVariable String seriesUID
    ) {
        log.info("WADO-RS: Get series metadata - studyUID={}, seriesUID={}", studyUID, seriesUID);

        Series series = seriesService.findBySeriesInstanceUid(seriesUID)
                .orElse(null);

        if (series == null || !studyUID.equals(series.getStudy().getStudyInstanceUid())) {
            return ResponseEntity.notFound().build();
        }

        List<Instance> instances = instanceService.findBySeriesId(series.getId());
        List<Map<String, Object>> result = instances.stream()
                .map(i -> instanceToDicomJson(i, studyUID, seriesUID))
                .toList();

        log.info("WADO-RS: Returning {} instance metadata for series", result.size());

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(DICOM_JSON_MEDIA_TYPE))
                .body(result);
    }

    /**
     * WADO-RS: Instance 다운로드 (DICOM 파일)
     *
     * @param studyUID Study Instance UID
     * @param seriesUID Series Instance UID
     * @param sopInstanceUID SOP Instance UID
     * @return DICOM 파일 스트림
     */
    @GetMapping(value = "/studies/{studyUID}/series/{seriesUID}/instances/{sopInstanceUID}",
            produces = DICOM_MEDIA_TYPE)
    @Operation(summary = "WADO-RS: Instance 다운로드", description = "DICOM 파일을 다운로드합니다.")
    public ResponseEntity<Resource> retrieveInstance(
            @Parameter(description = "Study Instance UID") @PathVariable String studyUID,
            @Parameter(description = "Series Instance UID") @PathVariable String seriesUID,
            @Parameter(description = "SOP Instance UID") @PathVariable String sopInstanceUID
    ) {
        log.info("WADO-RS: Retrieve instance - studyUID={}, seriesUID={}, sopInstanceUID={}",
                studyUID, seriesUID, sopInstanceUID);

        Instance instance = instanceService.findBySopInstanceUid(sopInstanceUID)
                .orElse(null);

        if (instance == null) {
            return ResponseEntity.notFound().build();
        }

        // UID 검증
        Series series = instance.getSeries();
        if (!seriesUID.equals(series.getSeriesInstanceUid()) ||
                !studyUID.equals(series.getStudy().getStudyInstanceUid())) {
            return ResponseEntity.notFound().build();
        }

        // CRITICAL: Resource 반환으로 리소스 누수 방지
        // Spring Framework가 Resource.close()를 자동 호출
        Resource resource = dicomStorageService.downloadDicomFileAsResource(instance.getStoragePath());

        log.info("WADO-RS: Returning instance file as Resource");

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(DICOM_MEDIA_TYPE))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + sopInstanceUID + ".dcm\"")
                .body(resource);
    }

    /**
     * WADO-RS: Instance 메타데이터 조회
     *
     * @param studyUID Study Instance UID
     * @param seriesUID Series Instance UID
     * @param sopInstanceUID SOP Instance UID
     * @return Instance 메타데이터 (DICOM JSON)
     */
    @GetMapping(value = "/studies/{studyUID}/series/{seriesUID}/instances/{sopInstanceUID}/metadata",
            produces = DICOM_JSON_MEDIA_TYPE)
    @Operation(summary = "WADO-RS: Instance 메타데이터", description = "Instance의 메타데이터를 조회합니다.")
    public ResponseEntity<List<Map<String, Object>>> getInstanceMetadata(
            @Parameter(description = "Study Instance UID") @PathVariable String studyUID,
            @Parameter(description = "Series Instance UID") @PathVariable String seriesUID,
            @Parameter(description = "SOP Instance UID") @PathVariable String sopInstanceUID
    ) {
        log.info("WADO-RS: Get instance metadata - studyUID={}, seriesUID={}, sopInstanceUID={}",
                studyUID, seriesUID, sopInstanceUID);

        Instance instance = instanceService.findBySopInstanceUid(sopInstanceUID)
                .orElse(null);

        if (instance == null) {
            return ResponseEntity.notFound().build();
        }

        // UID 검증
        Series series = instance.getSeries();
        if (!seriesUID.equals(series.getSeriesInstanceUid()) ||
                !studyUID.equals(series.getStudy().getStudyInstanceUid())) {
            return ResponseEntity.notFound().build();
        }

        Map<String, Object> result = instanceToDicomJson(instance, studyUID, seriesUID);

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(DICOM_JSON_MEDIA_TYPE))
                .body(List.of(result));
    }

    /**
     * WADO-URI: 레거시 WADO URL 지원
     *
     * <p>OHIF Viewer 호환성을 위해 WADO-URI도 지원합니다.
     *
     * @param requestType WADO 요청 타입 (WADO)
     * @param studyUID Study Instance UID
     * @param seriesUID Series Instance UID
     * @param objectUID SOP Instance UID
     * @return DICOM 파일 스트림
     */
    @GetMapping(value = "/wado", produces = DICOM_MEDIA_TYPE)
    @Operation(summary = "WADO-URI: 레거시 WADO 지원", description = "레거시 WADO-URI 형식을 지원합니다.")
    public ResponseEntity<Resource> wadoUri(
            @RequestParam String requestType,
            @RequestParam @Pattern(regexp = "^[0-9.]+$", message = "Invalid DICOM UID format") String studyUID,
            @RequestParam @Pattern(regexp = "^[0-9.]+$", message = "Invalid DICOM UID format") String seriesUID,
            @RequestParam @Pattern(regexp = "^[0-9.]+$", message = "Invalid DICOM UID format") String objectUID
    ) {
        if (!"WADO".equals(requestType)) {
            return ResponseEntity.badRequest().build();
        }

        log.info("WADO-URI: Request - studyUID={}, seriesUID={}, objectUID={}",
                studyUID, seriesUID, objectUID);

        return retrieveInstance(studyUID, seriesUID, objectUID);
    }

    // ========== WADO-RS: Rendered Services ==========

    /**
     * WADO-RS: Instance Rendered (싱글프레임 또는 첫 프레임)
     *
     * <p>DICOM 파일을 PNG 이미지로 렌더링하여 반환합니다.
     * 멀티프레임 DICOM의 경우 첫 번째 프레임을 반환합니다.
     *
     * @param studyUID Study Instance UID
     * @param seriesUID Series Instance UID
     * @param sopInstanceUID SOP Instance UID
     * @return PNG 이미지
     */
    @GetMapping(value = "/studies/{studyUID}/series/{seriesUID}/instances/{sopInstanceUID}/rendered",
            produces = MediaType.IMAGE_PNG_VALUE)
    @Operation(summary = "WADO-RS: Instance Rendered", description = "DICOM 파일을 PNG 이미지로 렌더링합니다.")
    public ResponseEntity<byte[]> renderInstance(
            @Parameter(description = "Study Instance UID") @PathVariable String studyUID,
            @Parameter(description = "Series Instance UID") @PathVariable String seriesUID,
            @Parameter(description = "SOP Instance UID") @PathVariable String sopInstanceUID
    ) {
        log.info("WADO-RS Rendered: instance - sopInstanceUID={}", sopInstanceUID);

        Instance instance = instanceService.findBySopInstanceUid(sopInstanceUID)
                .orElseThrow(() -> new BusinessException(MiniPacsErrorCode.INSTANCE_NOT_FOUND));

        // UID 검증
        validateInstanceUIDs(instance, studyUID, seriesUID);

        // 렌더링 (첫 프레임)
        byte[] pngBytes = dicomRenderingService.renderToPng(instance.getStoragePath(), 1);

        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_PNG)
                .cacheControl(CacheControl.maxAge(Duration.ofHours(1)))
                .body(pngBytes);
    }

    /**
     * WADO-RS: Frame Rendered (멀티프레임)
     *
     * <p>DICOM 파일의 특정 프레임을 PNG 이미지로 렌더링하여 반환합니다.
     *
     * @param studyUID Study Instance UID
     * @param seriesUID Series Instance UID
     * @param sopInstanceUID SOP Instance UID
     * @param frameNumber 프레임 번호 (1부터 시작)
     * @return PNG 이미지
     */
    @GetMapping(value = "/studies/{studyUID}/series/{seriesUID}/instances/{sopInstanceUID}/frames/{frameNumber}/rendered",
            produces = MediaType.IMAGE_PNG_VALUE)
    @Operation(summary = "WADO-RS: Frame Rendered", description = "DICOM 멀티프레임의 특정 프레임을 PNG 이미지로 렌더링합니다.")
    public ResponseEntity<byte[]> renderFrame(
            @Parameter(description = "Study Instance UID") @PathVariable String studyUID,
            @Parameter(description = "Series Instance UID") @PathVariable String seriesUID,
            @Parameter(description = "SOP Instance UID") @PathVariable String sopInstanceUID,
            @Parameter(description = "프레임 번호 (1부터 시작)") @PathVariable int frameNumber
    ) {
        log.info("WADO-RS Rendered: frame - sopInstanceUID={}, frame={}", sopInstanceUID, frameNumber);

        Instance instance = instanceService.findBySopInstanceUid(sopInstanceUID)
                .orElseThrow(() -> new BusinessException(MiniPacsErrorCode.INSTANCE_NOT_FOUND));

        // UID 검증
        validateInstanceUIDs(instance, studyUID, seriesUID);

        // 프레임 번호 검증
        int totalFrames = instance.getNumberOfFrames() != null ? instance.getNumberOfFrames() : 1;
        if (frameNumber < 1 || frameNumber > totalFrames) {
            throw new BusinessException(MiniPacsErrorCode.INVALID_FRAME_NUMBER,
                    "Frame " + frameNumber + " out of range (1-" + totalFrames + ")");
        }

        // 렌더링
        byte[] pngBytes = dicomRenderingService.renderToPng(instance.getStoragePath(), frameNumber);

        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_PNG)
                .cacheControl(CacheControl.maxAge(Duration.ofHours(1)))
                .body(pngBytes);
    }

    /**
     * WADO-RS: Frame BulkData (Raw Pixel Data)
     *
     * <p>DICOM 파일의 특정 프레임을 raw 픽셀 데이터로 반환합니다.
     * Cornerstone3D의 wadors: scheme에서 사용됩니다.
     *
     * <p>응답 형식:
     * <ul>
     *   <li>Content-Type: application/octet-stream</li>
     *   <li>Body: DICOM 파일 전체 (Cornerstone이 프레임 추출)</li>
     * </ul>
     *
     * @param studyUID Study Instance UID
     * @param seriesUID Series Instance UID
     * @param sopInstanceUID SOP Instance UID
     * @param frameNumber 프레임 번호 (1부터 시작)
     * @return DICOM 파일 바이트
     */
    @GetMapping(value = "/studies/{studyUID}/series/{seriesUID}/instances/{sopInstanceUID}/frames/{frameNumber}",
            produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    @Operation(summary = "WADO-RS: Frame BulkData", description = "DICOM 멀티프레임의 특정 프레임을 raw 픽셀 데이터로 반환합니다 (Cornerstone3D wadors: 지원).")
    public ResponseEntity<byte[]> retrieveFrame(
            @Parameter(description = "Study Instance UID") @PathVariable String studyUID,
            @Parameter(description = "Series Instance UID") @PathVariable String seriesUID,
            @Parameter(description = "SOP Instance UID") @PathVariable String sopInstanceUID,
            @Parameter(description = "프레임 번호 (1부터 시작)") @PathVariable int frameNumber
    ) {
        log.info("WADO-RS BulkData: frame - sopInstanceUID={}, frame={}", sopInstanceUID, frameNumber);

        Instance instance = instanceService.findBySopInstanceUid(sopInstanceUID)
                .orElseThrow(() -> new BusinessException(MiniPacsErrorCode.INSTANCE_NOT_FOUND));

        // UID 검증
        validateInstanceUIDs(instance, studyUID, seriesUID);

        // 프레임 번호 검증
        int totalFrames = instance.getNumberOfFrames() != null ? instance.getNumberOfFrames() : 1;
        if (frameNumber < 1 || frameNumber > totalFrames) {
            throw new BusinessException(MiniPacsErrorCode.INVALID_FRAME_NUMBER,
                    "Frame " + frameNumber + " out of range (1-" + totalFrames + ")");
        }

        // DICOM 파일 바이트 조회 (Cornerstone이 프레임 추출 수행)
        byte[] dicomBytes = dicomRenderingService.getDicomFileBytes(instance.getStoragePath());

        // Transfer Syntax 정보를 헤더로 전달 (클라이언트 힌트)
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        headers.setCacheControl(CacheControl.maxAge(Duration.ofHours(1)));

        // DICOM 메타데이터 헤더 추가 (클라이언트 디코딩 힌트)
        if (instance.getTransferSyntaxUid() != null) {
            headers.set("X-DICOM-TransferSyntax", instance.getTransferSyntaxUid());
        }
        if (instance.getPhotometricInterpretation() != null) {
            headers.set("X-DICOM-PhotometricInterpretation", instance.getPhotometricInterpretation());
        }
        if (instance.getBitsAllocated() != null) {
            headers.set("X-DICOM-BitsAllocated", String.valueOf(instance.getBitsAllocated()));
        }
        if (instance.getBitsStored() != null) {
            headers.set("X-DICOM-BitsStored", String.valueOf(instance.getBitsStored()));
        }

        return new ResponseEntity<>(dicomBytes, headers, HttpStatus.OK);
    }

    /**
     * UID 검증 헬퍼 메서드
     */
    private void validateInstanceUIDs(Instance instance, String studyUID, String seriesUID) {
        Series series = instance.getSeries();
        if (!seriesUID.equals(series.getSeriesInstanceUid()) ||
                !studyUID.equals(series.getStudy().getStudyInstanceUid())) {
            throw new BusinessException(MiniPacsErrorCode.INSTANCE_NOT_FOUND,
                    "Instance UID hierarchy mismatch");
        }
    }

    // ========== Helper Methods: DICOM JSON 변환 ==========

    /**
     * Study → DICOM JSON 변환
     */
    private Map<String, Object> studyToDicomJson(Study study) {
        Map<String, Object> json = new LinkedHashMap<>();

        // Study Instance UID (0020,000D)
        json.put("0020000D", createDicomValue("UI", study.getStudyInstanceUid()));

        // Study Date (0008,0020)
        if (study.getStudyDate() != null) {
            json.put("00080020", createDicomValue("DA",
                    study.getStudyDate().format(DateTimeFormatter.BASIC_ISO_DATE)));
        }

        // Study Description (0008,1030)
        if (study.getStudyDescription() != null) {
            json.put("00081030", createDicomValue("LO", study.getStudyDescription()));
        }

        // Patient 정보
        if (study.getPatient() != null) {
            // Patient ID (0010,0020)
            json.put("00100020", createDicomValue("LO", study.getPatient().getDicomPatientId()));

            // Patient Name (0010,0010)
            if (study.getPatient().getPatientName() != null) {
                json.put("00100010", createDicomValue("PN", study.getPatient().getPatientName()));
            }
        }

        // Number of Series (0020,1206)
        json.put("00201206", createDicomValue("IS", String.valueOf(study.getNumberOfSeries())));

        // Number of Instances (0020,1208)
        json.put("00201208", createDicomValue("IS", String.valueOf(study.getNumberOfInstances())));

        return json;
    }

    /**
     * Series → DICOM JSON 변환
     */
    private Map<String, Object> seriesToDicomJson(Series series, String studyUID) {
        Map<String, Object> json = new LinkedHashMap<>();

        // Study Instance UID (0020,000D)
        json.put("0020000D", createDicomValue("UI", studyUID));

        // Series Instance UID (0020,000E)
        json.put("0020000E", createDicomValue("UI", series.getSeriesInstanceUid()));

        // Series Number (0020,0011)
        if (series.getSeriesNumber() != null) {
            json.put("00200011", createDicomValue("IS", String.valueOf(series.getSeriesNumber())));
        }

        // Modality (0008,0060)
        if (series.getModality() != null) {
            json.put("00080060", createDicomValue("CS", series.getModality()));
        }

        // Series Description (0008,103E)
        if (series.getSeriesDescription() != null) {
            json.put("0008103E", createDicomValue("LO", series.getSeriesDescription()));
        }

        // Number of Instances (0020,1209)
        json.put("00201209", createDicomValue("IS", String.valueOf(series.getNumberOfInstances())));

        return json;
    }

    /**
     * Instance → DICOM JSON 변환
     */
    private Map<String, Object> instanceToDicomJson(Instance instance, String studyUID, String seriesUID) {
        Map<String, Object> json = new LinkedHashMap<>();

        // Study Instance UID (0020,000D)
        json.put("0020000D", createDicomValue("UI", studyUID));

        // Series Instance UID (0020,000E)
        json.put("0020000E", createDicomValue("UI", seriesUID));

        // SOP Instance UID (0008,0018)
        json.put("00080018", createDicomValue("UI", instance.getSopInstanceUid()));

        // SOP Class UID (0008,0016)
        if (instance.getSopClassUid() != null) {
            json.put("00080016", createDicomValue("UI", instance.getSopClassUid()));
        }

        // Instance Number (0020,0013)
        if (instance.getInstanceNumber() != null) {
            json.put("00200013", createDicomValue("IS", String.valueOf(instance.getInstanceNumber())));
        }

        // Rows (0028,0010)
        if (instance.getImageRows() != null) {
            json.put("00280010", createDicomValue("US", instance.getImageRows()));
        }

        // Columns (0028,0011)
        if (instance.getImageColumns() != null) {
            json.put("00280011", createDicomValue("US", instance.getImageColumns()));
        }

        // Number of Frames (0028,0008)
        if (instance.getNumberOfFrames() != null) {
            json.put("00280008", createDicomValue("IS", String.valueOf(instance.getNumberOfFrames())));
        }

        return json;
    }

    /**
     * DICOM JSON Value 객체 생성
     */
    private Map<String, Object> createDicomValue(String vr, Object value) {
        Map<String, Object> valueMap = new LinkedHashMap<>();
        valueMap.put("vr", vr);
        if (value != null) {
            valueMap.put("Value", List.of(value));
        }
        return valueMap;
    }

    // ========== STOW-RS: Store Services ==========

    private static final long MAX_FILE_SIZE = 500L * 1024 * 1024; // 500MB

    /**
     * STOW-RS: DICOM 파일 업로드
     *
     * <p>DICOMweb 표준 STOW-RS(Store Over the Web) 엔드포인트입니다.
     *
     * <p>지원 Content-Type:
     * <ul>
     *   <li>multipart/related (DICOMweb 표준)</li>
     *   <li>multipart/form-data (브라우저 호환성)</li>
     * </ul>
     *
     * <p>응답 형식:
     * <ul>
     *   <li>HTTP 200 OK: 업로드 성공</li>
     *   <li>HTTP 409 Conflict: 중복 파일</li>
     *   <li>HTTP 413 Payload Too Large: 파일 크기 초과</li>
     *   <li>HTTP 415 Unsupported Media Type: DICOM 형식 아님</li>
     *   <li>HTTP 422 Unprocessable Entity: 유효하지 않은 DICOM</li>
     *   <li>HTTP 429 Too Many Requests: 동시 업로드 제한 초과</li>
     * </ul>
     *
     * <p>동시 업로드 제한 (2026-01-05 추가):
     * <ul>
     *   <li>Semaphore 기반 동시성 제어 (기본: 5개)</li>
     *   <li>타임아웃 초과 시 429 Too Many Requests + Retry-After 헤더</li>
     * </ul>
     *
     * @param file DICOM 파일 (MultipartFile)
     * @return DICOMweb 표준 응답 (application/dicom+json)
     */
    @PostMapping(value = "/studies",
            consumes = {MediaType.MULTIPART_FORM_DATA_VALUE, "multipart/related"})
    @Operation(summary = "STOW-RS: DICOM 파일 업로드",
            description = "DICOMweb 표준 STOW-RS 업로드")
    public ResponseEntity<Map<String, Object>> storeInstance(
            @RequestParam("file") MultipartFile file
    ) throws IOException {
        log.info("STOW-RS: Upload request - filename: {}, size: {} bytes, availablePermits: {}",
                file.getOriginalFilename(), file.getSize(), uploadLimiter.availablePermits());

        // 1. 파일 검증 (Semaphore 획득 전에 빠른 실패)
        if (file.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                    .body(createErrorResponse("EMPTY_FILE", "파일이 비어있습니다"));
        }

        // 2. 파일 크기 제한 (500MB) - Semaphore 획득 전에 빠른 실패
        if (file.getSize() > MAX_FILE_SIZE) {
            return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                    .body(createErrorResponse("FILE_TOO_LARGE",
                            "파일 크기가 너무 큽니다 (최대: " + MAX_FILE_SIZE + " bytes)"));
        }

        // 3. 동시 업로드 제한 내에서 실행
        // - 타임아웃 초과 시 TooManyRequestsException → GlobalExceptionHandler에서 429 반환
        return uploadLimiter.executeWithLimit(() -> {
            try {
                // 4. Service 호출 (모든 비즈니스 로직은 Service에서 처리)
                byte[] fileBytes = file.getBytes();
                Instance instance = instanceService.uploadDicomFile(
                        fileBytes,
                        file.getOriginalFilename()
                );

                // 5. DICOMweb 표준 응답 생성
                Map<String, Object> response = createStowRsResponse(instance);

                log.info("STOW-RS: Upload success - sopInstanceUid: {}",
                        instance.getSopInstanceUid());

                // 6. HTTP 200 OK 반환
                return ResponseEntity.ok()
                        .contentType(MediaType.parseMediaType(DICOM_JSON_MEDIA_TYPE))
                        .body(response);

            } catch (IllegalArgumentException e) {
                // 409 Conflict (중복) 또는 422 Unprocessable Entity
                if (e.getMessage() != null && e.getMessage().contains("already exists")) {
                    log.warn("STOW-RS: Duplicate instance - {}", e.getMessage());
                    return ResponseEntity.status(HttpStatus.CONFLICT)
                            .body(createErrorResponse("DUPLICATE_INSTANCE",
                                    "이미 존재하는 파일입니다"));
                } else {
                    log.error("STOW-RS: Invalid argument", e);
                    return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                            .body(createErrorResponse("INVALID_ARGUMENT", e.getMessage()));
                }

            } catch (RuntimeException e) {
                // 415 Unsupported Media Type 또는 422 Unprocessable Entity
                log.error("STOW-RS: Runtime exception", e);

                if (e.getMessage() != null &&
                        (e.getMessage().contains("DICOM") ||
                                e.getMessage().contains("magic number"))) {
                    return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
                            .body(createErrorResponse("NOT_DICOM",
                                    "지원하지 않는 파일 형식입니다"));
                }

                return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                        .body(createErrorResponse("PROCESSING_FAILED", e.getMessage()));

            } catch (IOException e) {
                // 500 Internal Server Error
                log.error("STOW-RS: Upload failed", e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(createErrorResponse("UPLOAD_FAILED", "서버 오류가 발생했습니다"));
            }
        });
    }

    /**
     * Instance → DICOMweb STOW-RS 응답 변환
     *
     * <p>Frontend 호환 형식 (dicomWebService.ts StowRsResponse):
     * {
     *   studyInstanceUid: string,
     *   seriesInstanceUid: string,
     *   sopInstanceUid: string,
     *   success: boolean
     * }
     *
     * <p>향후 DICOMweb Part 18 표준 응답으로 전환 가능 (OHIF Viewer 통합 시)
     */
    private Map<String, Object> createStowRsResponse(Instance instance) {
        Map<String, Object> response = new LinkedHashMap<>();

        Series series = instance.getSeries();
        Study study = series.getStudy();

        // Frontend 호환 형식
        response.put("studyInstanceUid", study.getStudyInstanceUid());
        response.put("seriesInstanceUid", series.getSeriesInstanceUid());
        response.put("sopInstanceUid", instance.getSopInstanceUid());
        response.put("success", true);

        return response;
    }

    /**
     * 에러 응답 생성 (Frontend 호환)
     */
    private Map<String, Object> createErrorResponse(String errorCode, String errorMessage) {
        Map<String, Object> error = new LinkedHashMap<>();
        error.put("success", false);
        error.put("error", errorMessage);
        error.put("errorCode", errorCode);
        return error;
    }
}
