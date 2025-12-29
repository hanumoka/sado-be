package com.hanumoka.sado.minipacs;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * MiniPACS Application
 *
 * DICOM 영상 관리 시스템 (Standalone PACS)
 *
 * [Entity 스캔 범위]
 * - 기본: @SpringBootApplication 패키지 및 하위 패키지 자동 스캔
 * - com.hanumoka.sado.minipacs.domain.entity.* (모든 @Entity 클래스)
 * - @MappedSuperclass (BaseEntity, TenantAwareEntity)는 자동 인식됨
 *
 * [컴포넌트 스캔 범위]
 * - com.hanumoka.sado.common.* (@Service, @Repository, @Component 등)
 * - com.hanumoka.sado.minipacs.* (@Controller, @Service, @Repository 등)
 */
@SpringBootApplication(scanBasePackages = {
    "com.hanumoka.sado.common",     // Common 모듈
    "com.hanumoka.sado.minipacs"    // MiniPACS 모듈
})
@EnableJpaAuditing  // JPA Auditing 활성화 (@CreatedDate, @LastModifiedDate)
public class MiniPacsApplication {

    public static void main(String[] args) {
        SpringApplication.run(MiniPacsApplication.class, args);
    }
}
