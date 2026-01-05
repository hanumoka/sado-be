package com.hanumoka.sado.minipacs.controller;

import com.hanumoka.sado.common.exception.BusinessException;
import com.hanumoka.sado.minipacs.code.MiniPacsErrorCode;
import com.hanumoka.sado.minipacs.domain.entity.FileAccessLog;
import com.hanumoka.sado.minipacs.domain.entity.FileAsset;
import com.hanumoka.sado.minipacs.domain.repository.FileAssetRepository;
import com.hanumoka.sado.minipacs.domain.service.FileAccessLogService;
import com.hanumoka.sado.minipacs.storage.service.DicomStorageService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.InputStream;

/**
 * 파일 Proxy Controller
 *
 * <p>Backend Proxy 방식의 파일 다운로드 엔드포인트
 *
 * <p>특징:
 * <ul>
 *   <li>Backend를 통한 파일 다운로드 (Pre-signed URL 대안)</li>
 *   <li>완벽한 Audit Log 기록 (HIPAA/GDPR 준수)</li>
 *   <li>Chunked Transfer Encoding (대용량 파일 스트리밍)</li>
 *   <li>권한 검증 (Tenant ID 매칭)</li>
 * </ul>
 *
 * <p>현재 상태 (Week 8 POC):
 * <ul>
 *   <li>userId: "admin" (하드코딩, Week 9+ JWT 추출)</li>
 *   <li>tenantId: 현재 Hibernate Filter 컨텍스트</li>
 *   <li>권한 검증: FileAsset 존재 확인 (Tenant 격리는 Hibernate Filter 자동 처리)</li>
 * </ul>
 *
 * <p>Week 9+ 변경사항:
 * <ul>
 *   <li>@AuthenticationPrincipal Jwt jwt 추가</li>
 *   <li>jwt.getClaim("sub")로 실제 userId 추출</li>
 *   <li>jwt.getClaim("tenant_id")로 실제 tenantId 추출</li>
 *   <li>명시적 Tenant 권한 검증 추가</li>
 * </ul>
 */
@Slf4j
@RestController
@RequestMapping("/api/files")
@RequiredArgsConstructor
public class FileProxyController {

    private final DicomStorageService storageService;
    private final FileAccessLogService auditLogService;
    private final FileAssetRepository fileAssetRepository;

