package com.hanumoka.sado.common.code;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum CommonCode implements ApiCode {

    // 2xx Success
    SUCCESS(HttpStatus.OK, 200000, "Success"),

    // 4xx Client Errors
    INVALID_PARAMETER(HttpStatus.BAD_REQUEST, 400001, "Invalid parameter"),
    MISSING_PARAMETER(HttpStatus.BAD_REQUEST, 400002, "Missing required parameter"),
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, 404001, "Resource not found"),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, 405001, "Method not allowed"),
    NOT_ACCEPTABLE(HttpStatus.NOT_ACCEPTABLE, 406001, "Not acceptable media type"),
    UNSUPPORTED_MEDIA_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE, 415001, "Unsupported media type"),
    TOO_MANY_REQUESTS(HttpStatus.TOO_MANY_REQUESTS, 429001, "Too many requests. Please try again later."),

    // 5xx Server Errors
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
