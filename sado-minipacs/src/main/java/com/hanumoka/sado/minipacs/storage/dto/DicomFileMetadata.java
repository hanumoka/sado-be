package com.hanumoka.sado.minipacs.storage.dto;

import lombok.Builder;
import lombok.Getter;

/**
 * DICOM 파일 메타데이터
 *
 * Storage 계층에 전달하는 추상화된 키
 * 실제 파일 경로는 Storage 계층이 결정
 *
 * <p>설계 원칙:
 * <ul>
 *   <li>비즈니스 로직은 저장소 구조를 몰라야 함</li>
 *   <li>DICOM 표준 식별자만 전달</li>
 *   <li>경로 생성은 StoragePathStrategy가 담당</li>
 * </ul>
 */
@Getter
@Builder
public class DicomFileMetadata {

    /**
     * DICOM Study Instance UID (0020,000D)
     * DICOM 표준의 검사 고유 식별자
     */
    private final String studyInstanceUid;

    /**
     * DICOM Series Instance UID (0020,000E)
     * DICOM 표준의 시리즈 고유 식별자
     */
    private final String seriesInstanceUid;

    /**
     * DICOM SOP Instance UID (0008,0018)
     * DICOM 표준의 인스턴스 고유 식별자 (globally unique)
     */
    private final String sopInstanceUid;

    /**
     * DICOM SOP Class UID (0008,0016)
     * DICOM 표준의 이미지 타입 식별자 (CT, MR, US 등)
     */
    private final String sopClassUid;

    /**
     * 파일 크기 (bytes)
     *
     * <p>스트리밍 업로드를 위해 필요 (2026-01-05 추가)
     * <ul>
     *   <li>S3 PutObject에 Content-Length 필요</li>
     *   <li>RequestBody.fromInputStream() 사용 시 필수</li>
     *   <li>메모리 효율: readAllBytes() 제거</li>
     * </ul>
     */
    private final Long fileSize;
}
