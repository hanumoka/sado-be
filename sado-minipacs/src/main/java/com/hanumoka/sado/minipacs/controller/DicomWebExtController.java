package com.hanumoka.sado.minipacs.controller;

import com.hanumoka.sado.common.exception.BusinessException;
import com.hanumoka.sado.common.tenant.TenantContext;
import com.hanumoka.sado.minipacs.code.MiniPacsErrorCode;
import com.hanumoka.sado.minipacs.domain.entity.Instance;
import com.hanumoka.sado.minipacs.domain.entity.Series;
import com.hanumoka.sado.minipacs.domain.entity.Study;
import com.hanumoka.sado.minipacs.domain.service.DicomRenderingService;
import com.hanumoka.sado.minipacs.domain.service.InstanceService;
import com.hanumoka.sado.minipacs.domain.service.StudyService;
import com.hanumoka.sado.minipacs.dto.request.BatchFrameRequest;
import com.hanumoka.sado.minipacs.storage.service.DicomStorageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * DICOMweb 확장 REST API Controller
 *
 * <p>표준 DICOMweb API를 확장한 커스텀 엔드포인트를 제공합니다.
 * <ul>
 *   <li>URL Path에 tenantId 포함</li>
 *   <li>DICOM UID 기반 API (확장 WADO-RS)</li>
 *   <li>내부 DB ID 기반 API</li>
 *   <li>Batch 프레임 조회</li>
 * </ul>
 *
 * @see DicomWebController 표준 DICOMweb API
 */
@RestController
@RequestMapping("/dicomweb-ext")
@RequiredArgsConstructor
@Slf4j
@Validated
@Tag(name = "DICOMweb Extension", description = "DICOMweb 확장 커스텀 API (tenantId 포함)")
public class DicomWebExtController {

    private static final String DICOM_JSON_MEDIA_TYPE = "application/dicom+json";
    private static final String DICOM_MEDIA_TYPE = "application/dicom";

    private final StudyService studyService;
    private final InstanceService instanceService;
    private final DicomStorageService dicomStorageService;
    private final DicomRenderingService dicomRenderingService;

    // ========== Helper: 테넌트 설정 ==========

    /**
     * Path에서 받은 tenantId를 TenantContext에 설정
     */
    private void setTenantContext(Long tenantId) {
        TenantContext.setCurrentTenantId(tenantId);
        log.debug("TenantContext set from path: tenantId={}", tenantId);
    }

    // ========================================================================
    // 1. 확장 WADO-RS: DICOM UID 기반 API (tenantId 포함)
    // ========================================================================

