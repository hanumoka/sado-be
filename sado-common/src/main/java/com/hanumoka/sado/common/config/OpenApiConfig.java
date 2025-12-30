package com.hanumoka.sado.common.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.media.IntegerSchema;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI 3.0 Configuration
 *
 * 모든 SADO 모듈에서 공유하는 Swagger/OpenAPI 설정
 */
@Configuration
public class OpenApiConfig {

    @Value("${springdoc.api-title:SADO API}")
    private String apiTitle;

    @Value("${springdoc.api-description:SADO Project REST API}")
    private String apiDescription;

    @Value("${springdoc.api-version:0.0.1-SNAPSHOT}")
    private String apiVersion;

    @Value("${springdoc.api-contact-name:SADO Team}")
    private String contactName;

    @Value("${springdoc.api-contact-email:dev@hanumoka.com}")
    private String contactEmail;

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title(apiTitle)
                        .description(apiDescription)
                        .version(apiVersion)
                        .contact(new Contact()
                                .name(contactName)
                                .email(contactEmail)
                        )
                )
                .components(new Components()
                        .addSchemas("ApiResponse", new Schema<>()
                                .type("object")
                                .description("표준 API 응답 래퍼")
                                .addProperty("code", new IntegerSchema()
                                        .description("응답 코드 (2xxxxx=성공, 4xxxxx=클라이언트 오류, 5xxxxx=서버 오류)")
                                        .example(200000))
                                .addProperty("message", new StringSchema()
                                        .description("응답 메시지")
                                        .example("Success"))
                                .addProperty("data", new Schema<>()
                                        .description("응답 데이터 (제네릭 타입 T)")
                                        .nullable(true))
                        )
                );
    }
}
