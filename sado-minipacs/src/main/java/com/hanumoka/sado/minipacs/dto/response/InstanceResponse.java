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
    private String storagePath;
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
