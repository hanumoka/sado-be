package com.hanumoka.sado.common.code;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum CommonCode implements ApiCode {

    SUCCESS(HttpStatus.OK, 200000, "Success"),
    INVALID_PARAMETER(HttpStatus.BAD_REQUEST, 400001, "Invalid parameter"),
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, 404001, "Resource not found"),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, 500001, "Internal server error");

    private final HttpStatus httpStatus;
    private final int code;
    private final String message;

    CommonCode(HttpStatus httpStatus, int code, String message) {
        this.httpStatus = httpStatus;
        this.code = code;
        this.message = message;
    }

}
