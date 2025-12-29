package com.hanumoka.sado.common.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.hanumoka.sado.common.code.ApiCode;
import com.hanumoka.sado.common.code.CommonCode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Builder
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class ApiResponse<T> {
    @JsonIgnore  // Jackson 역직렬화에서 제외 (인터페이스 타입이라 역직렬화 불가)
    private ApiCode type;
    private Integer code;
    private String message;
    private T data;

    // 성공 여부 판단 (API 응답 코드 기반)
    public boolean isSuccess() {
        // API 코드 체계: 2xxxxx = 성공, 4xxxxx = 클라이언트 에러, 5xxxxx = 서버 에러
        return this.code != null && this.code >= 200000 && this.code < 300000;
    }

    // ========== 팩토리 메서드 ==========

    /**
     * 성공 응답 (데이터 없음)
     */
    public static ApiResponse<Void> success() {
        return ApiResponse.<Void>builder()
                .type(CommonCode.SUCCESS)
                .code(CommonCode.SUCCESS.getCode())
                .message(CommonCode.SUCCESS.getMessage())
                .data(null)
                .build();
    }

    /**
     * 성공 응답 (데이터 포함)
     */
    public static <T> ApiResponse<T> success(T data) {
        return ApiResponse.<T>builder()
                .type(CommonCode.SUCCESS)
                .code(CommonCode.SUCCESS.getCode())
                .message(CommonCode.SUCCESS.getMessage())
                .data(data)
                .build();
    }

    /**
     * ApiCode 기반 응답
     */
    public static <T> ApiResponse<T> of(ApiCode code) {
        return ApiResponse.<T>builder()
                .type(code)
                .code(code.getCode())
                .message(code.getMessage())
                .data(null)
                .build();
    }

    /**
     * ApiCode 기반 응답 (데이터 포함)
     */
    public static <T> ApiResponse<T> of(ApiCode code, T data) {
        return ApiResponse.<T>builder()
                .type(code)
                .code(code.getCode())
                .message(code.getMessage())
                .data(data)
                .build();
    }

}
