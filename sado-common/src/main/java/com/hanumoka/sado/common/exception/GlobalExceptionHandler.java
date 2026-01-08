package com.hanumoka.sado.common.exception;

import com.hanumoka.sado.common.code.ApiCode;
import com.hanumoka.sado.common.code.CommonCode;
import com.hanumoka.sado.common.dto.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.TypeMismatchException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 현재 요청 정보를 상세하게 로깅합니다.
     *
     * @param ex 발생한 예외
     * @param level 로그 레벨 ("WARN" 또는 "ERROR")
     */
    private void logRequestDetails(Exception ex, String level) {
        try {
            ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs == null) {
                log.warn("RequestContextHolder is empty - cannot log request details");
                return;
            }

            HttpServletRequest request = attrs.getRequest();

            StringBuilder sb = new StringBuilder();
            sb.append("\n========== Exception Details ==========\n");
            sb.append("Exception Type: ").append(ex.getClass().getName()).append("\n");
            sb.append("Exception Message: ").append(ex.getMessage()).append("\n");
            sb.append("\n---------- Request Info ----------\n");
            sb.append("URL: ").append(request.getMethod()).append(" ").append(request.getRequestURI()).append("\n");
            sb.append("Query String: ").append(request.getQueryString()).append("\n");
            sb.append("Remote Address: ").append(request.getRemoteAddr()).append("\n");

            // Headers
            sb.append("\n---------- Headers ----------\n");
            Collections.list(request.getHeaderNames()).forEach(headerName ->
                    sb.append(headerName).append(": ").append(request.getHeader(headerName)).append("\n")
            );

            // Parameters
            sb.append("\n---------- Parameters ----------\n");
            request.getParameterMap().forEach((key, values) ->
                    sb.append(key).append(": ").append(String.join(", ", values)).append("\n")
            );

            sb.append("========================================\n");

            if ("ERROR".equals(level)) {
                log.error(sb.toString(), ex);
            } else {
                log.warn(sb.toString());
            }
        } catch (Exception e) {
            log.error("Failed to log request details", e);
        }
    }

    /**
     * TooManyRequestsException 처리 (429 Too Many Requests)
     *
     * <p>Retry-After 헤더를 포함하여 클라이언트에게 재시도 시간을 안내합니다.
     */
    @ExceptionHandler(TooManyRequestsException.class)
    protected ResponseEntity<ApiResponse<?>> handleTooManyRequestsException(TooManyRequestsException ex) {
        logRequestDetails(ex, "WARN");

        ApiCode apiCode = ex.getApiCode();
        return ResponseEntity
                .status(apiCode.getHttpStatus())
                .header(HttpHeaders.RETRY_AFTER, String.valueOf(ex.getRetryAfterSeconds()))
                .body(ApiResponse.of(apiCode, Map.of(
                        "retryAfterSeconds", ex.getRetryAfterSeconds(),
                        "message", ex.getMessage()
                )));
    }

    /**
     * BusinessException 처리
     */
    @ExceptionHandler(BusinessException.class)
    protected ResponseEntity<ApiResponse<?>> handleBusinessException(BusinessException ex) {
        logRequestDetails(ex, "WARN");

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

        logRequestDetails(ex, "WARN");

        // 필드별 에러 메시지 수집 (LinkedHashMap으로 순서 유지)
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(error ->
                fieldErrors.put(error.getField(), error.getDefaultMessage())
        );

        log.warn("Validation field errors: {}", fieldErrors);

        return ResponseEntity
                .status(CommonCode.INVALID_PARAMETER.getHttpStatus())
                .body(ApiResponse.of(CommonCode.INVALID_PARAMETER, fieldErrors));
    }

    /**
     * 필수 파라미터 누락 처리 (400 Bad Request)
     *
     * <p>@RequestParam(required = true)인 파라미터가 누락된 경우 발생합니다.
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    protected ResponseEntity<ApiResponse<?>> handleMissingServletRequestParameter(
            MissingServletRequestParameterException ex) {
        logRequestDetails(ex, "WARN");
        log.warn("Missing parameter details - name: {}, type: {}", ex.getParameterName(), ex.getParameterType());

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.of(CommonCode.MISSING_PARAMETER,
                        Map.of("parameter", ex.getParameterName(),
                                "type", ex.getParameterType(),
                                "message", "Required parameter '" + ex.getParameterName() + "' is missing")));
    }

    /**
     * 타입 변환 실패 처리 (400 Bad Request)
     *
     * <p>요청 파라미터의 타입 변환이 실패한 경우 발생합니다.
     * 예: String을 Integer로 변환 실패
     */
    @ExceptionHandler(TypeMismatchException.class)
    protected ResponseEntity<ApiResponse<?>> handleTypeMismatch(TypeMismatchException ex) {
        logRequestDetails(ex, "WARN");
        log.warn("Type mismatch details - property: {}, value: {}, requiredType: {}",
                ex.getPropertyName(), ex.getValue(), ex.getRequiredType());

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.of(CommonCode.INVALID_PARAMETER,
                        Map.of("property", String.valueOf(ex.getPropertyName()),
                                "value", String.valueOf(ex.getValue()),
                                "message", "Invalid value for parameter '" + ex.getPropertyName() + "'")));
    }

    /**
     * Constraint 검증 실패 처리 (400 Bad Request)
     *
     * <p>@Pattern, @Min, @Max 등 제약 조건 검증이 실패한 경우 발생합니다.
     * 주로 @Validated가 적용된 Controller에서 @RequestParam 검증 시 발생합니다.
     */
    @ExceptionHandler(ConstraintViolationException.class)
    protected ResponseEntity<ApiResponse<?>> handleConstraintViolation(ConstraintViolationException ex) {
        logRequestDetails(ex, "WARN");

        Map<String, String> violations = ex.getConstraintViolations().stream()
                .collect(Collectors.toMap(
                        v -> v.getPropertyPath().toString(),
                        v -> v.getMessage(),
                        (existing, replacement) -> existing,
                        LinkedHashMap::new
                ));

        log.warn("Constraint violation details: {}", violations);

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.of(CommonCode.INVALID_PARAMETER, violations));
    }

    /**
     * HTTP 메서드 미지원 처리 (405 Method Not Allowed)
     *
     * <p>지원하지 않는 HTTP 메서드로 요청한 경우 발생합니다.
     * 예: GET만 지원하는 엔드포인트에 POST 요청
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    protected ResponseEntity<ApiResponse<?>> handleHttpRequestMethodNotSupported(
            HttpRequestMethodNotSupportedException ex) {
        logRequestDetails(ex, "WARN");
        log.warn("Method not supported details - method: {}, supported: {}", ex.getMethod(), ex.getSupportedHttpMethods());

        return ResponseEntity
                .status(HttpStatus.METHOD_NOT_ALLOWED)
                .body(ApiResponse.of(CommonCode.METHOD_NOT_ALLOWED,
                        Map.of("method", ex.getMethod(),
                                "supported", ex.getSupportedHttpMethods() != null ?
                                        ex.getSupportedHttpMethods().toString() : "[]")));
    }

    /**
     * Accept 헤더 불일치 처리 (406 Not Acceptable)
     *
     * <p>클라이언트의 Accept 헤더와 서버의 produces 설정이 일치하지 않는 경우 발생합니다.
     * 예: Accept: application/xml 요청 → produces: application/json 응답
     */
    @ExceptionHandler(HttpMediaTypeNotAcceptableException.class)
    protected ResponseEntity<ApiResponse<?>> handleHttpMediaTypeNotAcceptable(
            HttpMediaTypeNotAcceptableException ex) {
        logRequestDetails(ex, "WARN");
        log.warn("Media type not acceptable details - supported: {}", ex.getSupportedMediaTypes());

        return ResponseEntity
                .status(HttpStatus.NOT_ACCEPTABLE)
                .body(ApiResponse.of(CommonCode.NOT_ACCEPTABLE,
                        Map.of("supported", ex.getSupportedMediaTypes().toString(),
                                "message", "Requested media type is not supported")));
    }

    /**
     * Content-Type 미지원 처리 (415 Unsupported Media Type)
     *
     * <p>요청의 Content-Type이 서버에서 처리할 수 없는 경우 발생합니다.
     * 예: Content-Type: text/plain → consumes: application/json
     */
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    protected ResponseEntity<ApiResponse<?>> handleHttpMediaTypeNotSupported(
            HttpMediaTypeNotSupportedException ex) {
        logRequestDetails(ex, "WARN");
        log.warn("Media type not supported details - contentType: {}, supported: {}",
                ex.getContentType(), ex.getSupportedMediaTypes());

        return ResponseEntity
                .status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
                .body(ApiResponse.of(CommonCode.UNSUPPORTED_MEDIA_TYPE,
                        Map.of("contentType", String.valueOf(ex.getContentType()),
                                "supported", ex.getSupportedMediaTypes().toString())));
    }

    /**
     * 모든 미처리 예외 (500 Internal Server Error)
     *
     * <p>위의 핸들러에서 처리되지 않은 모든 예외는 여기서 처리됩니다.
     * 운영 환경에서는 상세 에러 메시지를 노출하지 않습니다.
     */
    @ExceptionHandler(Exception.class)
    protected ResponseEntity<ApiResponse<?>> handleException(Exception ex) {
        // 상세 요청 정보 및 스택 트레이스 로깅
        logRequestDetails(ex, "ERROR");

        return ResponseEntity
                .status(CommonCode.INTERNAL_SERVER_ERROR.getHttpStatus())
                .body(ApiResponse.of(CommonCode.INTERNAL_SERVER_ERROR));
    }

}
