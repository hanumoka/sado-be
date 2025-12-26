package com.hanumoka.sado.common.code;

import org.springframework.http.HttpStatus;

public interface ApiCode {
    String name();
    HttpStatus getHttpStatus();
    String getMessage();
    int getCode();
}
