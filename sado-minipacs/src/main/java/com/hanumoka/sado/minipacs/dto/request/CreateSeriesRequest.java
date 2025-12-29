package com.hanumoka.sado.minipacs.dto.request;

import lombok.Getter;
import lombok.Setter;

/**
 * 시리즈 생성 요청 DTO
 */
@Getter
@Setter
public class CreateSeriesRequest {

    // 1. 소속 검사 ID (필수)
    private Long studyId;

    // 2. DICOM SeriesInstanceUID (선택)
    private String seriesInstanceUid;

    // 3. Modality (선택) - US, CT, MR 등
    private String modality;

    // 4. 시리즈 설명 (선택)
    private String seriesDescription;

    // 5. 검사 부위 (선택)
    private String bodyPartExamined;

    // 6. 제조사 (선택)
    private String manufacturer;

    // 7. 제조사 모델명 (선택)
    private String manufacturerModelName;

    // 8. 시리즈 번호 (선택)
    private Integer seriesNumber;
}