    /**
     * 확장 WADO-RS: Study 메타데이터 조회
     */
    @GetMapping(value = "/{tenantId}/studies/{studyUID}/metadata", produces = DICOM_JSON_MEDIA_TYPE)
    @Operation(summary = "확장 WADO-RS: Study 메타데이터", description = "tenantId를 포함한 Study 메타데이터 조회")
    public ResponseEntity<List<Map<String, Object>>> getStudyMetadata(
            @Parameter(description = "테넌트 ID") @PathVariable Long tenantId,
            @Parameter(description = "Study Instance UID") @PathVariable String studyUID
    ) {
        setTenantContext(tenantId);
        log.info("Ext WADO-RS: Get study metadata - tenantId={}, studyUID={}", tenantId, studyUID);

        List<Instance> instances = instanceService.findAllByStudyInstanceUid(studyUID);

        if (instances.isEmpty()) {
            if (studyService.findByStudyInstanceUid(studyUID).isEmpty()) {
                return ResponseEntity.notFound().build();
            }
        }

        List<Map<String, Object>> result = instances.stream()
                .map(instance -> instanceToDicomJson(
                        instance,
                        studyUID,
                        instance.getSeries().getSeriesInstanceUid()))
                .toList();

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(DICOM_JSON_MEDIA_TYPE))
                .body(result);
    }

    /**
     * 확장 WADO-RS: Instance 다운로드
     */
    @GetMapping(value = "/{tenantId}/studies/{studyUID}/series/{seriesUID}/instances/{sopInstanceUID}",
            produces = DICOM_MEDIA_TYPE)
    @Operation(summary = "확장 WADO-RS: Instance 다운로드", description = "tenantId를 포함한 DICOM 파일 다운로드")
    public ResponseEntity<Resource> retrieveInstance(
            @Parameter(description = "테넌트 ID") @PathVariable Long tenantId,
            @Parameter(description = "Study Instance UID") @PathVariable String studyUID,
            @Parameter(description = "Series Instance UID") @PathVariable String seriesUID,
            @Parameter(description = "SOP Instance UID") @PathVariable String sopInstanceUID
    ) {
        setTenantContext(tenantId);
        log.info("Ext WADO-RS: Retrieve instance - tenantId={}, sopInstanceUID={}", tenantId, sopInstanceUID);

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

        Resource resource = dicomStorageService.downloadDicomFileAsResource(instance.getStoragePath());

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(DICOM_MEDIA_TYPE))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + sopInstanceUID + ".dcm\"")
                .body(resource);
    }

    /**
     * 확장 WADO-RS: Frame Rendered (단일/다중 프레임)
     */
    @GetMapping(value = "/{tenantId}/studies/{studyUID}/series/{seriesUID}/instances/{sopInstanceUID}/frames/{frameList}/rendered")
    @Operation(summary = "확장 WADO-RS: Frame Rendered", description = "tenantId를 포함한 프레임 렌더링")
    public ResponseEntity<StreamingResponseBody> renderFrames(
            @Parameter(description = "테넌트 ID") @PathVariable Long tenantId,
            @Parameter(description = "Study Instance UID") @PathVariable String studyUID,
            @Parameter(description = "Series Instance UID") @PathVariable String seriesUID,
            @Parameter(description = "SOP Instance UID") @PathVariable String sopInstanceUID,
            @Parameter(description = "프레임 번호 또는 쉼표로 구분된 목록") @PathVariable String frameList,
            @Parameter(description = "이미지 품질 (1-100)") @RequestParam(defaultValue = "100") Integer quality,
            @Parameter(description = "출력 이미지 높이") @RequestParam(required = false) Integer rows,
            @Parameter(description = "출력 이미지 너비") @RequestParam(required = false) Integer columns
    ) {
        setTenantContext(tenantId);
        List<Integer> frameNumbers = parseFrameList(frameList);
        log.info("Ext WADO-RS Rendered: tenantId={}, sopInstanceUID={}, frames={}", tenantId, sopInstanceUID, frameNumbers);

        Instance instance = instanceService.findBySopInstanceUid(sopInstanceUID)
                .orElseThrow(() -> new BusinessException(MiniPacsErrorCode.INSTANCE_NOT_FOUND));

        validateInstanceUIDs(instance, studyUID, seriesUID);

        int totalFrames = instance.getNumberOfFrames() != null ? instance.getNumberOfFrames() : 1;
        validateFrameNumbers(frameNumbers, totalFrames);

        String storagePath = instance.getStoragePath();

        // 단일 프레임
        if (frameNumbers.size() == 1) {
            int frameNumber = frameNumbers.get(0);
            StreamingResponseBody responseBody = outputStream -> {
                dicomRenderingService.renderToStreamWithOptions(storagePath, frameNumber, quality, rows, columns, outputStream);
            };

            MediaType contentType = quality < 100 ? MediaType.IMAGE_JPEG : MediaType.IMAGE_PNG;
            return ResponseEntity.ok()
                    .contentType(contentType)
                    .cacheControl(CacheControl.maxAge(Duration.ofHours(1)))
                    .body(responseBody);
        }

        // 다중 프레임: multipart/related 응답
        String boundary = "ext_rendered_boundary_" + System.currentTimeMillis();
        String imageType = quality < 100 ? "image/jpeg" : "image/png";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(
                "multipart/related; type=\"" + imageType + "\"; boundary=" + boundary));
        headers.setCacheControl(CacheControl.maxAge(Duration.ofHours(1)));

        StreamingResponseBody responseBody = outputStream -> {
            for (int frameNumber : frameNumbers) {
                String partHeader = "--" + boundary + "\r\n"
                        + "Content-Type: " + imageType + "\r\n"
                        + "Content-Location: /frames/" + frameNumber + "/rendered\r\n"
                        + "\r\n";
                outputStream.write(partHeader.getBytes(StandardCharsets.UTF_8));

                dicomRenderingService.renderToStreamWithOptions(storagePath, frameNumber, quality, rows, columns, outputStream);

                outputStream.write("\r\n".getBytes(StandardCharsets.UTF_8));
            }
            String endBoundary = "--" + boundary + "--\r\n";
            outputStream.write(endBoundary.getBytes(StandardCharsets.UTF_8));
            outputStream.flush();
        };

        return new ResponseEntity<>(responseBody, headers, HttpStatus.OK);
    }

    // ========================================================================
    // 2. 내부 DB ID 기반 API
    // ========================================================================

    /**
     * 내부 API: Instance 조회 (DB ID 기반)
     */
    @GetMapping(value = "/{tenantId}/internal/instances/{instanceId}")
    @Operation(summary = "내부 API: Instance 조회", description = "DB ID 기반 Instance 메타데이터 조회")
    public ResponseEntity<Map<String, Object>> getInstanceById(
            @Parameter(description = "테넌트 ID") @PathVariable Long tenantId,
            @Parameter(description = "Instance DB ID") @PathVariable Long instanceId
    ) {
        setTenantContext(tenantId);
        log.info("Internal API: Get instance - tenantId={}, instanceId={}", tenantId, instanceId);

        try {
            Instance instance = instanceService.findById(instanceId);
            Map<String, Object> result = instanceToInternalJson(instance);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.warn("Instance not found: instanceId={}", instanceId);
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * 내부 API: 프레임 BulkData 조회 (DB ID 기반, 단일/다중 지원)
     *
     * <p>단일 프레임: /frames/1 → application/octet-stream
     * <p>다중 프레임: /frames/1,2,3 → multipart/related 응답
     */
    @GetMapping(value = "/{tenantId}/internal/instances/{instanceId}/frames/{frameList}")
    @Operation(summary = "내부 API: 프레임 BulkData", description = "DB ID 기반 프레임 Raw Pixel Data 조회")
    public ResponseEntity<StreamingResponseBody> getFrameBulkDataById(
            @Parameter(description = "테넌트 ID") @PathVariable Long tenantId,
            @Parameter(description = "Instance DB ID") @PathVariable Long instanceId,
            @Parameter(description = "프레임 번호 목록 (쉼표 구분)") @PathVariable String frameList
    ) {
        setTenantContext(tenantId);
        List<Integer> frameNumbers = parseFrameList(frameList);
        log.info("Internal API: Get frame bulkdata - tenantId={}, instanceId={}, frames={}", tenantId, instanceId, frameNumbers);

        Instance instance;
        try {
            instance = instanceService.findById(instanceId);
        } catch (Exception e) {
            throw new BusinessException(MiniPacsErrorCode.INSTANCE_NOT_FOUND);
        }

        int totalFrames = instance.getNumberOfFrames() != null ? instance.getNumberOfFrames() : 1;
        validateFrameNumbers(frameNumbers, totalFrames);

        String storagePath = instance.getStoragePath();

        // 단일 프레임인 경우
        if (frameNumbers.size() == 1) {
            int frameNumber = frameNumbers.get(0);
            StreamingResponseBody responseBody = outputStream -> {
                byte[] pixelData = dicomRenderingService.extractFramePixelData(storagePath, frameNumber);
                outputStream.write(pixelData);
                outputStream.flush();
            };

            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .cacheControl(CacheControl.maxAge(Duration.ofHours(1)))
                    .body(responseBody);
        }

        // 다중 프레임: multipart/related 응답
        String boundary = "internal_bulkdata_boundary_" + System.currentTimeMillis();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(
                "multipart/related; type=\"application/octet-stream\"; boundary=" + boundary));
        headers.setCacheControl(CacheControl.maxAge(Duration.ofHours(1)));

        StreamingResponseBody responseBody = outputStream -> {
            for (int frameNumber : frameNumbers) {
                byte[] pixelData = dicomRenderingService.extractFramePixelData(storagePath, frameNumber);

                String partHeader = "--" + boundary + "\r\n"
                        + "Content-Type: application/octet-stream\r\n"
                        + "X-Frame-Number: " + frameNumber + "\r\n"
                        + "Content-Length: " + pixelData.length + "\r\n"
                        + "\r\n";
                outputStream.write(partHeader.getBytes(StandardCharsets.UTF_8));
                outputStream.write(pixelData);
                outputStream.write("\r\n".getBytes(StandardCharsets.UTF_8));
            }
            String endBoundary = "--" + boundary + "--\r\n";
            outputStream.write(endBoundary.getBytes(StandardCharsets.UTF_8));
            outputStream.flush();
        };

        return new ResponseEntity<>(responseBody, headers, HttpStatus.OK);
    }

    /**
     * 내부 API: 프레임 렌더링 (DB ID 기반, 단일/다중 지원)
     *
     * <p>단일 프레임: /frames/1/rendered
     * <p>다중 프레임: /frames/1,2,3/rendered (multipart/related 응답)
     */
    @GetMapping(value = "/{tenantId}/internal/instances/{instanceId}/frames/{frameList}/rendered")
    @Operation(summary = "내부 API: 프레임 렌더링", description = "DB ID 기반 프레임 렌더링 (단일 또는 쉼표 구분 다중)")
    public ResponseEntity<StreamingResponseBody> renderFramesById(
            @Parameter(description = "테넌트 ID") @PathVariable Long tenantId,
            @Parameter(description = "Instance DB ID") @PathVariable Long instanceId,
            @Parameter(description = "프레임 번호 목록 (쉼표 구분)") @PathVariable String frameList,
            @Parameter(description = "이미지 품질 (1-100)") @RequestParam(defaultValue = "100") Integer quality,
            @Parameter(description = "출력 이미지 높이") @RequestParam(required = false) Integer rows,
            @Parameter(description = "출력 이미지 너비") @RequestParam(required = false) Integer columns
    ) {
        setTenantContext(tenantId);
        List<Integer> frameNumbers = parseFrameList(frameList);
        log.info("Internal API: Render frames - tenantId={}, instanceId={}, frames={}", tenantId, instanceId, frameNumbers);

        Instance instance;
        try {
            instance = instanceService.findById(instanceId);
        } catch (Exception e) {
            throw new BusinessException(MiniPacsErrorCode.INSTANCE_NOT_FOUND);
        }

        int totalFrames = instance.getNumberOfFrames() != null ? instance.getNumberOfFrames() : 1;
        validateFrameNumbers(frameNumbers, totalFrames);

        String storagePath = instance.getStoragePath();

        // 단일 프레임인 경우
        if (frameNumbers.size() == 1) {
            int frameNumber = frameNumbers.get(0);
            StreamingResponseBody responseBody = outputStream -> {
                dicomRenderingService.renderToStreamWithOptions(storagePath, frameNumber, quality, rows, columns, outputStream);
            };

            MediaType contentType = quality < 100 ? MediaType.IMAGE_JPEG : MediaType.IMAGE_PNG;
            return ResponseEntity.ok()
                    .contentType(contentType)
                    .cacheControl(CacheControl.maxAge(Duration.ofHours(1)))
                    .body(responseBody);
        }

        // 다중 프레임: multipart/related 응답
        String boundary = "internal_rendered_boundary_" + System.currentTimeMillis();
        String imageType = quality < 100 ? "image/jpeg" : "image/png";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(
                "multipart/related; type=\"" + imageType + "\"; boundary=" + boundary));
        headers.setCacheControl(CacheControl.maxAge(Duration.ofHours(1)));

        StreamingResponseBody responseBody = outputStream -> {
            for (int frameNumber : frameNumbers) {
                String partHeader = "--" + boundary + "\r\n"
                        + "Content-Type: " + imageType + "\r\n"
                        + "X-Frame-Number: " + frameNumber + "\r\n"
                        + "\r\n";
                outputStream.write(partHeader.getBytes(StandardCharsets.UTF_8));

                dicomRenderingService.renderToStreamWithOptions(storagePath, frameNumber, quality, rows, columns, outputStream);

                outputStream.write("\r\n".getBytes(StandardCharsets.UTF_8));
            }
            String endBoundary = "--" + boundary + "--\r\n";
            outputStream.write(endBoundary.getBytes(StandardCharsets.UTF_8));
            outputStream.flush();
        };

        return new ResponseEntity<>(responseBody, headers, HttpStatus.OK);
    }

    // ========================================================================
    // 3. Batch 프레임 조회 API
    // ========================================================================

    /**
     * Batch API: 여러 Instance의 프레임 일괄 조회
     *
     * <p>여러 Instance에서 지정된 프레임들을 한 번의 요청으로 조회합니다.
     */
    @PostMapping(value = "/{tenantId}/internal/batch/frames")
    @Operation(summary = "Batch API: 다중 Instance 프레임 조회", description = "여러 Instance의 프레임을 일괄 조회")
    public ResponseEntity<StreamingResponseBody> batchFrames(
            @Parameter(description = "테넌트 ID") @PathVariable Long tenantId,
            @RequestBody @Validated BatchFrameRequest request
    ) {
        setTenantContext(tenantId);
        log.info("Batch API: tenantId={}, requests={}, format={}",
                tenantId, request.getRequests().size(), request.getFormat());

        String boundary = "batch_boundary_" + System.currentTimeMillis();
        String imageType = "rendered".equalsIgnoreCase(request.getFormat()) ? "image/jpeg" : "application/octet-stream";
        int quality = request.getQuality() != null ? request.getQuality() : 85;

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(
                "multipart/related; type=\"" + imageType + "\"; boundary=" + boundary));
        headers.setCacheControl(CacheControl.maxAge(Duration.ofHours(1)));

        StreamingResponseBody responseBody = outputStream -> {
            for (BatchFrameRequest.FrameRequestItem item : request.getRequests()) {
                Instance instance;
                try {
                    instance = instanceService.findById(item.getInstanceId());
                } catch (Exception e) {
                    log.warn("Batch API: Instance not found - instanceId={}", item.getInstanceId());
                    continue;
                }

                String storagePath = instance.getStoragePath();
                int totalFrames = instance.getNumberOfFrames() != null ? instance.getNumberOfFrames() : 1;

                for (Integer frameNumber : item.getFrames()) {
                    if (frameNumber < 1 || frameNumber > totalFrames) {
                        log.warn("Batch API: Invalid frame - instanceId={}, frame={}, total={}",
                                item.getInstanceId(), frameNumber, totalFrames);
                        continue;
                    }

                    String partHeader = "--" + boundary + "\r\n"
                            + "Content-Type: " + imageType + "\r\n"
                            + "X-Instance-Id: " + item.getInstanceId() + "\r\n"
                            + "X-Frame-Number: " + frameNumber + "\r\n"
                            + "\r\n";
                    outputStream.write(partHeader.getBytes(StandardCharsets.UTF_8));

                    if ("rendered".equalsIgnoreCase(request.getFormat())) {
                        dicomRenderingService.renderToStreamWithOptions(
                                storagePath, frameNumber, quality,
                                request.getResolution(), request.getResolution(), outputStream);
                    } else {
                        // bulkdata: raw pixel data
                        byte[] pixelData = dicomRenderingService.extractFramePixelData(storagePath, frameNumber);
                        outputStream.write(pixelData);
                    }

                    outputStream.write("\r\n".getBytes(StandardCharsets.UTF_8));
                }
            }

            String endBoundary = "--" + boundary + "--\r\n";
            outputStream.write(endBoundary.getBytes(StandardCharsets.UTF_8));
            outputStream.flush();

            log.info("Batch API: Completed - tenantId={}, totalRequests={}", tenantId, request.getRequests().size());
        };

        return new ResponseEntity<>(responseBody, headers, HttpStatus.OK);
    }

    // ========================================================================
    // Helper Methods
    // ========================================================================

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
     * frameList 문자열 파싱
     */
    private List<Integer> parseFrameList(String frameList) {
        try {
            return Arrays.stream(frameList.split(","))
                    .map(String::trim)
                    .map(Integer::parseInt)
                    .distinct()
                    .collect(Collectors.toList());
        } catch (NumberFormatException e) {
            throw new BusinessException(MiniPacsErrorCode.INVALID_FRAME_NUMBER,
                    "Invalid frame list format: " + frameList);
        }
    }

    /**
     * 프레임 번호 목록 유효성 검증
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
     * Instance → DICOM JSON 변환
     */
    private Map<String, Object> instanceToDicomJson(Instance instance, String studyUID, String seriesUID) {
        Map<String, Object> json = new LinkedHashMap<>();

        json.put("0020000D", createDicomValue("UI", studyUID));
        json.put("0020000E", createDicomValue("UI", seriesUID));
        json.put("00080018", createDicomValue("UI", instance.getSopInstanceUid()));

        if (instance.getSopClassUid() != null) {
            json.put("00080016", createDicomValue("UI", instance.getSopClassUid()));
        }
        if (instance.getInstanceNumber() != null) {
            json.put("00200013", createDicomValue("IS", String.valueOf(instance.getInstanceNumber())));
        }
        if (instance.getImageRows() != null) {
            json.put("00280010", createDicomValue("US", instance.getImageRows()));
        }
        if (instance.getImageColumns() != null) {
            json.put("00280011", createDicomValue("US", instance.getImageColumns()));
        }
        if (instance.getNumberOfFrames() != null) {
            json.put("00280008", createDicomValue("IS", String.valueOf(instance.getNumberOfFrames())));
        }
        if (instance.getSamplesPerPixel() != null) {
            json.put("00280002", createDicomValue("US", instance.getSamplesPerPixel()));
        }
        if (instance.getPhotometricInterpretation() != null) {
            json.put("00280004", createDicomValue("CS", instance.getPhotometricInterpretation()));
        }
        if (instance.getBitsAllocated() != null) {
            json.put("00280100", createDicomValue("US", instance.getBitsAllocated()));
        }
        if (instance.getBitsStored() != null) {
            json.put("00280101", createDicomValue("US", instance.getBitsStored()));
        }

        String transferSyntax = instance.getTransferSyntaxUid();
        if (transferSyntax == null || transferSyntax.isEmpty()) {
            transferSyntax = "1.2.840.10008.1.2.1";
        }
        json.put("00020010", createDicomValue("UI", transferSyntax));

        return json;
    }

    /**
     * Instance → 내부 JSON 변환 (DB ID 포함)
     */
    private Map<String, Object> instanceToInternalJson(Instance instance) {
        Map<String, Object> json = new LinkedHashMap<>();

        // DB 식별자
        json.put("id", instance.getId());
        json.put("uuid", instance.getUuid());

        // DICOM UID
        json.put("sopInstanceUid", instance.getSopInstanceUid());
        json.put("sopClassUid", instance.getSopClassUid());

        // 부모 정보
        Series series = instance.getSeries();
        json.put("seriesId", series.getId());
        json.put("seriesInstanceUid", series.getSeriesInstanceUid());

        Study study = series.getStudy();
        json.put("studyId", study.getId());
        json.put("studyInstanceUid", study.getStudyInstanceUid());

        // 이미지 정보
        json.put("instanceNumber", instance.getInstanceNumber());
        json.put("imageRows", instance.getImageRows());
        json.put("imageColumns", instance.getImageColumns());
        json.put("numberOfFrames", instance.getNumberOfFrames());
        json.put("bitsAllocated", instance.getBitsAllocated());
        json.put("bitsStored", instance.getBitsStored());
        json.put("transferSyntaxUid", instance.getTransferSyntaxUid());
        json.put("photometricInterpretation", instance.getPhotometricInterpretation());

        // 상태 정보
        json.put("transcodingStatus", instance.getTranscodingStatus());
        json.put("thumbnailPath", instance.getThumbnailPath());
        json.put("prerenderedBasePath", instance.getPrerenderedBasePath());

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
}
