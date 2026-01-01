package com.hanumoka.sado.minipacs.infrastructure.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Jackson ObjectMapper 설정
 * - Java 8 Date/Time API 지원
 * - ISO-8601 형식으로 날짜 직렬화
 */
@Configuration
public class JacksonConfig {

    @Bean
    public ObjectMapper objectMapper() {
        ObjectMapper objectMapper = new ObjectMapper();

        // Java 8 Date/Time API 지원 (LocalDate, LocalDateTime 등)
        objectMapper.registerModule(new JavaTimeModule());

        // 날짜를 timestamp 대신 ISO-8601 문자열로 직렬화
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        return objectMapper;
    }
}
