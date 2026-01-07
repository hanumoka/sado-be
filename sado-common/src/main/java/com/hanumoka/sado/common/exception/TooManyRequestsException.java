package com.hanumoka.sado.common.exception;

import com.hanumoka.sado.common.code.CommonCode;
import lombok.Getter;

/**
 * 429 Too Many Requests 예외
 *
 * <p>동시 요청 제한 초과 시 발생합니다.
 *
 * <p>특징:
 * <ul>
 *   <li>retryAfterSeconds: 클라이언트가 재시도해야 하는 대기 시간</li>
 *   <li>GlobalExceptionHandler에서 Retry-After 헤더로 변환</li>
 * </ul>
 */
@Getter
public class TooManyRequestsException extends BusinessException {

    /**
     * 클라이언트가 재시도까지 대기해야 하는 시간 (초)
     */
    private final int retryAfterSeconds;

    public TooManyRequestsException(String message, int retryAfterSeconds) {
        super(CommonCode.TOO_MANY_REQUESTS, message);
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public TooManyRequestsException(int retryAfterSeconds) {
        super(CommonCode.TOO_MANY_REQUESTS);
        this.retryAfterSeconds = retryAfterSeconds;
    }
}
