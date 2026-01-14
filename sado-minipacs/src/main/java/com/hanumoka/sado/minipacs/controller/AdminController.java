package com.hanumoka.sado.minipacs.controller;

import com.hanumoka.sado.common.dto.ApiResponse;
import com.hanumoka.sado.minipacs.domain.entity.FileAsset;
import com.hanumoka.sado.minipacs.domain.entity.Instance;
import com.hanumoka.sado.minipacs.domain.entity.StorageMetricsSnapshot;
import com.hanumoka.sado.minipacs.domain.entity.TierTransitionLog;
import com.hanumoka.sado.minipacs.domain.entity.TieringPolicy;
import com.hanumoka.sado.minipacs.domain.enums.FileCategory;
import com.hanumoka.sado.minipacs.domain.enums.FileStatus;
import com.hanumoka.sado.minipacs.domain.repository.FileAssetRepository;
import com.hanumoka.sado.minipacs.domain.repository.InstanceRepository;
import com.hanumoka.sado.minipacs.domain.repository.PatientRepository;
import com.hanumoka.sado.minipacs.domain.repository.SeriesRepository;
import com.hanumoka.sado.minipacs.domain.repository.StudyRepository;
import com.hanumoka.sado.minipacs.domain.repository.TierTransitionLogRepository;
import com.hanumoka.sado.minipacs.domain.service.StorageMetricsService;
import com.hanumoka.sado.minipacs.domain.service.TieringManagementService;
import com.hanumoka.sado.minipacs.dto.request.TierTransitionRequest;
import com.hanumoka.sado.minipacs.dto.request.UpdateTieringPolicyRequest;
import com.hanumoka.sado.minipacs.dto.response.admin.CategoryStorageMetrics;
import com.hanumoka.sado.minipacs.dto.response.admin.DashboardSummaryResponse;
import com.hanumoka.sado.minipacs.dto.response.admin.FileAssetSummary;
import com.hanumoka.sado.minipacs.dto.response.admin.SeaweedFSCapacityResponse;
import com.hanumoka.sado.minipacs.dto.response.admin.StorageMetricsTrendResponse;
import com.hanumoka.sado.minipacs.dto.response.admin.StorageSummary;
import com.hanumoka.sado.minipacs.dto.response.admin.TierDistribution;
import com.hanumoka.sado.minipacs.dto.response.admin.TenantStorageMetrics;
import com.hanumoka.sado.minipacs.dto.response.admin.TierTransitionHistoryResponse;
import com.hanumoka.sado.minipacs.dto.response.admin.TieringPolicies;
import com.hanumoka.sado.minipacs.dto.response.admin.MonitoringTasksResponse;
import com.hanumoka.sado.minipacs.dto.response.admin.UploadTask;
import com.hanumoka.sado.minipacs.dto.response.admin.RenderingTask;
import com.hanumoka.sado.minipacs.dto.response.admin.TaskSummary;
import com.hanumoka.sado.minipacs.dto.response.seaweedfs.ClusterStatusResponse;
import com.hanumoka.sado.minipacs.infrastructure.service.SeaweedFSAdminService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Admin REST API Controller
 *
 * Admin Dashboard 통계 및 관리 API
 */
@Tag(name = "Admin API", description = "관리자 전용 API - Dashboard, Storage Metrics, Tiering Management")
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@Slf4j
@Validated  // 메서드 파라미터 검증 활성화
public class AdminController {

    private final PatientRepository patientRepository;
    private final StudyRepository studyRepository;
    private final SeriesRepository seriesRepository;
    private final InstanceRepository instanceRepository;
    private final FileAssetRepository fileAssetRepository;
    private final TierTransitionLogRepository tierTransitionLogRepository;
    private final TieringManagementService tieringManagementService;
    private final StorageMetricsService storageMetricsService;
    private final SeaweedFSAdminService seaweedFSAdminService;
    private final com.hanumoka.sado.minipacs.config.AdminTieringProperties adminTieringProperties;

