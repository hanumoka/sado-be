package com.hanumoka.sado.minipacs.controller;

import com.hanumoka.sado.common.dto.ApiResponse;
import com.hanumoka.sado.minipacs.domain.entity.FileAsset;
import com.hanumoka.sado.minipacs.domain.enums.FileCategory;
import com.hanumoka.sado.minipacs.domain.enums.ReferenceType;
import com.hanumoka.sado.minipacs.domain.service.FileAssetService;
import com.hanumoka.sado.minipacs.dto.FileAssetResponse;
import com.hanumoka.sado.minipacs.storage.dto.FileAccessResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

/**
 * FileAsset REST API Controller
 *
 * <p>DICOM 외 부수 파일(AI 결과, 리포트, Export 등) 관리 API
 *
 * <p>주요 엔드포인트:
 * <ul>
 *   <li>POST /api/v1/files/upload - 파일 업로드</li>
 *   <li>GET /api/v1/files/{id} - 파일 메타데이터 조회</li>
 *   <li>GET /api/v1/files/{id}/download - 파일 다운로드</li>
 *   <li>GET /api/v1/files/{id}/presigned-url - Pre-signed URL 발급</li>
 *   <li>GET /api/v1/files - 참조 엔티티별 파일 목록 조회</li>
 *   <li>DELETE /api/v1/files/{id} - 파일 삭제 (Soft Delete)</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/files")
@RequiredArgsConstructor
@Slf4j
@Validated
@Tag(name = "FileAsset", description = "부수 파일 관리 API (AI 결과, 리포트, Export 등)")
public class FileAssetController {

    private final FileAssetService fileAssetService;

    /**
     * 파일 업로드
     *
     * @param file 업로드할 파일
     * @param category 파일 카테고리 (AI_RESULT, CLINICAL_DOC, SYSTEM, EXPORT)
     * @param referenceType 참조 엔티티 타입 (INSTANCE, STUDY, SERIES, PATIENT, AI_ANALYSIS)
     * @param referenceId 참조 엔티티 ID
     * @return 저장된 FileAsset 정보
     */
    @PostMapping("/upload")
    @Operation(summary = "파일 업로드", description = "부수 파일을 S3에 업로드하고 메타데이터를 저장합니다.")
    public ApiResponse<FileAssetResponse> uploadFile(
            @Parameter(description = "업로드할 파일") @RequestParam("file") MultipartFile file,
            @Parameter(description = "파일 카테고리") @RequestParam("category") FileCategory category,
            @Parameter(description = "참조 엔티티 타입") @RequestParam("referenceType") ReferenceType referenceType,
            @Parameter(description = "참조 엔티티 ID") @RequestParam("referenceId") Long referenceId
    ) {
        log.info("File upload request: category={}, referenceType={}, referenceId={}, filename={}",
                category, referenceType, referenceId, file.getOriginalFilename());

        FileAsset saved = fileAssetService.uploadFile(file, category, referenceType, referenceId);

        return ApiResponse.success(FileAssetResponse.from(saved));
    }

    /**
     * 파일 메타데이터 조회
     *
     * @param id FileAsset PK
     * @return FileAsset 메타데이터
     */
    @GetMapping("/{id}")
    @Operation(summary = "파일 메타데이터 조회", description = "파일의 메타데이터를 조회합니다.")
    public ApiResponse<FileAssetResponse> getFileAsset(
            @Parameter(description = "FileAsset ID") @PathVariable Long id
    ) {
        log.info("Get file metadata: id={}", id);

        FileAsset fileAsset = fileAssetService.findById(id);

        return ApiResponse.success(FileAssetResponse.from(fileAsset));
    }

    /**
     * 파일 다운로드 (Backend Proxy)
     *
     * <p>Backend에서 S3 파일을 스트리밍하여 클라이언트에 전달합니다.
     *
     * @param id FileAsset PK
     * @return 파일 스트림
     */
    @GetMapping("/{id}/download")
    @Operation(summary = "파일 다운로드", description = "파일을 다운로드합니다 (Backend Proxy).")
    public ResponseEntity<InputStreamResource> downloadFile(
            @Parameter(description = "FileAsset ID") @PathVariable Long id
    ) {
        log.info("File download request: id={}", id);

        FileAsset fileAsset = fileAssetService.findById(id);
        InputStream inputStream = fileAssetService.downloadFile(id);

        // Content-Disposition 헤더 설정 (한글 파일명 지원)
        String encodedFilename = URLEncoder.encode(fileAsset.getFileName(), StandardCharsets.UTF_8)
                .replace("+", "%20");

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename*=UTF-8''" + encodedFilename)
                .contentType(MediaType.parseMediaType(
                        fileAsset.getMimeType() != null ? fileAsset.getMimeType() : "application/octet-stream"))
                .contentLength(fileAsset.getFileSize())
                .body(new InputStreamResource(inputStream));
    }

    /**
     * Pre-signed URL 발급
     *
     * <p>클라이언트가 S3에 직접 접근할 수 있는 임시 URL을 발급합니다.
     *
     * @param id FileAsset PK
     * @param expiresInMinutes URL 유효 시간 (분, 기본 60분)
     * @return Pre-signed URL
     */
    @GetMapping("/{id}/presigned-url")
    @Operation(summary = "Pre-signed URL 발급", description = "S3 직접 접근용 임시 URL을 발급합니다.")
    public ApiResponse<FileAccessResponse> getPresignedUrl(
            @Parameter(description = "FileAsset ID") @PathVariable Long id,
            @Parameter(description = "URL 유효 시간 (분)") @RequestParam(defaultValue = "60") int expiresInMinutes
    ) {
        log.info("Pre-signed URL request: id={}, expiresInMinutes={}", id, expiresInMinutes);

        Duration validity = Duration.ofMinutes(expiresInMinutes);
        String presignedUrl = fileAssetService.getPresignedUrl(id, validity);

        return ApiResponse.success(FileAccessResponse.presignedUrl(presignedUrl, validity));
    }

    /**
     * 참조 엔티티별 파일 목록 조회
     *
     * @param referenceType 참조 엔티티 타입
     * @param referenceId 참조 엔티티 ID
     * @param category 파일 카테고리 (선택)
     * @return 파일 목록
     */
    @GetMapping
    @Operation(summary = "파일 목록 조회", description = "참조 엔티티에 연결된 파일 목록을 조회합니다.")
    public ApiResponse<List<FileAssetResponse>> listFiles(
            @Parameter(description = "참조 엔티티 타입") @RequestParam ReferenceType referenceType,
            @Parameter(description = "참조 엔티티 ID") @RequestParam Long referenceId,
            @Parameter(description = "파일 카테고리 (선택)") @RequestParam(required = false) FileCategory category
    ) {
        log.info("List files request: referenceType={}, referenceId={}, category={}",
                referenceType, referenceId, category);

        List<FileAsset> files;

        if (category != null) {
            files = fileAssetService.findByReferenceAndCategory(referenceType, referenceId, category);
        } else {
            files = fileAssetService.findByReference(referenceType, referenceId);
        }

        List<FileAssetResponse> response = files.stream()
                .map(FileAssetResponse::from)
                .toList();

        return ApiResponse.success(response);
    }

    /**
     * 파일 삭제 (Soft Delete)
     *
     * <p>파일 상태를 DELETED로 변경합니다.
     * <p>물리 파일 삭제는 FileAssetCleanupScheduler에서 처리합니다.
     *
     * @param id FileAsset PK
     * @return 성공 응답
     */
    @DeleteMapping("/{id}")
    @Operation(summary = "파일 삭제", description = "파일을 삭제합니다 (Soft Delete).")
    public ApiResponse<Void> deleteFile(
            @Parameter(description = "FileAsset ID") @PathVariable Long id
    ) {
        log.info("File delete request: id={}", id);

        fileAssetService.deleteFile(id);

        return ApiResponse.success(null);
    }
}
