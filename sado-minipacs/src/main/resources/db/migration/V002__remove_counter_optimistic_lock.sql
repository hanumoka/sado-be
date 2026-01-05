-- ========================================
-- Migration: 역정규화 카운터 및 Optimistic Lock 제거
-- Date: 2026-01-05
-- Purpose: Optimistic Lock 충돌 근본 원인 제거
-- ========================================

-- CRITICAL: 이 마이그레이션은 다음을 수행합니다:
-- 1. study 테이블에서 numberOfInstances, version 컬럼 제거
-- 2. series 테이블에서 numberOfInstances, version 컬럼 제거
--
-- 변경 후:
-- - numberOfInstances는 @Transient 메서드로 실시간 계산
-- - Optimistic Lock 충돌 완전 제거 → 무한 동시성 확보

-- ========================================
-- Step 1: Study 테이블 수정
-- ========================================

-- 1-1. numberOfInstances 컬럼 제거
ALTER TABLE study DROP COLUMN number_of_instances;

-- 1-2. version 컬럼 제거 (Optimistic Lock)
ALTER TABLE study DROP COLUMN version;

-- ========================================
-- Step 2: Series 테이블 수정
-- ========================================

-- 2-1. numberOfInstances 컬럼 제거
ALTER TABLE series DROP COLUMN number_of_instances;

-- 2-2. version 컬럼 제거 (Optimistic Lock)
ALTER TABLE series DROP COLUMN version;

-- ========================================
-- 검증 쿼리 (마이그레이션 후 실행 권장)
-- ========================================

-- Study 테이블 구조 확인
-- DESCRIBE study;

-- Series 테이블 구조 확인
-- DESCRIBE series;

-- Instance 개수 확인 (실시간 COUNT)
-- SELECT
--     s.id AS study_id,
--     s.study_instance_uid,
--     COUNT(i.id) AS actual_instance_count
-- FROM study s
-- LEFT JOIN series se ON se.study_id = s.id
-- LEFT JOIN instance i ON i.series_id = se.id
-- GROUP BY s.id, s.study_instance_uid
-- ORDER BY s.id;

-- ========================================
-- Rollback (필요 시 수동 실행)
-- ========================================

-- CRITICAL: 롤백 시 기존 데이터 복구 불가
-- numberOfInstances는 애플리케이션 레벨에서 재계산 필요

-- ALTER TABLE study ADD COLUMN number_of_instances INT DEFAULT 0;
-- ALTER TABLE study ADD COLUMN version BIGINT DEFAULT 0;
-- ALTER TABLE series ADD COLUMN number_of_instances INT DEFAULT 0;
-- ALTER TABLE series ADD COLUMN version BIGINT DEFAULT 0;

-- 재계산 스크립트:
-- UPDATE study s
-- SET number_of_instances = (
--     SELECT COUNT(*)
--     FROM instance i
--     INNER JOIN series se ON i.series_id = se.id
--     WHERE se.study_id = s.id
-- );

-- UPDATE series s
-- SET number_of_instances = (
--     SELECT COUNT(*)
--     FROM instance i
--     WHERE i.series_id = s.id
-- );
