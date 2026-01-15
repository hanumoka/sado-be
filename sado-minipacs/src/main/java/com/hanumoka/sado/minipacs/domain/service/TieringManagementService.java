package com.hanumoka.sado.minipacs.domain.service;

import static com.hanumoka.sado.common.util.StringUtils.hasText;
import com.hanumoka.sado.common.exception.BusinessException;
import com.hanumoka.sado.minipacs.code.MiniPacsErrorCode;
import com.hanumoka.sado.minipacs.domain.entity.FileAsset;
import com.hanumoka.sado.minipacs.domain.entity.TierTransitionLog;
import com.hanumoka.sado.minipacs.domain.entity.TieringPolicy;
import com.hanumoka.sado.minipacs.domain.enums.FileStatus;
import com.hanumoka.sado.minipacs.domain.repository.FileAssetRepository;
import com.hanumoka.sado.minipacs.domain.repository.TierTransitionLogRepository;
import com.hanumoka.sado.minipacs.domain.repository.TieringPolicyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Tier 관리 서비스
 *
 * <p>수동 Tier 전환 및 정책 관리를 담당합니다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class TieringManagementService {

    private final FileAssetRepository fileAssetRepository;
    private final TierTransitionLogRepository tierTransitionLogRepository;
    private final TieringPolicyRepository tieringPolicyRepository;

    /**
     * 수동 Tier 전환
     *
     * <p>관리자가 특정 파일의 Tier를 수동으로 변경합니다.
     *
     * @param fileId FileAsset ID
     * @param targetTier 목표 Tier (HOT/WARM/COLD)
     * @param reason 전환 사유
     * @param performedBy 작업 수행자 (User ID)
     * @return 업데이트된 FileAsset
     * @throws BusinessException FileAsset를 찾을 수 없거나 유효하지 않은 경우
     */
    @Transactional
    public FileAsset transitionTier(Long fileId, String targetTier, String reason, String performedBy) {
        log.info("Manual tier transition: fileId={}, targetTier={}, performedBy={}",
                 fileId, targetTier, performedBy);

        // 1. FileAsset 조회
        FileAsset fileAsset = fileAssetRepository.findById(fileId)
            .orElseThrow(() -> new BusinessException(
                MiniPacsErrorCode.FILE_NOT_FOUND,
                "FileAsset not found: " + fileId
            ));

        // 2. Validation
        validateTierTransition(fileAsset, targetTier);

        // 3. Tier 전환 로그 생성 (fileAsset 관계 사용 - N+1 방지)
        String currentTier = fileAsset.getStorageTier();
        TierTransitionLog log = TierTransitionLog.builder()
            .fileAsset(fileAsset)
            .fromTier(currentTier)
            .toTier(targetTier)
            .reason(reason != null ? reason : "Manual transition by admin")
            .transitionType(TierTransitionLog.TransitionType.MANUAL)
            .performedBy(performedBy != null ? performedBy : "ADMIN")
            .transitionedAt(LocalDateTime.now())
            .build();

        tierTransitionLogRepository.save(log);

        // 4. FileAsset Tier 업데이트
        fileAsset.setStorageTier(targetTier);
        FileAsset updated = fileAssetRepository.save(fileAsset);

        this.log.info("Tier transition completed: fileId={}, {} -> {}",
                      fileId, currentTier, targetTier);

        return updated;
    }

    /**
     * Tier 전환 유효성 검증
     *
     * @param fileAsset 대상 파일
     * @param targetTier 목표 Tier
     * @throws BusinessException 검증 실패 시
     */
    private void validateTierTransition(FileAsset fileAsset, String targetTier) {
        // 1. Tier 값 검증
        if (!"HOT".equals(targetTier) && !"WARM".equals(targetTier) && !"COLD".equals(targetTier)) {
            throw new BusinessException(
                MiniPacsErrorCode.INVALID_TIER,
                "Invalid tier: " + targetTier + ". Must be HOT, WARM, or COLD"
            );
        }

        // 2. FileAsset 상태 검증 (ACTIVE만 Tier 변경 가능)
        if (fileAsset.getStatus() != FileStatus.ACTIVE) {
            throw new BusinessException(
                MiniPacsErrorCode.INVALID_FILE_STATUS,
                "Cannot transition tier for non-ACTIVE file: status=" + fileAsset.getStatus()
            );
        }

        // 3. 동일 Tier로의 전환 방지
        if (targetTier.equals(fileAsset.getStorageTier())) {
            throw new BusinessException(
                MiniPacsErrorCode.TIER_ALREADY_SET,
                "File is already in " + targetTier + " tier"
            );
        }
    }

    /**
     * Tiering 정책 업데이트
     *
     * <p>현재 Tenant의 Tier 전환 정책을 업데이트합니다.
     * 정책이 없으면 새로 생성합니다.
     *
     * @param hotToWarmDays HOT → WARM 전환 일수
     * @param warmToColdDays WARM → COLD 전환 일수
     * @param schedulerEnabled 스케줄러 활성화 여부
     * @param hotToWarmSchedule HOT → WARM 스케줄 (Cron)
     * @param warmToColdSchedule WARM → COLD 스케줄 (Cron)
     * @return 업데이트된 정책
     * @throws BusinessException 정책 검증 실패 시
     */
    @Transactional
    public TieringPolicy updateTieringPolicy(
        Integer hotToWarmDays,
        Integer warmToColdDays,
        Boolean schedulerEnabled,
        String hotToWarmSchedule,
        String warmToColdSchedule
    ) {
        log.info("Updating tiering policy: hotToWarm={} days, warmToCold={} days, enabled={}",
                 hotToWarmDays, warmToColdDays, schedulerEnabled);

        // 1. 현재 정책 조회 (없으면 새로 생성)
        TieringPolicy policy = tieringPolicyRepository.findCurrentPolicy()
            .orElseGet(TieringPolicy::createDefault);

        // 2. 정책 업데이트
        policy.setHotToWarmDays(hotToWarmDays);
        policy.setWarmToColdDays(warmToColdDays);
        policy.setSchedulerEnabled(schedulerEnabled);

        if (hasText(hotToWarmSchedule)) {
            policy.setHotToWarmSchedule(hotToWarmSchedule);
        }
        if (hasText(warmToColdSchedule)) {
            policy.setWarmToColdSchedule(warmToColdSchedule);
        }

        // 3. Validation
        try {
            policy.validate();
        } catch (IllegalArgumentException e) {
            throw new BusinessException(
                MiniPacsErrorCode.INVALID_TIERING_POLICY,
                e.getMessage()
            );
        }

        // 4. 저장
        TieringPolicy saved = tieringPolicyRepository.save(policy);

        log.info("Tiering policy updated successfully");
        return saved;
    }

    /**
     * 현재 Tiering 정책 조회
     *
     * @return 현재 정책 (없으면 기본값 반환)
     */
    public TieringPolicy getCurrentPolicy() {
        return tieringPolicyRepository.findCurrentPolicy()
            .orElseGet(TieringPolicy::createDefault);
    }
}
