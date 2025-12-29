package com.hanumoka.sado.minipacs.domain.enums;

/**
 * 파일 자산 상태
 *
 * <p>FileAsset의 생명주기 상태를 나타내며, TTL 관리 및 정리 작업에 사용됩니다.
 *
 * <p>상태 전이 흐름:
 * <pre>
 * ACTIVE → EXPIRED → DELETED
 *   ↓
 * ARCHIVED
 * </pre>
 *
 * <p>사용 예시:
 * <pre>
 * {@code
 * FileAsset file = fileAssetRepository.findById(assetId);
 *
 * // TTL 만료 체크
 * if (file.getStatus() == FileStatus.ACTIVE && file.isExpired()) {
 *     file.setStatus(FileStatus.EXPIRED);
 * }
 *
 * // 만료된 파일 정리
 * if (file.getStatus() == FileStatus.EXPIRED) {
 *     deletePhysicalFile(file);
 *     file.setStatus(FileStatus.DELETED);
 * }
 * }
 * </pre>
 */
public enum FileStatus {

    /**
     * 활성 상태
     *
     * <p>파일이 정상적으로 사용 가능한 상태입니다.
     *
     * <p>특징:
     * <ul>
     *   <li>물리 파일 존재</li>
     *   <li>다운로드 가능</li>
     *   <li>TTL 미만료 (expiresAt이 null이거나 미래 시점)</li>
     * </ul>
     *
     * <p>다음 상태:
     * <ul>
     *   <li>EXPIRED: TTL 만료 시 (자동 전이)</li>
     *   <li>ARCHIVED: 장기 보관 이동 시</li>
     *   <li>DELETED: 사용자 명시적 삭제 시</li>
     * </ul>
     */
    ACTIVE,

    /**
     * 만료 상태
     *
     * <p>TTL이 만료되었지만 아직 물리 삭제되지 않은 상태입니다.
     *
     * <p>특징:
     * <ul>
     *   <li>물리 파일 존재 (아직 삭제 안 됨)</li>
     *   <li>다운로드 불가 (비즈니스 규칙)</li>
     *   <li>expiresAt < 현재 시각</li>
     *   <li>유예 기간 7일 (실수로 만료된 경우 복구 가능)</li>
     * </ul>
     *
     * <p>처리 로직:
     * <pre>
     * {@code
     * // RetentionPolicyService에서 주기적 실행
     * List<FileAsset> expiredFiles = fileAssetRepository.findByStatus(FileStatus.EXPIRED);
     * for (FileAsset file : expiredFiles) {
     *     if (file.getExpiredAt().plusDays(7).isBefore(LocalDateTime.now())) {
     *         // 유예 기간 7일 경과 → 물리 삭제
     *         deletePhysicalFile(file);
     *         file.setStatus(FileStatus.DELETED);
     *     }
     * }
     * }
     * </pre>
     *
     * <p>다음 상태:
     * <ul>
     *   <li>DELETED: 유예 기간 경과 후 물리 삭제</li>
     *   <li>ACTIVE: 관리자 복구 시 (수동 전이)</li>
     * </ul>
     */
    EXPIRED,

    /**
     * 삭제됨
     *
     * <p>물리 파일이 삭제된 상태입니다 (메타데이터만 존재).
     *
     * <p>특징:
     * <ul>
     *   <li>물리 파일 없음 (스토리지에서 삭제됨)</li>
     *   <li>DB 레코드만 존재 (감사 추적 목적)</li>
     *   <li>복구 불가</li>
     * </ul>
     *
     * <p>보관 이유:
     * <ul>
     *   <li>감사 추적 (Audit Trail): 언제 어떤 파일이 삭제되었는지 기록</li>
     *   <li>규정 준수: 의료 데이터 삭제 이력 보관 의무</li>
     *   <li>통계 분석: 스토리지 사용량 트렌드 분석</li>
     * </ul>
     *
     * <p>최종 정리:
     * <pre>
     * {@code
     * // 삭제된 지 1년 경과 시 메타데이터도 제거 (선택적)
     * @Scheduled(cron = "0 0 2 * * *") // 매일 새벽 2시
     * public void purgeOldDeletedRecords() {
     *     LocalDateTime oneYearAgo = LocalDateTime.now().minusYears(1);
     *     fileAssetRepository.deleteByStatusAndDeletedAtBefore(
     *         FileStatus.DELETED,
     *         oneYearAgo
     *     );
     * }
     * }
     * </pre>
     */
    DELETED,

    /**
     * 아카이브됨
     *
     * <p>장기 보관 스토리지로 이동된 상태입니다 (COLD Storage).
     *
     * <p>특징:
     * <ul>
     *   <li>물리 파일 존재 (COLD Storage에)</li>
     *   <li>즉시 다운로드 불가 (복원 필요)</li>
     *   <li>스토리지 비용 절감 (HOT 대비 1/10)</li>
     * </ul>
     *
     * <p>아카이브 대상:
     * <ul>
     *   <li>1년 이상 미접근 AI 분석 결과</li>
     *   <li>오래된 임상 문서 (법적 보관 의무만 있음)</li>
     *   <li>카테고리가 CLINICAL_DOC이고 마지막 접근 > 2년</li>
     * </ul>
     *
     * <p>복원 프로세스:
     * <pre>
     * {@code
     * // 사용자가 아카이브 파일 요청 시
     * FileAsset archivedFile = fileAssetRepository.findById(assetId);
     * if (archivedFile.getStatus() == FileStatus.ARCHIVED) {
     *     // S3 Glacier → S3 Standard 복원 (3-5시간 소요)
     *     s3Client.restoreObject(archivedFile.getStoragePath());
     *
     *     // 복원 완료 후
     *     archivedFile.setStatus(FileStatus.ACTIVE);
     *     archivedFile.setStorageTier("HOT");
     * }
     * }
     * </pre>
     *
     * <p>다음 상태:
     * <ul>
     *   <li>ACTIVE: 복원 완료 후</li>
     *   <li>DELETED: 보관 기간 만료 시</li>
     * </ul>
     */
    ARCHIVED
}
