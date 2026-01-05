package com.hanumoka.sado.minipacs.storage.dto;

import lombok.Builder;
import lombok.Getter;

/**
 * Storage 업로드 결과
 *
 * Storage 계층이 결정한 경로 및 메타데이터 반환
 *
 * <p>설계 원칙:
 * <ul>
 *   <li>Storage 계층이 경로를 결정하고 반환</li>
 *   <li>비즈니스 계층은 반환된 경로를 DB에 저장</li>
 *   <li>Storage 전략 변경 시 비즈니스 로직 수정 불필요</li>
 * </ul>
 */
@Getter
@Builder
public class StorageResult {

    /**
     * Storage에 저장된 파일 경로
     *
     * <p>예시:
     * <ul>
     *   <li>DICOMweb: "studies/1.2.840.../series/1.2.840.../instances/1.2.840....dcm"</li>
     *   <li>Date-based: "2026/01/05/1.2.840.113619.2.55.1.789.dcm"</li>
     * </ul>
     */
    private final String storagePath;

    /**
     * S3 키 (S3 사용 시)
     * storagePath와 동일할 수도 있음
     */
    private final String s3Key;

    /**
     * 업로드된 파일 크기 (bytes)
     */
    private final Long fileSize;

    /**
     * 파일 해시 (SHA-256, 선택사항)
     * 무결성 검증용
     */
    private final String fileHash;
}
