package com.hanumoka.sado.common.exception;

import com.hanumoka.sado.common.code.ApiCode;
import com.hanumoka.sado.common.code.CommonCode;
import com.hanumoka.sado.common.dto.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * BusinessException 처리
     */
    @ExceptionHandler(BusinessException.class)
    protected ResponseEntity<ApiResponse<?>> handleBusinessException(BusinessException ex) {
        log.warn("BusinessException: {}", ex.getMessage());

        ApiCode apiCode = ex.getApiCode();
        return ResponseEntity
                .status(apiCode.getHttpStatus())
                .body(ApiResponse.of(apiCode));
    }

    /**
     * Validation 에러 처리
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    protected ResponseEntity<ApiResponse<?>> handleValidationException(
            MethodArgumentNotValidException ex) {
        log.warn("Validation Error: {}", ex.getMessage());

        return ResponseEntity
                .status(CommonCode.INVALID_PARAMETER.getHttpStatus())
                .body(ApiResponse.of(CommonCode.INVALID_PARAMETER));
    }

    /**
     * 모든 미처리 예외
     */
    @ExceptionHandler(Exception.class)
    protected ResponseEntity<ApiResponse<?>> handleException(Exception ex) {
        log.error("Unexpected Error", ex);

        return ResponseEntity
                .status(CommonCode.INTERNAL_SERVER_ERROR.getHttpStatus())
                .body(ApiResponse.of(CommonCode.INTERNAL_SERVER_ERROR));
    }

}
