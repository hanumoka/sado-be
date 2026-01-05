package com.hanumoka.sado.minipacs.storage.strategy;

import com.hanumoka.sado.minipacs.storage.dto.DicomFileMetadata;

/**
 * Storage 경로 생성 전략 인터페이스
 *
 * 다양한 경로 생성 전략을 구현할 수 있도록 추상화
 *
 * <p>구현 전략:
 * <ul>
 *   <li>{@link DicomwebPathStrategy}: DICOMweb 표준 경로</li>
 *   <li>{@link DateBasedPathStrategy}: 날짜 기반 경로</li>
 *   <li>향후 추가: UUID 기반, 커스텀 경로 등</li>
 * </ul>
 *
 * <p>설계 패턴: Strategy Pattern
 * - 경로 생성 알고리즘을 캡슐화
 * - 런타임에 전략 교체 가능
 * - 새로운 전략 추가 시 기존 코드 수정 불필요 (OCP)
 */
public interface StoragePathStrategy {

    /**
     * DICOM 파일의 저장 경로 생성
     *
     * @param metadata DICOM 파일 메타데이터 (추상화된 키)
     * @return 생성된 파일 경로 (상대 경로)
     * @throws IllegalArgumentException metadata가 null이거나 필수 필드가 없는 경우
     */
    String generatePath(DicomFileMetadata metadata);

    /**
     * 전략 이름 반환 (로깅/디버깅용)
     *
     * @return 전략 이름 (예: "DICOMweb Standard", "Date-based")
     */
    default String getStrategyName() {
        return this.getClass().getSimpleName();
    }
}