    /**
     * Dashboard 전체 통계 조회
     * GET /api/admin/dashboard/summary
     *
     * @return Dashboard 통계 (환자/Study/Series/Instance 수, 스토리지 사용량, Tier 분포)
     */
    @Operation(
        summary = "Admin Dashboard 전체 통계 조회",
        description = "환자/Study/Series/Instance 수, 스토리지 사용량 (HOT/WARM/COLD), Tier 분포를 조회합니다."
    )
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "권한 없음 (ADMIN 역할 필요)")
    })
    @GetMapping("/dashboard/summary")
    public ApiResponse<DashboardSummaryResponse> getDashboardSummary() {
        log.info("GET /api/admin/dashboard/summary");

        // 1. DICOM 엔티티 통계
        Long totalPatients = patientRepository.count();
        Long totalStudies = studyRepository.count();
        Long totalSeries = seriesRepository.count();
        Long totalInstances = instanceRepository.count();

        // 2. Storage Tier별 사용량
        Long hotSize = fileAssetRepository.sumFileSizeByStorageTier("HOT");
        Long warmSize = fileAssetRepository.sumFileSizeByStorageTier("WARM");
        Long coldSize = fileAssetRepository.sumFileSizeByStorageTier("COLD");
        Long totalSize = hotSize + warmSize + coldSize;

        // 3. Storage Summary 생성
        StorageSummary storageSummary = StorageSummary.builder()
                .totalSize(totalSize)
                .hotSize(hotSize)
                .warmSize(warmSize)
                .coldSize(coldSize)
                .build();

        // 4. Tier Distribution 생성
        TierDistribution tierDistribution = TierDistribution.builder()
                .hot(hotSize)
                .warm(warmSize)
                .cold(coldSize)
                .build();

        // 5. Dashboard Summary Response 생성
        DashboardSummaryResponse response = DashboardSummaryResponse.builder()
                .totalPatients(totalPatients)
                .totalStudies(totalStudies)
                .totalSeries(totalSeries)
                .totalInstances(totalInstances)
                .storageSummary(storageSummary)
                .tierDistribution(tierDistribution)
                .build();

        log.info("Dashboard summary: patients={}, studies={}, series={}, instances={}, totalStorage={}",
                totalPatients, totalStudies, totalSeries, totalInstances, totalSize);

        return ApiResponse.success(response);
    }

    /**
     * 스토리지 메트릭 조회
     * GET /api/admin/metrics/storage
     *
     * @return 스토리지 사용량 상세 정보
     */
    @Operation(
        summary = "스토리지 메트릭 조회",
        description = "HOT/WARM/COLD Storage Tier별 총 사용량을 조회합니다."
    )
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "권한 없음 (ADMIN 역할 필요)")
    })
    @GetMapping("/metrics/storage")
    public ApiResponse<StorageSummary> getStorageMetrics() {
        log.info("GET /api/admin/metrics/storage");

        Long hotSize = fileAssetRepository.sumFileSizeByStorageTier("HOT");
        Long warmSize = fileAssetRepository.sumFileSizeByStorageTier("WARM");
        Long coldSize = fileAssetRepository.sumFileSizeByStorageTier("COLD");
        Long totalSize = hotSize + warmSize + coldSize;

        StorageSummary storageSummary = StorageSummary.builder()
                .totalSize(totalSize)
                .hotSize(hotSize)
                .warmSize(warmSize)
                .coldSize(coldSize)
                .build();

        log.info("Storage metrics: total={}, hot={}, warm={}, cold={}",
                totalSize, hotSize, warmSize, coldSize);

        return ApiResponse.success(storageSummary);
    }

    /**
     * Tier 분포 조회
     * GET /api/admin/metrics/tier-distribution
     *
     * @return Tier별 파일 크기 분포
     */
    @Operation(
        summary = "Tier 분포 조회",
        description = "Storage Tier별 파일 크기 분포를 조회합니다."
    )
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "권한 없음 (ADMIN 역할 필요)")
    })
    @GetMapping("/metrics/tier-distribution")
    public ApiResponse<TierDistribution> getTierDistribution() {
        log.info("GET /api/admin/metrics/tier-distribution");

        Long hotSize = fileAssetRepository.sumFileSizeByStorageTier("HOT");
        Long warmSize = fileAssetRepository.sumFileSizeByStorageTier("WARM");
        Long coldSize = fileAssetRepository.sumFileSizeByStorageTier("COLD");

        TierDistribution tierDistribution = TierDistribution.builder()
                .hot(hotSize)
                .warm(warmSize)
                .cold(coldSize)
                .build();

        log.info("Tier distribution: hot={}, warm={}, cold={}",
                hotSize, warmSize, coldSize);

        return ApiResponse.success(tierDistribution);
    }

    /**
     * Tier별 파일 목록 페이징 조회
     * GET /api/admin/tiering/files?tier=HOT&page=0&size=20
     *
     * @param tier Storage Tier (HOT, WARM, COLD)
     * @param page 페이지 번호 (0부터 시작)
     * @param size 페이지 크기 (기본값: 20)
     * @return Tier별 파일 목록 (페이징)
     */
    @Operation(
        summary = "Tier별 파일 목록 페이징 조회",
        description = "특정 Storage Tier의 파일 목록을 페이징하여 조회합니다. 마지막 접근 시간 기준 내림차순으로 정렬됩니다."
    )
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "잘못된 요청 (tier 값이 HOT/WARM/COLD가 아님)"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "권한 없음 (ADMIN 역할 필요)")
    })
    @GetMapping("/tiering/files")
    public ApiResponse<Page<FileAssetSummary>> getTieringFiles(
            @RequestParam
            @Pattern(regexp = "^(HOT|WARM|COLD)$", message = "Tier must be HOT, WARM, or COLD")
            String tier,

            @RequestParam(defaultValue = "0")
            @Min(value = 0, message = "Page must be >= 0")
            int page,

            @RequestParam(defaultValue = "20")
            @Min(value = 1, message = "Size must be >= 1")
            @Max(value = 100, message = "Size must be <= 100")
            int size
    ) {
        log.info("GET /api/admin/tiering/files?tier={}&page={}&size={}", tier, page, size);

        // 1. Repository에서 페이징 조회
        Pageable pageable = PageRequest.of(page, size);
        Page<FileAsset> fileAssets = fileAssetRepository.findByStorageTierAndStatusOrderByLastAccessedAtDesc(
                tier, FileStatus.ACTIVE, pageable
        );

        // 2. Entity → DTO 변환
        Page<FileAssetSummary> summaries = fileAssets.map(file -> FileAssetSummary.builder()
                .id(file.getId())
                .storagePath(file.getStoragePath())  // S3 Key
                .fileCategory(file.getCategory().name())
                .storageTier(file.getStorageTier())
                .fileSize(file.getFileSize())
                .lastAccessedAt(file.getLastAccessedAt())
                .createdAt(file.getCreatedAt())
                .build()
        );

        log.info("Tier files: tier={}, total={}, page={}/{}",
                tier, summaries.getTotalElements(), page + 1, summaries.getTotalPages());

        return ApiResponse.success(summaries);
    }

    /**
     * Storage Tiering 정책 조회
     * GET /api/admin/tiering/policies
     *
     * @return Tier 전환 정책 정보
     */
    @Operation(
        summary = "Storage Tiering 정책 조회",
        description = "HOT→WARM, WARM→COLD 전환 기준 일수와 스케줄러 설정을 조회합니다. application.yml에서 설정값을 읽어옵니다."
    )
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "권한 없음 (ADMIN 역할 필요)")
    })
    @GetMapping("/tiering/policies")
    public ApiResponse<TieringPolicies> getTieringPolicies() {
        log.info("GET /api/admin/tiering/policies");

        // application.yml에서 설정값 읽어오기 (AdminTieringProperties)
        TieringPolicies policies = TieringPolicies.builder()
                .hotToWarmDays(adminTieringProperties.getHotToWarmDays())
                .warmToColdDays(adminTieringProperties.getWarmToColdDays())
                .schedulerEnabled(adminTieringProperties.isSchedulerEnabled())
                .hotToWarmSchedule(adminTieringProperties.getHotToWarmSchedule())
                .warmToColdSchedule(adminTieringProperties.getWarmToColdSchedule())
                .build();

        log.info("Tiering policies: HOT→WARM={} days, WARM→COLD={} days",
                policies.getHotToWarmDays(), policies.getWarmToColdDays());

        return ApiResponse.success(policies);
    }

    /**
     * 카테고리별 스토리지 사용량 메트릭 조회
     * GET /api/admin/metrics/storage-by-category
     *
     * @return 카테고리별 파일 개수 및 총 크기
     */
    @Operation(
        summary = "카테고리별 스토리지 사용량 메트릭 조회",
        description = "파일 카테고리(DICOM, AI_RESULT, CLINICAL_DOC, SYSTEM, EXPORT)별 파일 개수와 총 크기를 조회합니다."
    )
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "권한 없음 (ADMIN 역할 필요)")
    })
    @GetMapping("/metrics/storage-by-category")
    public ApiResponse<List<CategoryStorageMetrics>> getStorageByCategory() {
        log.info("GET /api/admin/metrics/storage-by-category");

        // 단일 GROUP BY 쿼리로 모든 카테고리 통계 조회 (N+1 문제 해결)
        List<CategoryStorageMetrics> metrics =
            fileAssetRepository.findStorageMetricsByAllCategories();

        log.info("Storage by category: total categories={}", metrics.size());

        // 각 카테고리별 상세 로깅
        metrics.forEach(metric ->
            log.debug("Category {}: count={}, size={}",
                metric.getCategory(), metric.getFileCount(), metric.getTotalSize())
        );

        return ApiResponse.success(metrics);
    }

    /**
     * 테넌트별 스토리지 사용량 메트릭 조회
     * GET /api/admin/metrics/storage-by-tenant
     *
     * <p>각 테넌트별로 원본 파일(DICOM)과 사전렌더링 파일(SYSTEM)의
     * 사용량을 구분하여 제공합니다.
     *
     * @return 테넌트별 스토리지 메트릭 목록
     */
    @Operation(
        summary = "테넌트별 스토리지 사용량 메트릭 조회",
        description = "테넌트별로 원본 파일(DICOM)과 사전렌더링 파일(SYSTEM)의 개수와 크기를 조회합니다."
    )
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "권한 없음 (ADMIN 역할 필요)")
    })
    @GetMapping("/metrics/storage-by-tenant")
    public ApiResponse<List<TenantStorageMetrics>> getStorageByTenant() {
        log.info("GET /api/admin/metrics/storage-by-tenant");

        // 테넌트/카테고리별 통계 조회
        List<Object[]> rawMetrics = fileAssetRepository.findStorageMetricsByTenantAndType();

        // tenantId 기준으로 그룹화하여 TenantStorageMetrics로 변환
        java.util.Map<Long, TenantStorageMetrics.TenantStorageMetricsBuilder> builderMap = new java.util.LinkedHashMap<>();

        for (Object[] row : rawMetrics) {
            Long tenantId = (Long) row[0];
            com.hanumoka.sado.minipacs.domain.enums.FileCategory category =
                (com.hanumoka.sado.minipacs.domain.enums.FileCategory) row[1];
            Long fileCount = (Long) row[2];
            Long totalSize = (Long) row[3];

            TenantStorageMetrics.TenantStorageMetricsBuilder builder =
                builderMap.computeIfAbsent(tenantId, id ->
                    TenantStorageMetrics.builder()
                        .tenantId(id)
                        .tenantName("Tenant " + id)  // TODO: Tenant 엔티티에서 이름 조회
                        .originalFileCount(0L)
                        .originalSize(0L)
                        .preRenderedFileCount(0L)
                        .preRenderedSize(0L)
                        .totalSize(0L)
                );

            // DICOM = 원본, SYSTEM = 사전렌더링
            if (category == com.hanumoka.sado.minipacs.domain.enums.FileCategory.DICOM) {
                // Builder에서 직접 접근 불가하므로 임시 변수 사용
                TenantStorageMetrics temp = builder.build();
                builder.originalFileCount(fileCount)
                       .originalSize(totalSize)
                       .preRenderedFileCount(temp.getPreRenderedFileCount())
                       .preRenderedSize(temp.getPreRenderedSize())
                       .totalSize(temp.getTotalSize() + totalSize);
            } else if (category == com.hanumoka.sado.minipacs.domain.enums.FileCategory.SYSTEM) {
                TenantStorageMetrics temp = builder.build();
                builder.preRenderedFileCount(fileCount)
                       .preRenderedSize(totalSize)
                       .originalFileCount(temp.getOriginalFileCount())
                       .originalSize(temp.getOriginalSize())
                       .totalSize(temp.getTotalSize() + totalSize);
            }
        }

        List<TenantStorageMetrics> result = builderMap.values().stream()
            .map(TenantStorageMetrics.TenantStorageMetricsBuilder::build)
            .toList();

        log.info("Storage by tenant: total tenants={}", result.size());
        result.forEach(metric ->
            log.debug("Tenant {}: original={}/{}, preRendered={}/{}",
                metric.getTenantId(),
                metric.getOriginalFileCount(), metric.getOriginalSize(),
                metric.getPreRenderedFileCount(), metric.getPreRenderedSize())
        );

        return ApiResponse.success(result);
    }

    /**
     * 스토리지 메트릭 트렌드 조회
     * GET /api/admin/metrics/trends?range=7d
     *
     * @param range 조회 범위 (7d, 30d, 90d)
     * @return 시계열 메트릭 데이터
     */
    @Operation(
        summary = "스토리지 메트릭 트렌드 조회",
        description = "7일/30일/90일 단위로 스토리지 사용량 추이를 조회합니다."
    )
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "잘못된 range 값"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "권한 없음 (ADMIN 역할 필요)")
    })
    @GetMapping("/metrics/trends")
    public ApiResponse<List<StorageMetricsTrendResponse>> getStorageMetricsTrends(
            @RequestParam(defaultValue = "7d")
            @Pattern(regexp = "^(7d|30d|90d)$", message = "Range must be 7d, 30d, or 90d")
            String range
    ) {
        log.info("GET /api/admin/metrics/trends?range={}", range);

        // 1. range 파라미터에서 일수 추출
        int days = Integer.parseInt(range.replace("d", ""));

        // 2. 최근 N일간의 스냅샷 조회
        List<StorageMetricsSnapshot> snapshots = storageMetricsService.getRecentSnapshots(days);

        // 3. Entity → DTO 변환
        List<StorageMetricsTrendResponse> response = snapshots.stream()
            .map(snapshot -> StorageMetricsTrendResponse.builder()
                .timestamp(snapshot.getSnapshotTime())
                .totalSize(snapshot.getTotalSize())
                .hotSize(snapshot.getHotSize())
                .warmSize(snapshot.getWarmSize())
                .coldSize(snapshot.getColdSize())
                .fileCount(snapshot.getFileCount())
                .hotFileCount(snapshot.getHotFileCount())
                .warmFileCount(snapshot.getWarmFileCount())
                .coldFileCount(snapshot.getColdFileCount())
                .build())
            .collect(Collectors.toList());

        log.info("Storage metrics trends: range={}, data points={}", range, response.size());

        return ApiResponse.success(response);
    }

    /**
     * Tier 전환 이력 조회
     * GET /api/admin/metrics/tier-history?days=30&page=0&size=20
     *
     * @param days 조회 기간 (일)
     * @param page 페이지 번호
     * @param size 페이지 크기
     * @return Tier 전환 이력 (페이징)
     */
    @Operation(
        summary = "Tier 전환 이력 조회",
        description = "최근 N일간의 Tier 전환 이력을 페이징하여 조회합니다."
    )
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "권한 없음 (ADMIN 역할 필요)")
    })
    @GetMapping("/metrics/tier-history")
    public ApiResponse<Page<TierTransitionHistoryResponse>> getTierTransitionHistory(
            @RequestParam(defaultValue = "30")
            @Min(value = 1, message = "Days must be >= 1")
            @Max(value = 365, message = "Days must be <= 365")
            int days,

            @RequestParam(defaultValue = "0")
            @Min(value = 0, message = "Page must be >= 0")
            int page,

            @RequestParam(defaultValue = "20")
            @Min(value = 1, message = "Size must be >= 1")
            @Max(value = 100, message = "Size must be <= 100")
            int size
    ) {
        log.info("GET /api/admin/metrics/tier-history?days={}&page={}&size={}", days, page, size);

        // 1. 조회 기준 시각 계산
        LocalDateTime since = LocalDateTime.now().minusDays(days);

        // 2. Repository에서 페이징 조회 (N+1 방지용 JOIN FETCH 사용)
        Pageable pageable = PageRequest.of(page, size);
        Page<TierTransitionLog> logs = tierTransitionLogRepository
            .findByTransitionedAtAfterWithFileAsset(since, pageable);

        // 3. Entity → DTO 변환 (이미 JOIN FETCH로 로드된 fileAsset 활용)
        Page<TierTransitionHistoryResponse> response = logs.map(log -> {
            // CRITICAL: N+1 방지 - fileAsset이 이미 eager loading되어 있음
            FileAsset fileAsset = log.getFileAsset();
            String storagePath = (fileAsset != null) ? fileAsset.getStoragePath() : null;

            return TierTransitionHistoryResponse.builder()
                .id(log.getId())
                .fileAssetId(log.getFileAsset() != null ? log.getFileAsset().getId() : null)
                .storagePath(storagePath)
                .fromTier(log.getFromTier())
                .toTier(log.getToTier())
                .reason(log.getReason())
                .transitionType(log.getTransitionType().name())
                .performedBy(log.getPerformedBy())
                .transitionedAt(log.getTransitionedAt())
                .tenantId(log.getTenantId())
                .build();
        });

        log.info("Tier transition history: days={}, total={}, page={}/{}",
                 days, response.getTotalElements(), page + 1, response.getTotalPages());

        return ApiResponse.success(response);
    }

    /**
     * 수동 Tier 전환
     * POST /api/admin/files/{id}/tier
     *
     * @param id FileAsset ID
     * @param request Tier 전환 요청 (tier, reason)
     * @return 업데이트된 FileAsset 정보
     */
    @Operation(
        summary = "수동 Tier 전환",
        description = "특정 파일의 Storage Tier를 수동으로 변경합니다."
    )
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "전환 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "잘못된 요청 (유효하지 않은 Tier 등)"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "FileAsset을 찾을 수 없음"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "권한 없음 (ADMIN 역할 필요)")
    })
    @PostMapping("/files/{id}/tier")
    public ApiResponse<FileAssetSummary> transitionFileTier(
            @PathVariable Long id,
            @Valid @RequestBody TierTransitionRequest request
    ) {
        log.info("POST /api/admin/files/{}/tier, tier={}", id, request.getTier());

        // 1. Tier 전환 실행 (performedBy는 POC에서는 "ADMIN", Week 12+에서는 JWT에서 추출)
        FileAsset updated = tieringManagementService.transitionTier(
            id,
            request.getTier(),
            request.getReason(),
            "ADMIN" // TODO: Week 12+ JWT에서 userId 추출
        );

        // 2. Entity → DTO 변환
        FileAssetSummary response = FileAssetSummary.builder()
            .id(updated.getId())
            .storagePath(updated.getStoragePath())
            .fileCategory(updated.getCategory().name())
            .storageTier(updated.getStorageTier())
            .fileSize(updated.getFileSize())
            .lastAccessedAt(updated.getLastAccessedAt())
            .createdAt(updated.getCreatedAt())
            .build();

        log.info("Tier transition completed: fileId={}, newTier={}", id, request.getTier());

        return ApiResponse.success(response);
    }

    /**
     * Tiering 정책 업데이트
     * PUT /api/admin/tiering/policy
     *
     * @param request 정책 업데이트 요청
     * @return 업데이트된 정책
     */
    @Operation(
        summary = "Tiering 정책 업데이트",
        description = "HOT→WARM, WARM→COLD 전환 기준 일수 및 스케줄러 설정을 업데이트합니다."
    )
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "업데이트 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "잘못된 요청 (warmToColdDays <= hotToWarmDays 등)"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "권한 없음 (ADMIN 역할 필요)")
    })
    @PutMapping("/tiering/policy")
    public ApiResponse<TieringPolicies> updateTieringPolicy(
            @Valid @RequestBody UpdateTieringPolicyRequest request
    ) {
        log.info("PUT /api/admin/tiering/policy");

        // 1. 정책 업데이트
        TieringPolicy updated = tieringManagementService.updateTieringPolicy(
            request.getHotToWarmDays(),
            request.getWarmToColdDays(),
            request.getSchedulerEnabled(),
            request.getHotToWarmSchedule(),
            request.getWarmToColdSchedule()
        );

        // 2. Entity → DTO 변환
        TieringPolicies response = TieringPolicies.builder()
            .hotToWarmDays(updated.getHotToWarmDays())
            .warmToColdDays(updated.getWarmToColdDays())
            .schedulerEnabled(updated.getSchedulerEnabled())
            .hotToWarmSchedule(updated.getHotToWarmSchedule())
            .warmToColdSchedule(updated.getWarmToColdSchedule())
            .build();

        log.info("Tiering policy updated: HOT→WARM={} days, WARM→COLD={} days",
                 updated.getHotToWarmDays(), updated.getWarmToColdDays());

        return ApiResponse.success(response);
    }

    /**
     * SeaweedFS 물리적 스토리지 용량 조회
     * GET /api/admin/seaweedfs/capacity
     *
     * @return SeaweedFS 디스크 용량 정보
     */
    @Operation(
        summary = "SeaweedFS 물리적 스토리지 용량 조회",
        description = "SeaweedFS 파일 스토리지의 실제 디스크 용량(전체 용량, 사용량, 여유 공간)을 조회합니다. " +
                      "FileAsset 테이블 기반 메타데이터 합계와는 다른 정보입니다."
    )
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "503", description = "SeaweedFS 서버 접근 불가"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "권한 없음 (ADMIN 역할 필요)")
    })
    @GetMapping("/seaweedfs/capacity")
    public ApiResponse<SeaweedFSCapacityResponse> getSeaweedFSCapacity() {
        log.info("GET /api/admin/seaweedfs/capacity");

        // 1. Volume Server 디스크 용량 조회
        Long[] diskCapacity = seaweedFSAdminService.getVolumeServerDiskCapacity();
        Long totalCapacity = diskCapacity[0];
        Long usedSpace = diskCapacity[1];
        Long freeSpace = diskCapacity[2];

        // 2. 사용률 계산 (소수점 2자리)
        double percentUsed = totalCapacity > 0
            ? (usedSpace * 100.0 / totalCapacity)
            : 0.0;
        percentUsed = Math.round(percentUsed * 100.0) / 100.0;

        // 3. 응답 생성
        SeaweedFSCapacityResponse response = SeaweedFSCapacityResponse.builder()
            .totalCapacity(totalCapacity)
            .usedSpace(usedSpace)
            .freeSpace(freeSpace)
            .percentUsed(percentUsed)
            .build();

        log.info("SeaweedFS capacity: total={}, used={}, free={}, usage={}%",
            totalCapacity, usedSpace, freeSpace, percentUsed);

        return ApiResponse.success(response);
    }

    /**
     * 모니터링 작업 현황 조회
     * GET /api/admin/monitoring/tasks
     *
     * <p>업로드 및 사전렌더링 작업 현황을 실시간으로 조회합니다.
     * 프론트엔드에서 5초 간격으로 폴링합니다.
     *
     * @param limit 최근 업로드 조회 개수 (기본값: 10)
     * @return 모니터링 작업 현황
     */
    @Operation(
        summary = "모니터링 작업 현황 조회",
        description = "업로드 및 사전렌더링 작업 현황을 조회합니다. " +
                      "최근 1시간 내 업로드된 Instance와 렌더링 진행 중인 작업 목록을 제공합니다."
    )
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "권한 없음 (ADMIN 역할 필요)")
    })
    @GetMapping("/monitoring/tasks")
    public ApiResponse<MonitoringTasksResponse> getMonitoringTasks(
            @RequestParam(defaultValue = "10")
            @Min(value = 1, message = "Limit must be >= 1")
            @Max(value = 50, message = "Limit must be <= 50")
            int limit
    ) {
        log.info("GET /api/admin/monitoring/tasks?limit={}", limit);

        LocalDateTime oneHourAgo = LocalDateTime.now().minusHours(1);

        // 1. 최근 업로드 조회 (최근 1시간)
        Pageable pageable = PageRequest.of(0, limit);
        Page<Instance> recentUploads = instanceRepository.findRecentUploads(oneHourAgo, pageable);

        List<UploadTask> uploadTasks = recentUploads.getContent().stream()
            .map(instance -> UploadTask.builder()
                .instanceId(instance.getId())
                .sopInstanceUid(instance.getSopInstanceUid())
                .studyDescription(instance.getSeries().getStudy().getStudyDescription())
                .modality(instance.getSeries().getModality())
                .fileSize(instance.getFileSize())
                .uploadedAt(instance.getCreatedAt())
                .renderingStatus(instance.getTranscodingStatus() != null
                    ? instance.getTranscodingStatus().name()
                    : "NONE")
                .tenantId(instance.getTenantId())
                .build())
            .toList();

        // 2. 렌더링 진행 중인 작업 조회 (PENDING + PROCESSING)
        List<Instance> renderingInstances = instanceRepository.findByTranscodingStatusIn(
            List.of(Instance.TranscodingStatus.PENDING, Instance.TranscodingStatus.PROCESSING)
        );

        List<RenderingTask> renderingTasks = renderingInstances.stream()
            .map(instance -> {
                int totalFrames = instance.getNumberOfFrames() != null ? instance.getNumberOfFrames() : 1;
                int renderedFrames = instance.getPrerenderedFrameCount() != null ? instance.getPrerenderedFrameCount() : 0;
                int progressPercent = totalFrames > 0 ? (renderedFrames * 100 / totalFrames) : 0;

                return RenderingTask.builder()
                    .instanceId(instance.getId())
                    .sopInstanceUid(instance.getSopInstanceUid())
                    .studyDescription(instance.getSeries().getStudy().getStudyDescription())
                    .modality(instance.getSeries().getModality())
                    .status(instance.getTranscodingStatus().name())
                    .totalFrames(totalFrames)
                    .renderedFrames(renderedFrames)
                    .renderedSize(instance.getPrerenderedTotalSize())
                    .startedAt(instance.getCreatedAt())
                    .progressPercent(progressPercent)
                    .tenantId(instance.getTenantId())
                    .build();
            })
            .toList();

        // 3. 요약 통계 계산
        int totalPending = 0;
        int totalProcessing = 0;

        List<Object[]> statusCounts = instanceRepository.countByTranscodingStatus();
        for (Object[] row : statusCounts) {
            Instance.TranscodingStatus status = (Instance.TranscodingStatus) row[0];
            Long count = (Long) row[1];
            if (status == Instance.TranscodingStatus.PENDING) {
                totalPending = count.intValue();
            } else if (status == Instance.TranscodingStatus.PROCESSING) {
                totalProcessing = count.intValue();
            }
        }

        // 최근 1시간 내 완료/실패 개수
        int recentCompleted = (int) instanceRepository.countByPrerenderedAtAfterAndTranscodingStatus(
            oneHourAgo, Instance.TranscodingStatus.COMPLETED
        );
        int recentFailed = (int) instanceRepository.countByPrerenderedAtAfterAndTranscodingStatus(
            oneHourAgo, Instance.TranscodingStatus.FAILED
        );

        TaskSummary summary = TaskSummary.builder()
            .totalPending(totalPending)
            .totalProcessing(totalProcessing)
            .recentCompleted(recentCompleted)
            .recentFailed(recentFailed)
            .build();

        // 4. 응답 생성
        MonitoringTasksResponse response = MonitoringTasksResponse.builder()
            .uploadTasks(uploadTasks)
            .renderingTasks(renderingTasks)
            .summary(summary)
            .build();

        log.info("Monitoring tasks: uploads={}, rendering={}, pending={}, processing={}",
            uploadTasks.size(), renderingTasks.size(), totalPending, totalProcessing);

        return ApiResponse.success(response);
    }
}