    /**
     * 파일 다운로드 Proxy 엔드포인트
     *
     * <p>흐름:
     * <ol>
     *   <li>FileAsset 조회 및 권한 검증</li>
     *   <li>Audit Log 기록 (DOWNLOAD_START)</li>
     *   <li>SeaweedFS에서 파일 스트림 조회</li>
     *   <li>Chunked Transfer Encoding으로 클라이언트에 스트리밍 (8KB chunks)</li>
     *   <li>Audit Log 기록 (DOWNLOAD_SUCCESS or DOWNLOAD_FAILED)</li>
     * </ol>
     *
     * <p>보안:
     * <ul>
     *   <li>Tenant 격리: Hibernate Filter 자동 적용 (POC)</li>
     *   <li>Audit Log: 모든 접근 기록 (User, IP, User-Agent, 시간, 결과)</li>
     *   <li>권한 검증: FileAsset 존재 확인 = Tenant 소유 확인</li>
     * </ul>
     *
     * @param fileId  FileAsset ID
     * @param request HTTP 요청 (IP, User-Agent 추출용)
     * @return 파일 스트림 (application/dicom or application/octet-stream)
     * @throws BusinessException FILE_NOT_FOUND - 파일이 존재하지 않는 경우
     * @throws BusinessException STORAGE_DOWNLOAD_FAILED - SeaweedFS 다운로드 실패
     */
    @GetMapping("/{fileId}/proxy")
    public ResponseEntity<StreamingResponseBody> downloadFile(
        @PathVariable Long fileId,
        HttpServletRequest request
        // TODO(Week 9+): @AuthenticationPrincipal Jwt jwt
    ) {
        // POC: 하드코딩된 userId (Week 9+: jwt.getClaim("sub"))
        String userId = "admin";
        String ipAddress = request.getRemoteAddr();
        String userAgent = request.getHeader("User-Agent");

        log.info("File proxy download request: fileId={}, userId={}, ip={}", fileId, userId, ipAddress);

        // 1. FileAsset 조회 및 권한 검증
        // Hibernate Filter가 자동으로 tenant_id 조건 추가 → Tenant 격리 보장
        FileAsset fileAsset = fileAssetRepository.findById(fileId)
            .orElseThrow(() -> {
                // 권한 없거나 존재하지 않는 파일 (Hibernate Filter로 구분 불가능)
                auditLogService.logAccessDenied(
                    FileAccessLog.ResourceType.FILE_ASSET,
                    fileId,
                    userId,
                    ipAddress,
                    userAgent,
                    "FileAsset not found or access denied (tenant mismatch)"
                );

                return new BusinessException(
                    MiniPacsErrorCode.FILE_NOT_FOUND,
                    "파일을 찾을 수 없거나 접근 권한이 없습니다."
                );
            });

        String s3Key = fileAsset.getStoragePath();
        String contentType = determineContentType(fileAsset);

        log.debug("FileAsset found: id={}, s3Key={}, contentType={}", fileId, s3Key, contentType);

        // 2. Audit Log 기록 (다운로드 시작)
        auditLogService.logDownloadStart(
            FileAccessLog.ResourceType.FILE_ASSET,
            fileId,
            userId,
            ipAddress,
            userAgent
        );

        // 3. Streaming Response Body (8KB chunks)
        long startTime = System.currentTimeMillis();

        StreamingResponseBody stream = outputStream -> {
            long totalBytes = 0;
            boolean success = false;

            try (InputStream inputStream = storageService.downloadFileStream(s3Key)) {
                byte[] buffer = new byte[8192]; // 8KB 버퍼
                int bytesRead;

                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, bytesRead);
                    outputStream.flush();
                    totalBytes += bytesRead;
                }

                success = true;

                // 4. Audit Log 기록 (다운로드 성공)
                long durationMs = System.currentTimeMillis() - startTime;
                auditLogService.logDownloadSuccess(
                    FileAccessLog.ResourceType.FILE_ASSET,
                    fileId,
                    userId,
                    ipAddress,
                    userAgent,
                    totalBytes,
                    durationMs
                );

                log.info("File proxy download completed: fileId={}, bytes={}, duration={}ms",
                        fileId, totalBytes, durationMs);

            } catch (Exception e) {
                // 5. Audit Log 기록 (다운로드 실패)
                auditLogService.logDownloadFailure(
                    FileAccessLog.ResourceType.FILE_ASSET,
                    fileId,
                    userId,
                    ipAddress,
                    userAgent,
                    e.getMessage()
                );

                log.error("File proxy download failed: fileId={}, s3Key={}, bytes={}", fileId, s3Key, totalBytes, e);

                throw new BusinessException(
                    MiniPacsErrorCode.STORAGE_DOWNLOAD_FAILED,
                    "파일 다운로드 중 오류가 발생했습니다."
                );
            }
        };

        // 6. HTTP Response Headers
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_TYPE, contentType)
            .header(HttpHeaders.CONTENT_DISPOSITION, buildContentDisposition(fileAsset))
            .header(HttpHeaders.CACHE_CONTROL, "no-cache, no-store, must-revalidate")
            .header(HttpHeaders.PRAGMA, "no-cache")
            .header(HttpHeaders.EXPIRES, "0")
            .body(stream);
    }

    /**
     * Content-Type 결정
     *
     * @param fileAsset FileAsset
     * @return Content-Type (예: application/dicom, image/png)
     */
    private String determineContentType(FileAsset fileAsset) {
        return switch (fileAsset.getCategory()) {
            case DICOM -> "application/dicom";
            case AI_RESULT -> {
                // AI 결과 파일은 확장자로 판단
                String fileName = fileAsset.getFileName();
                if (fileName != null && fileName.toLowerCase().endsWith(".png")) {
                    yield "image/png";
                } else if (fileName != null && fileName.toLowerCase().endsWith(".json")) {
                    yield "application/json";
                } else if (fileName != null && fileName.toLowerCase().endsWith(".pdf")) {
                    yield "application/pdf";
                }
                yield "application/octet-stream";
            }
            case CLINICAL_DOC -> "application/pdf";
            case SYSTEM -> {
                // SYSTEM 파일은 확장자로 판단 (썸네일, 비디오 등)
                String fileName = fileAsset.getFileName();
                if (fileName != null && fileName.toLowerCase().endsWith(".jpg") ||
                    fileName != null && fileName.toLowerCase().endsWith(".jpeg")) {
                    yield "image/jpeg";
                } else if (fileName != null && fileName.toLowerCase().endsWith(".mp4")) {
                    yield "video/mp4";
                }
                yield "application/octet-stream";
            }
            case EXPORT -> "application/zip";
            default -> "application/octet-stream";
        };
    }

    /**
     * Content-Disposition 헤더 생성
     *
     * @param fileAsset FileAsset
     * @return Content-Disposition 값
     */
    private String buildContentDisposition(FileAsset fileAsset) {
        // DICOM은 inline (브라우저에서 바로 열기), 나머지는 attachment (다운로드)
        String dispositionType = (fileAsset.getCategory() == com.hanumoka.sado.minipacs.domain.enums.FileCategory.DICOM)
            ? "inline"
            : "attachment";

        String fileName = fileAsset.getFileName() != null
            ? fileAsset.getFileName()
            : "file_" + fileAsset.getId();

        return String.format("%s; filename=\"%s\"", dispositionType, fileName);
    }
}
