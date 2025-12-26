package com.hanumoka.sado.common.exception;

import com.hanumoka.sado.common.code.CommonCode;

public class ResourceNotFoundException extends BusinessException {
    public ResourceNotFoundException(String message) {
        super(CommonCode.RESOURCE_NOT_FOUND, message);
    }

    public ResourceNotFoundException() {
        super(CommonCode.RESOURCE_NOT_FOUND);
    }
}
