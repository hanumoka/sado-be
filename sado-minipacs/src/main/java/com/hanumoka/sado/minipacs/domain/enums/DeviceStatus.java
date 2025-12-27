package com.hanumoka.sado.minipacs.domain.enums;

public enum DeviceStatus {
    ACTIVE, // 활성화된 장비(API 호출가능)
    INACTIVE, // 비활성화 (일시중지)
    REVOKED // 인증 취소됨(영구 차단)
}
