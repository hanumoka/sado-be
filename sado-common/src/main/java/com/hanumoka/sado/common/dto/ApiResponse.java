package com.hanumoka.sado.common.dto;

import com.hanumoka.sado.common.code.ApiCode;
import com.hanumoka.sado.common.code.CommonCode;
import lombok.Builder;
import lombok.Getter;

@Builder
@Getter
public class ApiResponse<T> {
    private final ApiCode type;
    private final Integer code;
    private final String message;
    private final T data;

    // 성공 여부 판단 (HttpStatus 기반)
    public boolean isSuccess() {
        return this.type.getHttpStatus().is2xxSuccessful();
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
