package com.hanumoka.sado.minipacs.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 영상 응답 DTO
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InstanceResponse {

    // 1. PK
    private Long id;

    // 2. 소속 시리즈 ID
    private Long seriesId;

    // 3. DICOM 식별자
    private String sopInstanceUid;
    private String sopClassUid;

    // 4. 이미지 정보
    private Integer rows;
    private Integer columns;
    private Integer numberOfFrames;
    private Double frameRate;
    private String frameRateSource;
    private Integer instanceNumber;

    // 5. 스토리지
    private String storagePath;  // S3 Key

    /**
     * Pre-signed URL (Frontend DICOM Viewer용)
     * <p>
     * <b>접근 방식:</b>
     * <ul>
     *   <li><b>Pre-signed URL 방식</b> (POC 기본값): 유효 기간 1시간, Frontend가 SeaweedFS에 직접 접근</li>
     *   <li><b>Backend Proxy 방식</b> (프로덕션): 무제한, Frontend가 Backend 경유</li>
     * </ul>
     *
     * <p><b>설정:</b> application.yml의 storage.access-strategy 값으로 전환 (코드 변경 0줄)
     *
     * <p><b>주의:</b> 예외 발생 시 빈 문자열("")로 반환되며, null이 아닙니다.
     */
    private String storageUri;

    private Long fileSize;

    // 6. 트랜스코딩
    private String transcodingStatus;
    private String thumbnailPath;
    private String videoPath;

    // 7. 스토리지 티어
    private String storageTier;

    // 8. 감사 필드
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // 9. 멀티테넌시
    private Long tenantId;
}
