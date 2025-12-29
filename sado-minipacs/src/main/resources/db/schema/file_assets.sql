-- =====================================================
-- FileAsset Entity DDL
-- =====================================================
-- 목적: DICOM 파일 외의 모든 파일 자산 관리
--       (AI 결과, 리포트, 임시 파일, Export 등)
--
-- 특징:
-- 1. Polymorphic Association (reference_type + reference_id)
-- 2. TTL 기반 자동 만료 (expires_at)
-- 3. Storage Tiering (HOT/WARM/COLD)
-- 4. 멀티테넌시 (tenant_id)
-- =====================================================

CREATE TABLE IF NOT EXISTS file_assets (
    -- ========== PK ==========
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '파일 자산 ID (PK)',

    -- ========== 카테고리 및 참조 ==========
    category VARCHAR(32) NOT NULL COMMENT '파일 카테고리 (AI_RESULT, CLINICAL_DOC, SYSTEM, EXPORT)',
    reference_type VARCHAR(32) NOT NULL COMMENT '참조 엔티티 타입 (INSTANCE, STUDY, SERIES, PATIENT, AI_ANALYSIS)',
    reference_id BIGINT NOT NULL COMMENT '참조 엔티티 ID (polymorphic association)',

    -- ========== 파일 상태 ==========
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE' COMMENT '파일 상태 (ACTIVE, EXPIRED, DELETED, ARCHIVED)',

    -- ========== 파일 메타데이터 ==========
    file_name VARCHAR(255) NOT NULL COMMENT '원본 파일명 (예: segmentation_frame_001.png)',
    storage_path VARCHAR(512) NOT NULL UNIQUE COMMENT '스토리지 경로 (예: /ai_results/123/seg/frame_001.png)',
    file_size BIGINT NOT NULL COMMENT '파일 크기 (bytes)',
    mime_type VARCHAR(128) NOT NULL DEFAULT 'application/octet-stream' COMMENT 'MIME 타입 (예: image/png, application/pdf)',
    checksum VARCHAR(128) NULL COMMENT '파일 체크섬 (MD5 또는 SHA-256)',

    -- ========== TTL 및 접근 관리 ==========
    expires_at DATETIME NULL COMMENT '만료 일시 (TTL), null이면 영구 보관',
    last_accessed_at DATETIME NULL COMMENT '마지막 접근 일시 (Storage Tiering 기준)',
    storage_tier VARCHAR(16) DEFAULT 'HOT' COMMENT 'Storage Tier (HOT, WARM, COLD)',

    -- ========== 메타데이터 (확장성) ==========
    metadata TEXT NULL COMMENT '추가 메타데이터 (JSON)',

    -- ========== Audit Fields ==========
    tenant_id BIGINT NOT NULL COMMENT '테넌트 ID (멀티테넌시)',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성 일시',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '수정 일시',
    created_by VARCHAR(100) NULL COMMENT '생성자',
    updated_by VARCHAR(100) NULL COMMENT '수정자',

    -- ========== Indexes ==========
    INDEX idx_file_assets_reference (reference_type, reference_id)
        COMMENT '참조 엔티티로 파일 조회 (Instance의 모든 파일, AI 분석의 모든 결과)',

    INDEX idx_file_assets_category_status (category, status)
        COMMENT '카테고리+상태로 파일 조회 (활성 AI 결과, 만료된 Export 등)',

    INDEX idx_file_assets_expires_at (expires_at)
        COMMENT 'TTL 만료 파일 조회 (RetentionPolicyService)',

    INDEX idx_file_assets_tenant_id (tenant_id)
        COMMENT '멀티테넌시 필터링',

    INDEX idx_file_assets_checksum (checksum)
        COMMENT '중복 파일 감지 (체크섬 기반)',

    INDEX idx_file_assets_tier_accessed (storage_tier, last_accessed_at)
        COMMENT 'Storage Tier 마이그레이션 대상 조회'

) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='파일 자산 관리 (AI 결과, 리포트, 임시 파일, Export 등)';

-- =====================================================
-- 샘플 데이터 (개발/테스트용)
-- =====================================================

