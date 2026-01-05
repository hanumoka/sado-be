package com.hanumoka.sado.minipacs.storage.strategy;

import com.hanumoka.sado.minipacs.storage.dto.DicomFileMetadata;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * 날짜 기반 경로 생성 전략
 *
 * <p>경로 구조:
 * <pre>
 * {year}/{month}/{day}/{sopInstanceUid}.dcm
 * </pre>
 *
 * <p>예시:
 * <pre>
 * 2026/01/05/1.2.840.113619.2.55.1.789.dcm
 * </pre>
 *
 * <p>장점:
 * <ul>
 *   <li>날짜별 디렉토리 정리 용이</li>
 *   <li>오래된 데이터 아카이빙 편리</li>
 *   <li>경로 길이 짧음</li>
 *   <li>날짜별 통계/모니터링 쉬움</li>
 * </ul>
 *
 * <p>단점:
 * <ul>
 *   <li>DICOMweb 표준 미준수</li>
 *   <li>Study/Series 계층 구조 손실</li>
 *   <li>같은 날짜의 파일이 한 디렉토리에 모임</li>
 * </ul>
 *
 * <p>사용 시나리오:
 * - 날짜별 백업/아카이빙이 중요한 경우
 * - DICOMweb API 사용하지 않는 경우
 * - 파일 시스템 탐색 편의성 우선
 */
@Slf4j
@Component("dateBasedPathStrategy")
public class DateBasedPathStrategy implements StoragePathStrategy {

    @Override
    public String generatePath(DicomFileMetadata metadata) {
        // Null 체크
        if (metadata == null) {
            throw new IllegalArgumentException("DicomFileMetadata cannot be null");
        }

        if (metadata.getSopInstanceUid() == null || metadata.getSopInstanceUid().isEmpty()) {
            throw new IllegalArgumentException("SopInstanceUid cannot be null or empty");
        }

        // 현재 날짜 기반 경로 생성
        LocalDate now = LocalDate.now();
        String path = String.format("%d/%02d/%02d/%s.dcm",
                now.getYear(),
                now.getMonthValue(),
                now.getDayOfMonth(),
                metadata.getSopInstanceUid());

        log.debug("Generated date-based path: {}", path);

        return path;
    }

    @Override
    public String getStrategyName() {
        return "Date-based Path Strategy";
    }
}
