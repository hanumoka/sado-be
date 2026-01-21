package com.hanumoka.sado.minipacs.infrastructure.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web MVC 설정
 *
 * <p>CORS, 인터셉터 등 Web MVC 관련 설정을 관리합니다.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Value("${spring.profiles.active:dev}")
    private String activeProfile;

    @Value("${cors.allowed-origins:http://localhost:10300,http://localhost:3000}")
    private String[] allowedOrigins;

    /**
     * CORS 설정
     *
     * <p>DICOMweb API 및 REST API에 대한 CORS 설정:
     * <ul>
     *   <li>개발 환경: 모든 origin 허용 (IP 접속 테스트용)</li>
     *   <li>운영 환경: 지정된 origin만 허용</li>
     *   <li>OHIF Viewer 호환 헤더 설정</li>
     *   <li>Pre-flight 요청 캐시 (1시간)</li>
     * </ul>
     */
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        boolean isDevMode = "dev".equals(activeProfile) || "local".equals(activeProfile);

        // DICOMweb API CORS 설정
        var dicomwebMapping = registry.addMapping("/dicomweb/**")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .exposedHeaders(
                        "Content-Type",
                        "Content-Length",
                        "Content-Disposition",
                        "Accept",
                        "X-Request-ID",
                        "X-Tenant-Id",
                        // WADO-RS BulkData 지원을 위한 DICOM 메타데이터 헤더
                        "X-DICOM-TransferSyntax",
                        "X-DICOM-PhotometricInterpretation",
                        "X-DICOM-BitsAllocated",
                        "X-DICOM-BitsStored"
                )
                .allowCredentials(true)
                .maxAge(3600);  // Pre-flight 캐시 1시간

        // 개발 환경: 모든 origin 허용 (allowedOriginPatterns 사용)
        if (isDevMode) {
            dicomwebMapping.allowedOriginPatterns("*");
        } else {
            dicomwebMapping.allowedOrigins(allowedOrigins);
        }

        // REST API CORS 설정
        var apiMapping = registry.addMapping("/api/**")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS")
                .allowedHeaders("*")
                .exposedHeaders(
                        "Content-Type",
                        "Content-Length",
                        "Content-Disposition",
                        "X-Total-Count",
                        "X-Request-ID",
                        "X-Tenant-Id"
                )
                .allowCredentials(true)
                .maxAge(3600);

        if (isDevMode) {
            apiMapping.allowedOriginPatterns("*");
        } else {
            apiMapping.allowedOrigins(allowedOrigins);
        }

        // Swagger UI CORS 설정 (개발 환경만)
        if (isDevMode) {
            registry.addMapping("/swagger-ui/**")
                    .allowedOriginPatterns("*")
                    .allowedMethods("GET", "OPTIONS");

            registry.addMapping("/api-docs/**")
                    .allowedOriginPatterns("*")
                    .allowedMethods("GET", "OPTIONS");
        }
    }
}
