package com.hanumoka.sado.common.exception;

import com.hanumoka.sado.common.code.ApiCode;
import lombok.Getter;

@Getter
public class BusinessException extends RuntimeException {
    private final ApiCode apiCode;

    public BusinessException(ApiCode apiCode, String message) {
        super(message);
        this.apiCode = apiCode;
    }

    public BusinessException(ApiCode apiCode) {
        super(apiCode.getMessage());
        this.apiCode = apiCode;
    }
}
