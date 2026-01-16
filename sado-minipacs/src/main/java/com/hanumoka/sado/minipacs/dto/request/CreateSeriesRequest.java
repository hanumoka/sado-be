package com.hanumoka.sado.minipacs.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * 시리즈 생성 요청 DTO
 */
@Getter
@Setter
public class CreateSeriesRequest {

    // 1. 소속 검사 ID (필수)
    @NotNull(message = "검사 ID는 필수입니다")
    private Long studyId;

    // 2. DICOM SeriesInstanceUID (선택) - OID 형식, 최대 64자
    @Size(max = 64, message = "Series Instance UID는 최대 64자까지 가능합니다")
    @Pattern(regexp = "^([0-9]+(\\.[0-9]+)*)?$", message = "Series Instance UID는 유효한 DICOM UID 형식이어야 합니다")
    private String seriesInstanceUid;

    // 3. Modality (선택) - DICOM CS VR 최대 16자, 대문자
    @Size(max = 16, message = "Modality는 최대 16자까지 가능합니다")
    @Pattern(regexp = "^[A-Z0-9]*$", message = "Modality는 대문자와 숫자만 가능합니다")
    private String modality;

    // 4. 시리즈 설명 (선택) - DICOM LO VR 최대 64자
    @Size(max = 64, message = "시리즈 설명은 최대 64자까지 가능합니다")
    private String seriesDescription;

    // 5. 검사 부위 (선택) - DICOM CS VR 최대 16자
    @Size(max = 16, message = "검사 부위는 최대 16자까지 가능합니다")
    private String bodyPartExamined;

    // 6. 제조사 (선택) - DICOM LO VR 최대 64자
    @Size(max = 64, message = "제조사는 최대 64자까지 가능합니다")
    private String manufacturer;

    // 7. 제조사 모델명 (선택) - DICOM LO VR 최대 64자
    @Size(max = 64, message = "제조사 모델명은 최대 64자까지 가능합니다")
    private String manufacturerModelName;

    // 8. 시리즈 번호 (선택) - 양수
    @Min(value = 0, message = "시리즈 번호는 0 이상이어야 합니다")
    private Integer seriesNumber;
}