-- AI 분석 결과 파일 (영구 보관, TTL 없음)
INSERT INTO file_assets (
    category, reference_type, reference_id, status,
    file_name, storage_path, file_size, mime_type, checksum,
    expires_at, storage_tier, tenant_id, created_by
) VALUES (
    'AI_RESULT', 'AI_ANALYSIS', 1, 'ACTIVE',
    'segmentation_frame_001.png', '/ai_results/1/seg/frame_001.png', 128000, 'image/png', 'abc123def456',
    NULL, 'HOT', 1, 'system'
);

-- Export 파일 (7일 TTL)
INSERT INTO file_assets (
    category, reference_type, reference_id, status,
    file_name, storage_path, file_size, mime_type, checksum,
    expires_at, storage_tier, tenant_id, created_by
) VALUES (
    'EXPORT', 'STUDY', 1, 'ACTIVE',
    'study_export.zip', '/exports/study_1.zip', 1234567890, 'application/zip', 'def789ghi012',
    DATE_ADD(NOW(), INTERVAL 7 DAY), 'HOT', 1, 'user@example.com'
);

-- 시스템 파일 (90일 TTL)
INSERT INTO file_assets (
    category, reference_type, reference_id, status,
    file_name, storage_path, file_size, mime_type, checksum,
    expires_at, storage_tier, tenant_id, created_by
) VALUES (
    'SYSTEM', 'INSTANCE', 1, 'ACTIVE',
    'thumbnail.jpg', '/thumbnails/instance_1.jpg', 45678, 'image/jpeg', 'ghi345jkl678',
    DATE_ADD(NOW(), INTERVAL 90 DAY), 'HOT', 1, 'system'
);

-- 임상 문서 (영구 보관, TTL 없음)
INSERT INTO file_assets (
    category, reference_type, reference_id, status,
    file_name, storage_path, file_size, mime_type, checksum,
    expires_at, storage_tier, tenant_id, created_by
) VALUES (
    'CLINICAL_DOC', 'STUDY', 1, 'ACTIVE',
    'diagnostic_report.pdf', '/clinical/study_1_report.pdf', 987654, 'application/pdf', 'jkl901mno234',
    NULL, 'WARM', 1, 'doctor@hospital.com'
);

-- =====================================================
-- 유용한 쿼리 예시
-- =====================================================

-- 1. 만료 예정 파일 조회 (7일 이내)
-- SELECT id, file_name, category, expires_at
-- FROM file_assets
-- WHERE expires_at IS NOT NULL
--   AND expires_at BETWEEN NOW() AND DATE_ADD(NOW(), INTERVAL 7 DAY)
-- ORDER BY expires_at ASC;

-- 2. 카테고리별 스토리지 사용량
-- SELECT category, COUNT(*) as file_count, SUM(file_size) as total_size_bytes
-- FROM file_assets
-- WHERE status = 'ACTIVE'
-- GROUP BY category;

-- 3. Storage Tier별 스토리지 사용량
-- SELECT storage_tier, COUNT(*) as file_count, SUM(file_size) as total_size_bytes
-- FROM file_assets
-- WHERE status = 'ACTIVE'
-- GROUP BY storage_tier;

-- 4. COLD Storage 이동 대상 파일 조회 (1년 이상 미접근)
-- SELECT id, file_name, last_accessed_at, storage_tier
-- FROM file_assets
-- WHERE status = 'ACTIVE'
--   AND storage_tier = 'HOT'
--   AND last_accessed_at < DATE_SUB(NOW(), INTERVAL 1 YEAR);

-- 5. 만료된 파일 정리 대상 (만료 후 7일 경과)
-- SELECT id, file_name, updated_at
-- FROM file_assets
-- WHERE status = 'EXPIRED'
--   AND updated_at < DATE_SUB(NOW(), INTERVAL 7 DAY);

-- 6. 특정 AI 분석의 모든 결과 파일
-- SELECT id, file_name, file_size, storage_path
-- FROM file_assets
-- WHERE reference_type = 'AI_ANALYSIS'
--   AND reference_id = 123
-- ORDER BY created_at DESC;

-- 7. 중복 파일 감지 (동일 체크섬)
-- SELECT checksum, COUNT(*) as duplicate_count, GROUP_CONCAT(id) as file_ids
-- FROM file_assets
-- WHERE checksum IS NOT NULL
-- GROUP BY checksum
-- HAVING COUNT(*) > 1;
