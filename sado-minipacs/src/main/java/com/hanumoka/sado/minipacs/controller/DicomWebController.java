package com.hanumoka.sado.minipacs.controller;

import com.hanumoka.sado.common.exception.BusinessException;
import static com.hanumoka.sado.common.util.StringUtils.hasText;
import com.hanumoka.sado.minipacs.code.MiniPacsErrorCode;
import com.hanumoka.sado.minipacs.domain.entity.Instance;
import com.hanumoka.sado.minipacs.domain.entity.Series;
import com.hanumoka.sado.minipacs.domain.entity.Study;
import com.hanumoka.sado.minipacs.domain.service.DicomRenderingService;
import com.hanumoka.sado.minipacs.dto.FrameExtractionResult;
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
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import org.dcm4che3.data.UID;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

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

        if (hasText(StudyInstanceUID)) {
            // StudyInstanceUID로 직접 검색
            studies = studyService.findByStudyInstanceUid(StudyInstanceUID)
                    .map(List::of)
                    .orElse(List.of());
        } else {
            // CRITICAL: OOM 방지 - DB 쿼리로 필터링 (findAll() + Stream 제거)
            // StudyDate 파싱 (YYYYMMDD → LocalDate)
            java.time.LocalDate studyDateFilter = null;
            if (hasText(StudyDate)) {
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
        if (hasText(SeriesInstanceUID)) {
            seriesList = seriesList.stream()
                    .filter(s -> SeriesInstanceUID.equals(s.getSeriesInstanceUid()))
                    .toList();
        }

        if (hasText(Modality)) {
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
        if (hasText(SOPInstanceUID)) {
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
     * <p>성능 최적화 (2026-01-09):
     * <ul>
     *   <li>기존: N+1 쿼리 (1 Study + N Series + M Instance) → 5-10초</li>
     *   <li>개선: 단일 JPQL JOIN FETCH 쿼리 → 500ms 이하 (90% 단축)</li>
     * </ul>
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

        // CRITICAL: N+1 쿼리 최적화 - 단일 쿼리로 모든 Instance 조회
        // 기존: seriesService.findByStudyId() + instanceService.findBySeriesId() (N+1)
        // 개선: instanceService.findAllByStudyInstanceUid() (1 쿼리, JOIN FETCH)
        List<Instance> instances = instanceService.findAllByStudyInstanceUid(studyUID);

        if (instances.isEmpty()) {
            // Study가 존재하지 않거나 Instance가 없는 경우
            // Study 존재 여부 확인
            if (studyService.findByStudyInstanceUid(studyUID).isEmpty()) {
                return ResponseEntity.notFound().build();
            }
            // Study는 있지만 Instance가 없는 경우 - 빈 배열 반환
        }

        // DICOM JSON 형식으로 변환
        // Instance에서 Series를 eager loading으로 가져왔으므로 추가 쿼리 없음
        List<Map<String, Object>> result = instances.stream()
                .map(instance -> instanceToDicomJson(
                        instance,
                        studyUID,
                        instance.getSeries().getSeriesInstanceUid()))
                .toList();

        log.info("WADO-RS: Returning {} instance metadata for study (single query)", result.size());

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(DICOM_JSON_MEDIA_TYPE))
                .body(result);
    }

    /**
     * WADO-RS: Series 메타데이터 조회
     *
     * <p>성능 최적화 (2026-01-09):
     * JOIN FETCH로 Series, Study eager loading하여 N+1 쿼리 방지
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

        // CRITICAL: N+1 쿼리 최적화 - JOIN FETCH로 단일 쿼리 조회
        List<Instance> instances = instanceService.findAllBySeriesInstanceUid(seriesUID);

        if (instances.isEmpty()) {
            // Series가 존재하지 않거나 Instance가 없는 경우
            Series series = seriesService.findBySeriesInstanceUid(seriesUID).orElse(null);
            if (series == null || !studyUID.equals(series.getStudy().getStudyInstanceUid())) {
                return ResponseEntity.notFound().build();
            }
            // Series는 있지만 Instance가 없는 경우 - 빈 배열 반환
        } else {
            // Study UID 검증 (첫 번째 Instance의 Series에서 확인)
            String actualStudyUid = instances.get(0).getSeries().getStudy().getStudyInstanceUid();
            if (!studyUID.equals(actualStudyUid)) {
                return ResponseEntity.notFound().build();
            }
        }

        List<Map<String, Object>> result = instances.stream()
                .map(i -> instanceToDicomJson(i, studyUID, seriesUID))
                .toList();

        log.info("WADO-RS: Returning {} instance metadata for series (single query)", result.size());

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
     * WADO-URI: 레거시 WADO URL 지원 (썸네일 포함)
     *
     * <p>OHIF Viewer 호환성을 위해 WADO-URI도 지원합니다.
     *
     * <p>썸네일 지원 (2026-01-12):
     * <ul>
     *   <li>contentType=image/jpeg, rows, columns 파라미터로 썸네일 요청 가능</li>
     *   <li>파라미터 없으면 기존 동작 (DICOM 파일 반환)</li>
     * </ul>
     *
     * @param requestType WADO 요청 타입 (WADO)
     * @param studyUID Study Instance UID
     * @param seriesUID Series Instance UID
     * @param objectUID SOP Instance UID
     * @param contentType 요청 컨텐츠 타입 (optional, image/jpeg 또는 image/png)
     * @param rows 썸네일 높이 (optional, 기본값 128)
     * @param columns 썸네일 너비 (optional, 기본값 128)
     * @return DICOM 파일 스트림 또는 이미지 썸네일
     */
    @GetMapping(value = "/wado")
    @Operation(summary = "WADO-URI: 레거시 WADO 지원 (썸네일 포함)",
            description = "레거시 WADO-URI 형식을 지원합니다. contentType=image/jpeg 파라미터로 썸네일 요청 가능.")
    public ResponseEntity<?> retrieveInstanceViaWadoUri(
            @RequestParam String requestType,
            @RequestParam @Pattern(regexp = "^[0-9.]+$", message = "Invalid DICOM UID format") String studyUID,
            @RequestParam @Pattern(regexp = "^[0-9.]+$", message = "Invalid DICOM UID format") String seriesUID,
            @RequestParam @Pattern(regexp = "^[0-9.]+$", message = "Invalid DICOM UID format") String objectUID,
            @RequestParam(required = false) String contentType,
            @RequestParam(required = false) Integer rows,
            @RequestParam(required = false) Integer columns
    ) {
        if (!"WADO".equals(requestType)) {
            return ResponseEntity.badRequest().build();
        }

        log.info("WADO-URI: Request - objectUID={}, contentType={}, rows={}, columns={}",
                objectUID, contentType, rows, columns);

        // 썸네일 요청 (contentType이 image/jpeg 또는 image/png인 경우)
        if (contentType != null && contentType.startsWith("image/")) {
            return renderWadoUriThumbnail(studyUID, seriesUID, objectUID, contentType, rows, columns);
        }

        // 기본: DICOM 파일 반환
        return retrieveInstance(studyUID, seriesUID, objectUID);
    }

    /**
     * WADO-URI 썸네일 렌더링
     *
     * @param studyUID Study Instance UID
     * @param seriesUID Series Instance UID
     * @param objectUID SOP Instance UID
     * @param contentType 요청 컨텐츠 타입 (image/jpeg 또는 image/png)
     * @param rows 썸네일 높이 (null이면 128)
     * @param columns 썸네일 너비 (null이면 128)
     * @return 썸네일 이미지 바이트 배열
     */
    private ResponseEntity<byte[]> renderWadoUriThumbnail(
            String studyUID, String seriesUID, String objectUID,
            String contentType, Integer rows, Integer columns
    ) {
        Instance instance = instanceService.findBySopInstanceUid(objectUID)
                .orElseThrow(() -> new BusinessException(MiniPacsErrorCode.INSTANCE_NOT_FOUND));

        // UID 검증
        validateInstanceUIDs(instance, studyUID, seriesUID);

        // 응답 타입 결정
        MediaType mediaType = contentType.contains("png") ? MediaType.IMAGE_PNG : MediaType.IMAGE_JPEG;
        boolean isPng = mediaType.equals(MediaType.IMAGE_PNG);

        // 사전 렌더링된 썸네일 사용 여부 확인 (JPEG, 128x128일 때만)
        boolean usePrerenderedThumbnail = !isPng
                && instance.getTranscodingStatus() == Instance.TranscodingStatus.COMPLETED
                && instance.getThumbnailPath() != null
                && (rows == null || rows == 128) && (columns == null || columns == 128);

        if (usePrerenderedThumbnail) {
            // 사전 렌더링된 썸네일 사용
            try (var inputStream = dicomStorageService.downloadFileStream(instance.getThumbnailPath())) {
                byte[] imageBytes = inputStream.readAllBytes();

                log.info("WADO-URI Thumbnail (prerendered): objectUID={}, path={}, bytes={}",
                        objectUID, instance.getThumbnailPath(), imageBytes.length);

                return ResponseEntity.ok()
                        .contentType(mediaType)
                        .cacheControl(CacheControl.maxAge(Duration.ofHours(24)))
                        .body(imageBytes);
            } catch (Exception e) {
                log.warn("Failed to load prerendered thumbnail, falling back to real-time rendering: {}",
                        e.getMessage());
                // 실패 시 아래의 기존 로직으로 fallback
            }
        }

        // 기존 실시간 렌더링 로직
        String storagePath = instance.getStoragePath();

        // 기본 크기: 128x128
        int targetWidth = (columns != null && columns > 0) ? columns : 128;
        int targetHeight = (rows != null && rows > 0) ? rows : 128;

        // 최대 크기 제한 (보안/성능)
        targetWidth = Math.min(targetWidth, 512);
        targetHeight = Math.min(targetHeight, 512);

        // 썸네일은 작으므로 byte[]로 반환 (StreamingResponseBody 대신)
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        if (isPng) {
            // PNG: 리사이징 없이 원본 크기 (기존 로직)
            dicomRenderingService.renderToPngStream(storagePath, 1, baos);
        } else {
            // JPEG: 리사이징 적용 (썸네일용)
            dicomRenderingService.renderToJpegStream(storagePath, 1, targetWidth, targetHeight, baos);
        }

        byte[] imageBytes = baos.toByteArray();

        log.info("WADO-URI Thumbnail: objectUID={}, format={}, size={}x{}, bytes={}",
                objectUID, isPng ? "PNG" : "JPEG", targetWidth, targetHeight, imageBytes.length);

        return ResponseEntity.ok()
                .contentType(mediaType)
                .cacheControl(CacheControl.maxAge(Duration.ofHours(24)))  // 썸네일 캐시 24시간
                .body(imageBytes);
    }

    // ========== WADO-RS: Rendered Services ==========

    /**
     * WADO-RS: Instance Rendered (싱글프레임 또는 첫 프레임)
     *
     * <p>DICOM 파일을 PNG 이미지로 렌더링하여 반환합니다.
     * 멀티프레임 DICOM의 경우 첫 번째 프레임을 반환합니다.
     *
     * <p>성능 최적화 (2026-01-09):
     * <ul>
     *   <li>기존: byte[] 전체 메모리 로드 → 동시 10개 요청 시 2.5GB 메모리</li>
     *   <li>개선: StreamingResponseBody로 직접 스트리밍 → 메모리 90% 절감</li>
     * </ul>
     *
     * @param studyUID Study Instance UID
     * @param seriesUID Series Instance UID
     * @param sopInstanceUID SOP Instance UID
     * @return PNG 이미지 (StreamingResponseBody)
     */
    @GetMapping(value = "/studies/{studyUID}/series/{seriesUID}/instances/{sopInstanceUID}/rendered")
    @Operation(summary = "WADO-RS: Instance Rendered",
            description = "DICOM 파일을 이미지로 렌더링합니다. quality < 100이면 JPEG, 100이면 PNG. rows/columns로 다운샘플링 가능.")
    public ResponseEntity<StreamingResponseBody> renderInstance(
            @Parameter(description = "Study Instance UID") @PathVariable String studyUID,
            @Parameter(description = "Series Instance UID") @PathVariable String seriesUID,
            @Parameter(description = "SOP Instance UID") @PathVariable String sopInstanceUID,
            @Parameter(description = "이미지 품질 (1-100). 100=PNG(기본), <100=JPEG")
            @RequestParam(required = false, defaultValue = "100") Integer quality,
            @Parameter(description = "출력 이미지 높이 (다운샘플링)")
            @RequestParam(required = false) Integer rows,
            @Parameter(description = "출력 이미지 너비 (다운샘플링)")
            @RequestParam(required = false) Integer columns
    ) {
        log.info("WADO-RS Rendered: instance - sopInstanceUID={}, quality={}, rows={}, columns={}",
                sopInstanceUID, quality, rows, columns);

        Instance instance = instanceService.findBySopInstanceUid(sopInstanceUID)
                .orElseThrow(() -> new BusinessException(MiniPacsErrorCode.INSTANCE_NOT_FOUND));

        // UID 검증
        validateInstanceUIDs(instance, studyUID, seriesUID);

        String storagePath = instance.getStoragePath();

        // 통합 렌더링 메서드 사용 (quality + viewport)
        StreamingResponseBody responseBody = outputStream -> {
            dicomRenderingService.renderToStreamWithOptions(storagePath, 1, quality, rows, columns, outputStream);
        };

        // Content-Type 결정: quality < 100이면 JPEG, 100이면 PNG
        MediaType contentType = quality < 100 ? MediaType.IMAGE_JPEG : MediaType.IMAGE_PNG;

        return ResponseEntity.ok()
                .contentType(contentType)
                .cacheControl(CacheControl.maxAge(Duration.ofHours(1)))
                .body(responseBody);
    }

    /**
     * WADO-RS: Frame Rendered (멀티프레임) - DICOMweb Part 18 표준
     *
     * <p>DICOM 파일의 프레임을 PNG 이미지로 렌더링하여 반환합니다.
     *
     * <p>지원 형식 (DICOMweb FrameList 표준):
     * <ul>
     *   <li>/frames/1/rendered - 단일 프레임</li>
     *   <li>/frames/1,2,3,4,5/rendered - 다중 프레임 (쉼표 구분, multipart/related 응답)</li>
     * </ul>
     *
     * <p>성능 최적화 (2026-01-09):
     * <ul>
     *   <li>기존: 프레임당 1 HTTP 요청</li>
     *   <li>개선: 다중 프레임 1 HTTP 요청으로 DICOM 파일 1회 로드</li>
     * </ul>
     *
     * @param studyUID Study Instance UID
     * @param seriesUID Series Instance UID
     * @param sopInstanceUID SOP Instance UID
     * @param frameList 프레임 번호 (1부터 시작) 또는 쉼표로 구분된 목록
     * @return PNG 이미지 (단일) 또는 multipart/related (다중)
     */
    @GetMapping(value = "/studies/{studyUID}/series/{seriesUID}/instances/{sopInstanceUID}/frames/{frameList}/rendered")
    @Operation(summary = "WADO-RS: Frame Rendered",
            description = "DICOM 프레임을 이미지로 렌더링 (단일/다중 프레임 지원). quality < 100이면 JPEG, 100이면 PNG. rows/columns로 다운샘플링 가능. resolution으로 사전렌더링 이미지 선택 (512/256/128).")
    public ResponseEntity<StreamingResponseBody> renderFrames(
            @Parameter(description = "Study Instance UID") @PathVariable String studyUID,
            @Parameter(description = "Series Instance UID") @PathVariable String seriesUID,
            @Parameter(description = "SOP Instance UID") @PathVariable String sopInstanceUID,
            @Parameter(description = "프레임 번호 또는 쉼표로 구분된 목록") @PathVariable String frameList,
            @Parameter(description = "이미지 품질 (1-100). 100=PNG(기본), <100=JPEG")
            @RequestParam(required = false, defaultValue = "100") Integer quality,
            @Parameter(description = "출력 이미지 높이 (다운샘플링)")
            @RequestParam(required = false) Integer rows,
            @Parameter(description = "출력 이미지 너비 (다운샘플링)")
            @RequestParam(required = false) Integer columns,
            @Parameter(description = "사전렌더링 해상도 (512=PNG, 256=JPEG, 128=JPEG). 기본값 512.")
            @RequestParam(required = false, defaultValue = "512") Integer resolution
    ) {
        // frameList 파싱
        List<Integer> frameNumbers = parseFrameList(frameList);

        log.info("WADO-RS Rendered: frames - sopInstanceUID={}, frames={}, quality={}, rows={}, columns={}, resolution={}",
                sopInstanceUID, frameNumbers, quality, rows, columns, resolution);

        Instance instance = instanceService.findBySopInstanceUid(sopInstanceUID)
                .orElseThrow(() -> new BusinessException(MiniPacsErrorCode.INSTANCE_NOT_FOUND));

        // UID 검증
        validateInstanceUIDs(instance, studyUID, seriesUID);

        // 프레임 번호 검증
        int totalFrames = instance.getNumberOfFrames() != null ? instance.getNumberOfFrames() : 1;
        validateFrameNumbers(frameNumbers, totalFrames);

        String storagePath = instance.getStoragePath();

        // 사전 렌더링 데이터 사용 여부 확인
        boolean usePrerendered = instance.getTranscodingStatus() == Instance.TranscodingStatus.COMPLETED
                && instance.getPrerenderedBasePath() != null;

        // 단일 프레임
        if (frameNumbers.size() == 1) {
            int frameNumber = frameNumbers.get(0);

            // quality/rows/columns 중 하나라도 지정되면 실시간 렌더링 (사전 렌더링 무시)
            boolean needsRealTimeRendering = quality < 100 || rows != null || columns != null;

            if (needsRealTimeRendering) {
                // 통합 렌더링 메서드 사용 (quality + viewport)
                StreamingResponseBody responseBody = outputStream -> {
                    dicomRenderingService.renderToStreamWithOptions(
                            storagePath, frameNumber, quality, rows, columns, outputStream);
                };

                MediaType contentType = quality < 100 ? MediaType.IMAGE_JPEG : MediaType.IMAGE_PNG;

                return ResponseEntity.ok()
                        .contentType(contentType)
                        .cacheControl(CacheControl.maxAge(Duration.ofHours(1)))
                        .body(responseBody);
            }

            // PNG/JPEG 사전 렌더링 (quality=100, rows/columns 미지정)
            if (usePrerendered) {
                // resolution에 따라 사전 렌더링 경로 및 Content-Type 결정
                String prerenderedPath;
                MediaType contentType;

                switch (resolution) {
                    case 256:
                        prerenderedPath = String.format("%s/cine/frame-%03d-256.jpg",
                                instance.getPrerenderedBasePath(), frameNumber);
                        contentType = MediaType.IMAGE_JPEG;
                        break;
                    case 128:
                        prerenderedPath = String.format("%s/cine/frame-%03d-128.jpg",
                                instance.getPrerenderedBasePath(), frameNumber);
                        contentType = MediaType.IMAGE_JPEG;
                        break;
                    case 64:
                        prerenderedPath = String.format("%s/cine/frame-%03d-64.jpg",
                                instance.getPrerenderedBasePath(), frameNumber);
                        contentType = MediaType.IMAGE_JPEG;
                        break;
                    case 32:
                        prerenderedPath = String.format("%s/cine/frame-%03d-32.jpg",
                                instance.getPrerenderedBasePath(), frameNumber);
                        contentType = MediaType.IMAGE_JPEG;
                        break;
                    default: // 512 또는 기타 → cine-256 사용 (rendered PNG 대신, 용량 절감)
                        prerenderedPath = String.format("%s/cine/frame-%03d-256.jpg",
                                instance.getPrerenderedBasePath(), frameNumber);
                        contentType = MediaType.IMAGE_JPEG;
                }

                StreamingResponseBody responseBody = outputStream -> {
                    try (var inputStream = dicomStorageService.downloadFileStream(prerenderedPath)) {
                        inputStream.transferTo(outputStream);
                        log.info("WADO-RS Rendered (prerendered): frame={}, resolution={}, path={}",
                                frameNumber, resolution, prerenderedPath);
                    }
                };

                return ResponseEntity.ok()
                        .contentType(contentType)
                        .cacheControl(CacheControl.maxAge(Duration.ofHours(1)))
                        .body(responseBody);
            } else {
                // 기존 실시간 PNG 렌더링 로직
                StreamingResponseBody responseBody = outputStream -> {
                    dicomRenderingService.renderToPngStream(storagePath, frameNumber, outputStream);
                };

                return ResponseEntity.ok()
                        .contentType(MediaType.IMAGE_PNG)
                        .cacheControl(CacheControl.maxAge(Duration.ofHours(1)))
                        .body(responseBody);
            }
        }

        // 다중 프레임: multipart/related 응답 (DICOMweb FrameList 표준)
        String boundary = "rendered_boundary_" + System.currentTimeMillis();

        // quality에 따라 Content-Type 결정
        String imageType = quality < 100 ? "image/jpeg" : "image/png";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(
                "multipart/related; type=\"" + imageType + "\"; boundary=" + boundary));
        headers.setCacheControl(CacheControl.maxAge(Duration.ofHours(1)));

        StreamingResponseBody responseBody;

        // quality < 100 또는 rows/columns 지정 시 실시간 렌더링 (통합 메서드 사용)
        boolean needsRealTimeRendering = quality < 100 || rows != null || columns != null;

        if (needsRealTimeRendering) {
            final int renderQuality = quality;
            final Integer renderRows = rows;
            final Integer renderColumns = columns;

            responseBody = outputStream -> {
                for (int frameNumber : frameNumbers) {
                    // Multipart 헤더 작성
                    String partHeader = "--" + boundary + "\r\n"
                            + "Content-Type: " + imageType + "\r\n"
                            + "Content-Location: /frames/" + frameNumber + "/rendered\r\n"
                            + "\r\n";
                    outputStream.write(partHeader.getBytes(StandardCharsets.UTF_8));

                    // 통합 렌더링 (quality + viewport)
                    dicomRenderingService.renderToStreamWithOptions(
                            storagePath, frameNumber, renderQuality, renderRows, renderColumns, outputStream);

                    outputStream.write("\r\n".getBytes(StandardCharsets.UTF_8));
                }
                // 종료 boundary
                String endBoundary = "--" + boundary + "--\r\n";
                outputStream.write(endBoundary.getBytes(StandardCharsets.UTF_8));
                outputStream.flush();

                log.info("WADO-RS Rendered (realtime): frames={}, quality={}, rows={}, columns={}",
                        frameNumbers.size(), renderQuality, renderRows, renderColumns);
            };
        } else if (usePrerendered) {
            // 다중 프레임: 사전 렌더링 데이터 사용 (quality=100, rows/columns 미지정)
            // resolution에 따라 경로 및 Content-Type 결정
            String basePath = instance.getPrerenderedBasePath();
            final String multipartImageType;
            final String pathPattern;

            switch (resolution) {
                case 256:
                    multipartImageType = "image/jpeg";
                    pathPattern = "%s/cine/frame-%03d-256.jpg";
                    break;
                case 128:
                    multipartImageType = "image/jpeg";
                    pathPattern = "%s/cine/frame-%03d-128.jpg";
                    break;
                case 64:
                    multipartImageType = "image/jpeg";
                    pathPattern = "%s/cine/frame-%03d-64.jpg";
                    break;
                case 32:
                    multipartImageType = "image/jpeg";
                    pathPattern = "%s/cine/frame-%03d-32.jpg";
                    break;
                default: // 512 또는 기타 → cine-256 사용 (rendered PNG 대신, 용량 절감)
                    multipartImageType = "image/jpeg";
                    pathPattern = "%s/cine/frame-%03d-256.jpg";
            }

            responseBody = outputStream -> {
                for (int frameNumber : frameNumbers) {
                    String prerenderedPath = String.format(pathPattern, basePath, frameNumber);

                    try (var inputStream = dicomStorageService.downloadFileStream(prerenderedPath)) {
                        // Multipart 헤더 작성
                        String partHeader = "--" + boundary + "\r\n"
                                + "Content-Type: " + multipartImageType + "\r\n"
                                + "Content-Location: /frames/" + frameNumber + "/rendered\r\n"
                                + "\r\n";
                        outputStream.write(partHeader.getBytes(StandardCharsets.UTF_8));

                        // 사전 렌더링 데이터 스트리밍
                        inputStream.transferTo(outputStream);

                        outputStream.write("\r\n".getBytes(StandardCharsets.UTF_8));
                    }
                }
                // 종료 boundary
                String endBoundary = "--" + boundary + "--\r\n";
                outputStream.write(endBoundary.getBytes(StandardCharsets.UTF_8));
                outputStream.flush();

                log.info("WADO-RS Rendered (prerendered): frames={}, resolution={}, basePath={}",
                        frameNumbers.size(), resolution, basePath);
            };
        } else {
            // 기존 실시간 PNG 렌더링 (quality=100, rows/columns 미지정, 사전 렌더링 없음)
            responseBody = outputStream -> {
                dicomRenderingService.renderMultipleFramesToStream(
                        storagePath, frameNumbers, boundary, outputStream);
            };
        }

        return new ResponseEntity<>(responseBody, headers, HttpStatus.OK);
    }

    /**
     * WADO-RS: Frame BulkData (Raw Pixel Data) - DICOMweb Part 18 표준
     *
     * <p>DICOMweb 표준 (DICOM Part 18 - 6.5.4)에 따라 프레임의 PixelData를 반환합니다.
     * Cornerstone3D의 wadors: 로더가 기대하는 형식입니다.
     *
     * <p>지원 형식 (DICOMweb FrameList 표준):
     * <ul>
     *   <li>/frames/1 - 단일 프레임</li>
     *   <li>/frames/1,2,3,4,5 - 다중 프레임 (쉼표 구분)</li>
     * </ul>
     *
     * <p>응답 형식:
     * <ul>
     *   <li>Content-Type: multipart/related; type="application/octet-stream"; boundary=...</li>
     *   <li>Body: 요청된 프레임들의 raw PixelData (multipart 파트로 구분)</li>
     * </ul>
     *
     * <p>성능 최적화 (2026-01-09):
     * <ul>
     *   <li>기존: 프레임당 1 HTTP 요청 → 100프레임 시 100 요청</li>
     *   <li>개선: 다중 프레임 1 HTTP 요청 → 100프레임 시 10 요청 (배치 크기 10)</li>
     *   <li>DICOM 파일 1회 로드로 다중 프레임 추출 → I/O 90% 절감</li>
     * </ul>
     *
     * @param studyUID Study Instance UID
     * @param seriesUID Series Instance UID
     * @param sopInstanceUID SOP Instance UID
     * @param frameList 프레임 번호 (1부터 시작) 또는 쉼표로 구분된 목록 (예: "1" 또는 "1,2,3,4,5")
     * @return multipart/related 형식의 PixelData (StreamingResponseBody)
     */
    @GetMapping(value = "/studies/{studyUID}/series/{seriesUID}/instances/{sopInstanceUID}/frames/{frameList}")
    @Operation(summary = "WADO-RS: Frame BulkData", description = "DICOMweb 표준에 따라 프레임의 raw PixelData를 반환 (단일/다중 프레임 지원)")
    public ResponseEntity<StreamingResponseBody> retrieveFrames(
            @Parameter(description = "Study Instance UID") @PathVariable String studyUID,
            @Parameter(description = "Series Instance UID") @PathVariable String seriesUID,
            @Parameter(description = "SOP Instance UID") @PathVariable String sopInstanceUID,
            @Parameter(description = "프레임 번호 또는 쉼표로 구분된 목록 (예: 1 또는 1,2,3,4,5)") @PathVariable String frameList,
            @Parameter(description = "Accept 헤더 (압축 유지: image/jp2, 디코딩: application/octet-stream)")
            @RequestHeader(value = "Accept", defaultValue = "multipart/related; type=\"application/octet-stream\"") String acceptHeader,
            @Parameter(description = "데이터 포맷: auto(jpeg>compressed>raw), jpeg(JPEG Baseline), raw(디코딩된 픽셀), compressed/original(원본 압축)")
            @RequestParam(value = "format", defaultValue = "auto") String format
    ) {
        // frameList 파싱 (예: "1" → [1], "1,2,3" → [1,2,3])
        List<Integer> frameNumbers = parseFrameList(frameList);

        log.info("WADO-RS BulkData: frames - sopInstanceUID={}, frames={}", sopInstanceUID, frameNumbers);

        Instance instance = instanceService.findBySopInstanceUid(sopInstanceUID)
                .orElseThrow(() -> new BusinessException(MiniPacsErrorCode.INSTANCE_NOT_FOUND));

        // UID 검증
        validateInstanceUIDs(instance, studyUID, seriesUID);

        // 프레임 번호 검증
        int totalFrames = instance.getNumberOfFrames() != null ? instance.getNumberOfFrames() : 1;
        validateFrameNumbers(frameNumbers, totalFrames);

        // Transfer Syntax 조회 (원본 - 로깅용)
        String originalTransferSyntax = instance.getTransferSyntaxUid() != null
                ? instance.getTransferSyntaxUid()
                : dicomRenderingService.getTransferSyntaxUid(instance.getStoragePath());

        // Transfer Syntax에 따른 동적 처리 (Accept 헤더 기반 협상)
        // - Accept에 image/jp2 또는 image/jpeg 포함: 압축 유지 (클라이언트 디코딩)
        // - 기본값 (application/octet-stream): 서버에서 디코딩하여 raw pixels 반환
        boolean isCompressed = dicomRenderingService.isCompressedTransferSyntax(originalTransferSyntax);

        // Accept 헤더 파싱: 클라이언트가 압축 포맷을 지원하는지 확인
        boolean clientSupportsCompressed = acceptHeader != null &&
                (acceptHeader.contains("image/jp2") || acceptHeader.contains("image/jpeg"));

        // 압축 유지 조건: 원본이 압축됨 + 클라이언트가 압축 포맷 지원
        boolean preserveCompression = isCompressed && clientSupportsCompressed;

        log.debug("WADO-RS BulkData: Accept header negotiation - acceptHeader={}, isCompressed={}, clientSupportsCompressed={}, preserveCompression={}",
                acceptHeader, isCompressed, clientSupportsCompressed, preserveCompression);

        String outputTransferSyntax;
        String mimeType;

        if (preserveCompression) {
            // 압축 유지: 원본 Transfer Syntax와 해당 MIME 타입 사용
            outputTransferSyntax = originalTransferSyntax;
            mimeType = dicomRenderingService.getMimeTypeForTransferSyntax(originalTransferSyntax);
        } else {
            // 비압축 또는 디코딩 요청: 기존 로직
            outputTransferSyntax = UID.ExplicitVRLittleEndian;  // 1.2.840.10008.1.2.1
            mimeType = "application/octet-stream";
        }

        // Multipart boundary 생성
        String boundary = "frame_boundary_" + System.currentTimeMillis();

        String storagePath = instance.getStoragePath();

        // 사전 렌더링 데이터 사용 여부 확인
        boolean usePrerendered = instance.getTranscodingStatus() == Instance.TranscodingStatus.COMPLETED
                && instance.getPrerenderedBasePath() != null;

        // format 파라미터에 따른 데이터 포맷 결정
        // - auto: 우선순위 jpeg > compressed > raw
        // - jpeg: JPEG Baseline 데이터 (jpeg/ 폴더)
        // - original/compressed: 원본 압축 데이터 (compressed/ 폴더)
        // - raw/decoded: 디코딩된 픽셀 데이터 (bulkdata/ 폴더)
        boolean hasCompressedData = Boolean.TRUE.equals(instance.getHasCompressedBulkdata());
        boolean hasJpegBaseline = Boolean.TRUE.equals(instance.getHasJpegBaseline());

        // format에 따른 사용 포맷 결정
        boolean useJpegFormat = false;
        boolean useCompressedFormat = false;

        if ("jpeg-baseline".equalsIgnoreCase(format)) {
            // 명시적 jpeg-baseline 요청
            useJpegFormat = hasJpegBaseline;
        } else if ("original".equalsIgnoreCase(format) || "compressed".equalsIgnoreCase(format)) {
            // 명시적 compressed 요청
            useCompressedFormat = hasCompressedData;
        } else if ("auto".equalsIgnoreCase(format)) {
            // auto: 우선순위 jpeg > compressed > raw
            if (hasJpegBaseline) {
                useJpegFormat = true;
            } else if (hasCompressedData) {
                useCompressedFormat = true;
            }
            // else: raw 사용 (기본값)
        }
        // else: raw/decoded 요청 - 둘 다 false로 유지

        // 출력 설정 결정
        String effectiveMimeType = mimeType;
        String effectiveTransferSyntax = outputTransferSyntax;

        if (useJpegFormat && usePrerendered) {
            effectiveMimeType = "image/jpeg";
            effectiveTransferSyntax = UID.JPEGBaseline8Bit;  // 1.2.840.10008.1.2.4.50
        } else if (useCompressedFormat && usePrerendered) {
            effectiveMimeType = dicomRenderingService.getMimeTypeForTransferSyntax(originalTransferSyntax);
            effectiveTransferSyntax = originalTransferSyntax;
        }

        log.debug("WADO-RS BulkData format selection: format={}, hasJpegBaseline={}, hasCompressedData={}, useJpegFormat={}, useCompressedFormat={}",
                format, hasJpegBaseline, hasCompressedData, useJpegFormat, useCompressedFormat);

        // 응답 헤더 설정 (format 선택 후)
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(
                "multipart/related; type=\"" + effectiveMimeType + "\"; boundary=" + boundary));
        headers.setCacheControl(CacheControl.maxAge(Duration.ofHours(1)));

        // DICOM 메타데이터 헤더 추가 (클라이언트가 픽셀 해석에 필요)
        headers.set("X-DICOM-TransferSyntax", effectiveTransferSyntax);
        headers.set("X-DICOM-OriginalTransferSyntax", originalTransferSyntax);
        headers.set("X-DICOM-Format", useJpegFormat ? "jpeg" : (useCompressedFormat ? "original" : "decoded"));
        headers.set("X-DICOM-DecompressedOnServer", String.valueOf(!useJpegFormat && !useCompressedFormat && !preserveCompression));
        if (instance.getPhotometricInterpretation() != null) {
            headers.set("X-DICOM-PhotometricInterpretation", instance.getPhotometricInterpretation());
        }
        if (instance.getBitsAllocated() != null) {
            headers.set("X-DICOM-BitsAllocated", String.valueOf(instance.getBitsAllocated()));
        }
        if (instance.getBitsStored() != null) {
            headers.set("X-DICOM-BitsStored", String.valueOf(instance.getBitsStored()));
        }

        // 단일 프레임 vs 다중 프레임 분기
        StreamingResponseBody responseBody;
        final String finalMimeType = effectiveMimeType;
        final String finalTransferSyntax = effectiveTransferSyntax;
        final boolean finalUseJpeg = useJpegFormat;
        final boolean finalUseCompressed = useCompressedFormat;

        if (frameNumbers.size() == 1) {
            int frameNumber = frameNumbers.get(0);

            if (usePrerendered) {
                // 사전 렌더링 데이터 사용 (S3에서 직접 반환)
                String prerenderedPath;
                if (finalUseJpeg) {
                    // JPEG Baseline 데이터 경로
                    prerenderedPath = String.format("%s/jpeg/frame-%03d.jpg",
                            instance.getPrerenderedBasePath(), frameNumber);
                } else if (finalUseCompressed) {
                    // 압축 데이터 경로 (확장자는 Transfer Syntax에 따라 다름)
                    String extension = getExtensionForTransferSyntax(originalTransferSyntax);
                    prerenderedPath = String.format("%s/compressed/frame-%03d.%s",
                            instance.getPrerenderedBasePath(), frameNumber, extension);
                } else {
                    // RAW 데이터 경로
                    prerenderedPath = String.format("%s/bulkdata/frame-%03d.raw",
                            instance.getPrerenderedBasePath(), frameNumber);
                }
                final String finalPath = prerenderedPath;
                responseBody = outputStream -> {
                    try (var inputStream = dicomStorageService.downloadFileStream(finalPath)) {
                        // Multipart 헤더 작성
                        String partHeader = "--" + boundary + "\r\n"
                                + "Content-Type: " + finalMimeType + "; transfer-syntax=" + finalTransferSyntax + "\r\n"
                                + "\r\n";
                        outputStream.write(partHeader.getBytes(StandardCharsets.UTF_8));

                        // 사전 렌더링 데이터 스트리밍
                        inputStream.transferTo(outputStream);

                        // Part 종료
                        String partFooter = "\r\n--" + boundary + "--\r\n";
                        outputStream.write(partFooter.getBytes(StandardCharsets.UTF_8));
                        outputStream.flush();

                        String formatName = finalUseJpeg ? "jpeg" : (finalUseCompressed ? "original" : "decoded");
                        log.info("WADO-RS BulkData (prerendered): frame={}, path={}, format={}",
                                frameNumber, finalPath, formatName);
                    }
                };
            } else {
                // 실시간 렌더링 로직 (압축 유지 지원)
                responseBody = outputStream -> {
                    int dataSize = dicomRenderingService.extractFrameDataPreservingTransferSyntaxToStream(
                            storagePath, frameNumber, boundary, preserveCompression, outputStream);
                    log.info("WADO-RS BulkData: frame={}, tsUid={}, preserveCompression={}, size={}",
                            frameNumber, outputTransferSyntax, preserveCompression, dataSize);
                };
            }
        } else {
            if (usePrerendered) {
                // 다중 프레임: 사전 렌더링 데이터 사용
                String basePath = instance.getPrerenderedBasePath();
                String compressedExtension = finalUseCompressed ? getExtensionForTransferSyntax(originalTransferSyntax) : null;
                responseBody = outputStream -> {
                    int successCount = 0;
                    int failCount = 0;

                    for (int i = 0; i < frameNumbers.size(); i++) {
                        int frameNumber = frameNumbers.get(i);
                        String prerenderedPath;
                        if (finalUseJpeg) {
                            // JPEG Baseline 데이터 경로
                            prerenderedPath = String.format("%s/jpeg/frame-%03d.jpg", basePath, frameNumber);
                        } else if (finalUseCompressed) {
                            prerenderedPath = String.format("%s/compressed/frame-%03d.%s", basePath, frameNumber, compressedExtension);
                        } else {
                            prerenderedPath = String.format("%s/bulkdata/frame-%03d.raw", basePath, frameNumber);
                        }

                        try {
                            try (var inputStream = dicomStorageService.downloadFileStream(prerenderedPath)) {
                                // Multipart 헤더 작성
                                String partHeader = "--" + boundary + "\r\n"
                                        + "Content-Type: " + finalMimeType + "; transfer-syntax=" + finalTransferSyntax + "\r\n"
                                        + "Content-Location: /frames/" + frameNumber + "\r\n"
                                        + "\r\n";
                                outputStream.write(partHeader.getBytes(StandardCharsets.UTF_8));

                                // 사전 렌더링 데이터 스트리밍
                                inputStream.transferTo(outputStream);

                                outputStream.write("\r\n".getBytes(StandardCharsets.UTF_8));
                                successCount++;
                            }
                        } catch (Exception e) {
                            // 개별 프레임 실패 시 에러 마커 프레임 반환 (빈 데이터)
                            log.warn("Failed to load prerendered frame {}: path={}, error={}",
                                    frameNumber, prerenderedPath, e.getMessage());

                            // 에러 마커 파트 작성 (Content-Length: 0)
                            String errorPartHeader = "--" + boundary + "\r\n"
                                    + "Content-Type: " + finalMimeType + "; transfer-syntax=" + finalTransferSyntax + "\r\n"
                                    + "Content-Location: /frames/" + frameNumber + "\r\n"
                                    + "Content-Length: 0\r\n"
                                    + "\r\n";
                            outputStream.write(errorPartHeader.getBytes(StandardCharsets.UTF_8));
                            outputStream.write("\r\n".getBytes(StandardCharsets.UTF_8));
                            failCount++;
                        }
                    }

                    // 종료 boundary (항상 작성)
                    String endBoundary = "--" + boundary + "--\r\n";
                    outputStream.write(endBoundary.getBytes(StandardCharsets.UTF_8));
                    outputStream.flush();

                    String formatName = finalUseJpeg ? "jpeg" : (finalUseCompressed ? "compressed" : "raw");
                    log.info("WADO-RS BulkData (prerendered): frames={}, success={}, failed={}, basePath={}, format={}",
                            frameNumbers.size(), successCount, failCount, basePath, formatName);
                };
            } else {
                // 실시간 렌더링: DICOM 1회 로드로 최적화 (압축 유지 지원)
                responseBody = outputStream -> {
                    int totalSize = dicomRenderingService.extractMultipleFramesPreservingTransferSyntaxToStream(
                            storagePath, frameNumbers, boundary, preserveCompression, outputStream);
                    log.info("WADO-RS BulkData: frames={}, tsUid={}, preserveCompression={}, totalSize={}",
                            frameNumbers.size(), outputTransferSyntax, preserveCompression, totalSize);
                };
            }
        }

        return new ResponseEntity<>(responseBody, headers, HttpStatus.OK);
    }

    /**
     * Multipart/Related 형식의 Frame Body 생성
     *
     * <p>DICOMweb WADO-RS Part 18, Section 8.7.3.4 표준에 따른 multipart 형식:
     * <pre>
     * --boundary
     * Content-Type: {mimeType}; transfer-syntax={transferSyntax}
     * Content-Length: {length}
     *
     * [PixelData bytes]
     * --boundary--
     * </pre>
     *
     * @param pixelData 프레임 픽셀 데이터 (압축 또는 비압축)
     * @param boundary multipart boundary 문자열
     * @param transferSyntax Transfer Syntax UID
     * @param mimeType Content-Type MIME 타입 (예: image/jp2, image/jpeg)
     * @return multipart body 바이트 배열
     */
    private byte[] buildMultipartFrameBody(byte[] pixelData, String boundary,
                                           String transferSyntax, String mimeType) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();

            // Part 헤더 - MIME Type과 Transfer Syntax 포함
            String partHeader = "--" + boundary + "\r\n"
                    + "Content-Type: " + mimeType + "; transfer-syntax=" + transferSyntax + "\r\n"
                    + "Content-Length: " + pixelData.length + "\r\n"
                    + "\r\n";

            baos.write(partHeader.getBytes(StandardCharsets.UTF_8));

            // PixelData 본문
            baos.write(pixelData);

            // Part 종료
            String partFooter = "\r\n--" + boundary + "--\r\n";
            baos.write(partFooter.getBytes(StandardCharsets.UTF_8));

            return baos.toByteArray();

        } catch (IOException e) {
            throw new BusinessException(MiniPacsErrorCode.DICOM_RENDER_FAILED,
                    "Failed to build multipart body: " + e.getMessage());
        }
    }

    /**
     * Transfer Syntax UID를 MIME Type으로 변환
     *
     * <p>DICOMweb Part 18, Section 8.7.3.4 표준 준수:
     * 압축된 프레임 데이터는 해당 압축 포맷의 MIME 타입으로 반환해야 함
     *
     * @param tsUid Transfer Syntax UID
     * @return 해당하는 MIME Type (비압축/알 수 없는 경우 application/octet-stream)
     */
    private String getMediaTypeForTransferSyntax(String tsUid) {
        if (tsUid == null) return "application/octet-stream";

        return switch (tsUid) {
            // JPEG 2000 (Lossless 및 Lossy)
            case UID.JPEG2000Lossless, UID.JPEG2000 -> "image/jp2";
            // JPEG (Baseline, Extended, Lossless)
            case UID.JPEGBaseline8Bit, UID.JPEGExtended12Bit,
                 UID.JPEGLossless, UID.JPEGLosslessSV1 -> "image/jpeg";
            // JPEG-LS
            case UID.JPEGLSLossless, UID.JPEGLSNearLossless -> "image/jls";
            // RLE (별도 MIME 타입 없음)
            case UID.RLELossless -> "application/octet-stream";
            // 비압축 (Implicit/Explicit VR Little/Big Endian)
            default -> "application/octet-stream";
        };
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

    /**
     * frameList 문자열 파싱 (DICOMweb Part 18 표준)
     *
     * <p>예시:
     * <ul>
     *   <li>"1" → [1]</li>
     *   <li>"1,2,3,4,5" → [1, 2, 3, 4, 5]</li>
     * </ul>
     *
     * @param frameList 쉼표로 구분된 프레임 번호 문자열
     * @return 프레임 번호 목록
     * @throws BusinessException INVALID_FRAME_NUMBER - 파싱 실패 시
     */
    private List<Integer> parseFrameList(String frameList) {
        try {
            return Arrays.stream(frameList.split(","))
                    .map(String::trim)
                    .map(Integer::parseInt)
                    .distinct()  // 중복 제거
                    .collect(Collectors.toList());  // 순서 유지
        } catch (NumberFormatException e) {
            throw new BusinessException(MiniPacsErrorCode.INVALID_FRAME_NUMBER,
                    "Invalid frame list format: " + frameList);
        }
    }

    /**
     * 프레임 번호 목록 유효성 검증
     *
     * @param frameNumbers 검증할 프레임 번호 목록
     * @param totalFrames 총 프레임 수
     * @throws BusinessException INVALID_FRAME_NUMBER - 범위 초과 시
     */
    private void validateFrameNumbers(List<Integer> frameNumbers, int totalFrames) {
        for (int frame : frameNumbers) {
            if (frame < 1 || frame > totalFrames) {
                throw new BusinessException(MiniPacsErrorCode.INVALID_FRAME_NUMBER,
                        "Frame " + frame + " out of range (1-" + totalFrames + ")");
            }
        }
    }

    /**
     * Transfer Syntax UID에 따른 파일 확장자 반환
     *
     * @param transferSyntaxUid Transfer Syntax UID
     * @return 파일 확장자 (j2k, jpg, jls, bin)
     */
    private String getExtensionForTransferSyntax(String transferSyntaxUid) {
        if (transferSyntaxUid == null) return "bin";

        return switch (transferSyntaxUid) {
            // JPEG 2000
            case UID.JPEG2000Lossless, UID.JPEG2000 -> "j2k";
            // JPEG
            case UID.JPEGBaseline8Bit, UID.JPEGExtended12Bit,
                 UID.JPEGLossless, UID.JPEGLosslessSV1 -> "jpg";
            // JPEG-LS
            case UID.JPEGLSLossless, UID.JPEGLSNearLossless -> "jls";
            // 기타 (비압축 포함)
            default -> "bin";
        };
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
     *
     * <p>Cornerstone wadors 로더가 PixelData를 디코딩하는데 필요한
     * 모든 픽셀 메타데이터를 포함합니다.
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

        // ===== Pixel Metadata (Cornerstone wadors 로더 지원) =====

        // Samples per Pixel (0028,0002) - 픽셀당 채널 수 (1=grayscale, 3=RGB)
        if (instance.getSamplesPerPixel() != null) {
            json.put("00280002", createDicomValue("US", instance.getSamplesPerPixel()));
        }

        // Photometric Interpretation (0028,0004) - 픽셀 해석 방법
        if (instance.getPhotometricInterpretation() != null) {
            json.put("00280004", createDicomValue("CS", instance.getPhotometricInterpretation()));
        }

        // Bits Allocated (0028,0100) - 픽셀당 할당 비트
        if (instance.getBitsAllocated() != null) {
            json.put("00280100", createDicomValue("US", instance.getBitsAllocated()));
        }

        // Bits Stored (0028,0101) - 실제 저장 비트
        if (instance.getBitsStored() != null) {
            json.put("00280101", createDicomValue("US", instance.getBitsStored()));
        }

        // High Bit (0028,0102) - 최상위 비트 위치
        if (instance.getHighBit() != null) {
            json.put("00280102", createDicomValue("US", instance.getHighBit()));
        }

        // Pixel Representation (0028,0103) - 0=unsigned, 1=signed
        if (instance.getPixelRepresentation() != null) {
            json.put("00280103", createDicomValue("US", instance.getPixelRepresentation()));
        }

        // ===== Transfer Syntax (Cornerstone wadors 로더 필수) =====

        // Transfer Syntax UID (0002,0010) - PixelData 인코딩 방식
        // 원본 DICOM 파일의 Transfer Syntax를 반환 (UI 표시용)
        String transferSyntax = instance.getTransferSyntaxUid();
        if (transferSyntax == null || transferSyntax.isEmpty()) {
            transferSyntax = "1.2.840.10008.1.2.1"; // 기본값: Explicit VR Little Endian
        }
        json.put("00020010", createDicomValue("UI", transferSyntax));

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
