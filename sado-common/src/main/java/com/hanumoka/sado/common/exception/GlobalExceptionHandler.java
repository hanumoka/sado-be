package com.hanumoka.sado.common.exception;

import com.hanumoka.sado.common.code.ApiCode;
import com.hanumoka.sado.common.code.CommonCode;
import com.hanumoka.sado.common.dto.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;

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
     *
     * <p>Bean Validation (@Valid, @NotNull, @Size 등) 실패 시 필드별 에러 메시지를 반환합니다.
     *
     * <p>응답 예시:
     * <pre>
     * {
     *   "code": 400001,
     *   "message": "Invalid parameter",
     *   "data": {
     *     "dicomPatientId": "must not be blank",
     *     "patientBirthDate": "must be a past date"
     *   }
     * }
     * </pre>
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    protected ResponseEntity<ApiResponse<?>> handleValidationException(
            MethodArgumentNotValidException ex) {

        // 필드별 에러 메시지 수집 (LinkedHashMap으로 순서 유지)
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(error ->
                fieldErrors.put(error.getField(), error.getDefaultMessage())
        );

        log.warn("Validation failed: {}", fieldErrors);

        return ResponseEntity
                .status(CommonCode.INVALID_PARAMETER.getHttpStatus())
                .body(ApiResponse.of(CommonCode.INVALID_PARAMETER, fieldErrors));
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
