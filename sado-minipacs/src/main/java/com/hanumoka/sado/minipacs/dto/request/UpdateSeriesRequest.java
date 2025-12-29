package com.hanumoka.sado.minipacs.dto.request;

import lombok.Getter;
import lombok.Setter;

/**
 * 시리즈 수정 요청 DTO
 */
@Getter
@Setter
public class UpdateSeriesRequest {

    // 모든 필드 선택(Optional) - 부분 업데이트 지원

    // 1. DICOM SeriesInstanceUID (선택)
    private String seriesInstanceUid;

    // 2. Modality (선택)
    private String modality;

    // 3. 시리즈 설명 (선택)
    private String seriesDescription;

    // 4. 검사 부위 (선택)
    private String bodyPartExamined;

    // 5. 제조사 (선택)
    private String manufacturer;

    // 6. 제조사 모델명 (선택)
    private String manufacturerModelName;

    // 7. 시리즈 번호 (선택)
    private Integer seriesNumber;
}
