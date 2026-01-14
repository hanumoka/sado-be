package com.hanumoka.sado.minipacs.controller;

import com.hanumoka.sado.common.dto.ApiResponse;
import com.hanumoka.sado.minipacs.dto.request.seaweedfs.CreateVolumeRequest;
import com.hanumoka.sado.minipacs.dto.response.seaweedfs.ClusterStatusResponse;
import com.hanumoka.sado.minipacs.dto.response.seaweedfs.FilerEntryResponse;
import com.hanumoka.sado.minipacs.dto.response.seaweedfs.VolumeInfoResponse;
import com.hanumoka.sado.minipacs.infrastructure.service.SeaweedFSAdminService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * SeaweedFS Admin Controller
 *
 * <p>SeaweedFS 클러스터 관리 API
 *
 * <p>주요 기능:
 * <ul>
 *   <li>Volume 조회/생성/삭제</li>
 *   <li>Cluster 상태 모니터링</li>
 *   <li>Filer 디렉토리 탐색</li>
 * </ul>
 *
 * <p>접근 권한: ADMIN 역할 필요 (Week 9+ 적용)
 */
@Tag(name = "SeaweedFS Admin", description = "SeaweedFS 클러스터 관리 API")
@Slf4j
@RestController
@RequestMapping("/api/admin/seaweedfs")
@RequiredArgsConstructor
public class SeaweedFSAdminController {

    private final SeaweedFSAdminService seaweedFSAdminService;

