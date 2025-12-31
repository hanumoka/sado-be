package com.hanumoka.sado.minipacs.infrastructure;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.net.URI;

@Configuration
public class SeaweedFsS3Config {

    @Value("${seaweedfs.s3.endpoint}")
    private String s3Endpoint;

    @Value("${seaweedfs.s3.region}")
    private String region;

    @Value("${seaweedfs.s3.access-key}")
    private String accessKey;

    @Value("${seaweedfs.s3.secret-key}")
    private String secretKey;

    /**
     * S3Client Bean
     * - DICOM 파일 업로드/다운로드/삭제
     * - SeaweedFS Filer S3 API 연동
     */
    @Bean
    public S3Client s3Client() {
        AwsBasicCredentials credentials = AwsBasicCredentials.create(accessKey, secretKey);

        S3Configuration s3Config = S3Configuration.builder()
                .pathStyleAccessEnabled(true)  // SeaweedFS 필수: http://localhost:10405/bucket/key
                .build();

        return S3Client.builder()
                .endpointOverride(URI.create(s3Endpoint))
                .region(Region.of(region))
                .credentialsProvider(StaticCredentialsProvider.create(credentials))
                .serviceConfiguration(s3Config)
                .build();
    }

    /**
     * S3Presigner Bean
     * - Pre-signed URL 생성 (OHIF Viewer용)
     * - 시간 제한 보안 URL
     */
    @Bean
    public S3Presigner s3Presigner() {
        AwsBasicCredentials credentials = AwsBasicCredentials.create(accessKey, secretKey);

        return S3Presigner.builder()
                .endpointOverride(URI.create(s3Endpoint))
                .region(Region.of(region))
                .credentialsProvider(StaticCredentialsProvider.create(credentials))
                .build();
    }
}
