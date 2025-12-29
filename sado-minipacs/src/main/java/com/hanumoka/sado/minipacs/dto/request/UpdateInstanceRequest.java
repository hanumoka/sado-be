package com.hanumoka.sado.minipacs.dto.request;

import lombok.Getter;
import lombok.Setter;

/**
 * 영상 수정 요청 DTO
 */
@Getter
@Setter
public class UpdateInstanceRequest {

    // 모든 필드 선택(Optional) - 부분 업데이트 지원

    // 1. DICOM UID (선택)
    private String sopInstanceUid;
    private String sopClassUid;

    // 2. 이미지 정보 (선택)
    private Integer rows;
    private Integer columns;
    private Integer numberOfFrames;
    private Double frameRate;
    private String frameRateSource;
    private Integer instanceNumber;

    // 3. 스토리지 (선택)
    private String storagePath;
    private Long fileSize;

    // 4. 트랜스코딩 (선택)
    private String transcodingStatus; // NONE, PENDING, PROCESSING, COMPLETED, FAILED
    private String thumbnailPath;
    private String videoPath;

    // 5. 스토리지 티어 (선택)
    private String storageTier; // HOT, WARM, COLD
}