    /**
     * 모든 Volume 조회
     *
     * <p>GET /api/admin/seaweedfs/volumes
     *
     * @return Volume 목록
     */
    @Operation(
        summary = "Volume 목록 조회",
        description = "SeaweedFS의 모든 Volume 정보를 조회합니다. Volume ID, 크기, Collection, Replication 정보 포함."
    )
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "503", description = "SeaweedFS 서버 접근 불가"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "응답 파싱 실패")
    })
    @GetMapping("/volumes")
    public ApiResponse<List<VolumeInfoResponse>> listVolumes() {
        log.info("GET /api/admin/seaweedfs/volumes");

        List<VolumeInfoResponse> volumes = seaweedFSAdminService.listVolumes();

        log.info("Volumes retrieved: count={}", volumes.size());
        return ApiResponse.success(volumes);
    }

    /**
     * Cluster 상태 조회
     *
     * <p>GET /api/admin/seaweedfs/cluster
     *
     * @return Cluster 상태
     */
    @Operation(
        summary = "Cluster 상태 조회",
        description = "SeaweedFS Cluster의 전체 상태를 조회합니다. Master, Volume Server, Filer 노드 정보 포함."
    )
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "503", description = "SeaweedFS 서버 접근 불가")
    })
    @GetMapping("/cluster")
    public ApiResponse<ClusterStatusResponse> getClusterStatus() {
        log.info("GET /api/admin/seaweedfs/cluster");

        ClusterStatusResponse status = seaweedFSAdminService.getClusterStatus();

        log.info("Cluster status retrieved: health={}", status.getHealth());
        return ApiResponse.success(status);
    }

    /**
     * Filer 디렉토리 목록 조회
     *
     * <p>GET /api/admin/seaweedfs/filer/ls?path=/minipacs
     *
     * @param path 디렉토리 경로 (기본값: "/")
     * @return 파일/디렉토리 목록
     */
    @Operation(
        summary = "Filer 디렉토리 탐색",
        description = "SeaweedFS Filer의 특정 디렉토리 내용을 조회합니다. 파일 및 하위 디렉토리 목록 반환."
    )
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "디렉토리를 찾을 수 없음"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "503", description = "SeaweedFS 서버 접근 불가")
    })
    @GetMapping("/filer/ls")
    public ApiResponse<List<FilerEntryResponse>> listFilerDirectory(
        @RequestParam(defaultValue = "/") String path
    ) {
        log.info("GET /api/admin/seaweedfs/filer/ls?path={}", path);

        List<FilerEntryResponse> entries = seaweedFSAdminService.listFilerDirectory(path);

        log.info("Filer directory listed: path={}, count={}", path, entries.size());
        return ApiResponse.success(entries);
    }

    /**
     * Volume 생성
     *
     * <p>POST /api/admin/seaweedfs/volumes
     *
     * @param request Volume 생성 요청
     * @return 생성 결과 메시지
     */
    @Operation(
        summary = "Volume 생성",
        description = "새로운 Volume을 생성합니다. Collection, Replication, TTL 등을 지정할 수 있습니다."
    )
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "생성 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "잘못된 요청 (파라미터 검증 실패)"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "Volume 생성 실패"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "503", description = "SeaweedFS 서버 접근 불가")
    })
    @PostMapping("/volumes")
    public ApiResponse<String> createVolumes(
        @Valid @RequestBody CreateVolumeRequest request
    ) {
        log.info("POST /api/admin/seaweedfs/volumes: count={}, collection={}, replication={}",
                request.getCount(), request.getCollection(), request.getReplication());

        String result = seaweedFSAdminService.createVolumes(request);

        log.info("Volumes created successfully");
        return ApiResponse.success(result);
    }

    /**
     * Volume 삭제
     *
     * <p>DELETE /api/admin/seaweedfs/volumes/{volumeId}
     *
     * <p>주의: Volume은 비어있어야 삭제 가능
     *
     * @param volumeId Volume ID
     * @return 삭제 결과 메시지
     */
    @Operation(
        summary = "Volume 삭제",
        description = "특정 Volume을 삭제합니다. Volume이 비어있어야 삭제 가능합니다."
    )
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "삭제 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Volume이 비어있지 않음"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Volume을 찾을 수 없음"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "503", description = "SeaweedFS 서버 접근 불가")
    })
    @DeleteMapping("/volumes/{volumeId}")
    public ApiResponse<String> deleteVolume(
        @PathVariable Long volumeId
    ) {
        log.warn("DELETE /api/admin/seaweedfs/volumes/{}: Deleting volume", volumeId);

        seaweedFSAdminService.deleteVolume(volumeId);

        log.info("Volume deleted successfully: volumeId={}", volumeId);
        return ApiResponse.success("Volume " + volumeId + " deleted successfully");
    }

    // ============================================================
    // Filer 파일 삭제/다운로드
    // ============================================================

    /**
     * Filer 파일 삭제
     *
     * <p>DELETE /api/admin/seaweedfs/filer/file?path=/path/to/file
     *
     * @param path 삭제할 파일 경로
     * @return 삭제 결과 메시지
     */
    @Operation(
        summary = "Filer 파일 삭제",
        description = "SeaweedFS Filer에서 파일을 삭제합니다. 주의: 되돌릴 수 없습니다."
    )
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "삭제 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "파일을 찾을 수 없음"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "삭제 실패"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "503", description = "SeaweedFS 서버 접근 불가")
    })
    @DeleteMapping("/filer/file")
    public ApiResponse<String> deleteFilerFile(
        @RequestParam String path
    ) {
        log.warn("DELETE /api/admin/seaweedfs/filer/file?path={}: Deleting file", path);

        seaweedFSAdminService.deleteFilerFile(path);

        log.info("File deleted successfully: path={}", path);
        return ApiResponse.success("File deleted: " + path);
    }

    /**
     * Filer 파일 다운로드 URL 조회
     *
     * <p>GET /api/admin/seaweedfs/filer/download-url?path=/path/to/file
     *
     * @param path 파일 경로
     * @return 다운로드 URL
     */
    @Operation(
        summary = "Filer 파일 다운로드 URL 조회",
        description = "SeaweedFS Filer의 파일 다운로드 URL을 반환합니다. Frontend에서 직접 다운로드에 사용합니다."
    )
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "URL 생성 성공")
    })
    @GetMapping("/filer/download-url")
    public ApiResponse<String> getFilerFileDownloadUrl(
        @RequestParam String path
    ) {
        log.info("GET /api/admin/seaweedfs/filer/download-url?path={}", path);

        String downloadUrl = seaweedFSAdminService.getFilerFileDownloadUrl(path);

        return ApiResponse.success(downloadUrl);
    }
}
