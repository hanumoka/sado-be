package com.hanumoka.sado.minipacs.dto.request;

import lombok.Getter;
import lombok.Setter;

/**
 * 영상 생성 요청 DTO
 */
@Getter
@Setter
public class CreateInstanceRequest {

    // 1. 소속 시리즈 ID (필수)
    private Long seriesId;

    // 2. DICOM UID (선택)
    private String sopInstanceUid;
    private String sopClassUid;

    // 3. 이미지 정보 (선택)
    private Integer rows;
    private Integer columns;
    private Integer numberOfFrames;
    private Double frameRate;
    private String frameRateSource;
    private Integer instanceNumber;

    // 4. 스토리지 (선택)
    private String storagePath;
    private Long fileSize;
}
