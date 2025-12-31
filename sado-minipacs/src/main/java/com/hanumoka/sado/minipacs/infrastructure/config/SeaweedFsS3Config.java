package com.hanumoka.sado.minipacs.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.net.URI;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * SeaweedFS S3 API 설정
 *
 * <p>AWS SDK for Java v2를 사용하여 SeaweedFS S3 API에 연결합니다.
 *
 * <p>주요 설정:
 * <ul>
 *   <li>Path-style access 활성화 (http://localhost:10405/bucket/key)</li>
 *   <li>Custom endpoint 설정 (SeaweedFS Filer S3 API)</li>
 *   <li>로컬 개발용 인증 정보 (access-key: any, secret-key: any)</li>
 * </ul>
 *
 * <p>프로덕션 전환 (Week 9+):
 * <ul>
 *   <li>application.yml에서 endpoint만 변경: https://s3.ap-northeast-2.amazonaws.com</li>
 *   <li>IAM Role 또는 Access Key 사용</li>
 *   <li>pathStyleAccess: false (Virtual-hosted-style)</li>
 * </ul>
 */
@Configuration
@EnableConfigurationProperties(S3Properties.class)  // ⭐ S3Properties를 Spring에 등록 (경고 해결)
@RequiredArgsConstructor
@Slf4j
public class SeaweedFsS3Config {

    private final S3Properties s3Properties;

    /**
     * S3Client Bean 생성
     *
     * <p>AWS SDK for Java v2의 S3Client를 생성하여 Spring 컨테이너에 등록합니다.
     *
     * <p>설정 내용:
     * <ol>
     *   <li>Endpoint: SeaweedFS Filer S3 API (http://localhost:10405)</li>
     *   <li>Region: us-east-1 (AWS SDK 필수, SeaweedFS는 무시)</li>
     *   <li>Credentials: access-key=any, secret-key=any (로컬 개발용)</li>
     *   <li>Path-style access: true (버킷을 URL path에 포함)</li>
     * </ol>
     *
     * @return S3Client 인스턴스
     */
    @Bean
    public S3Client s3Client() {
        log.info("Initializing S3Client with endpoint: {}, bucket: {}, pathStyleAccess: {}",
                s3Properties.getEndpoint(),
                s3Properties.getBucket(),
                s3Properties.getPathStyleAccess());

        // 1. AWS 인증 정보 생성 (Access Key + Secret Key)
        AwsBasicCredentials credentials = AwsBasicCredentials.create(
                s3Properties.getAccessKey(),
                s3Properties.getSecretKey()
        );

        // 2. S3 클라이언트 설정
        S3Configuration s3Config = S3Configuration.builder()
                .pathStyleAccessEnabled(s3Properties.getPathStyleAccess())  // Path-style 활성화
                .build();

        // 3. S3Client 빌드
        S3Client s3Client = S3Client.builder()
                .endpointOverride(URI.create(s3Properties.getEndpoint()))  // SeaweedFS 엔드포인트
                .region(Region.of(s3Properties.getRegion()))                // 리전 설정
                .credentialsProvider(StaticCredentialsProvider.create(credentials))  // 인증 정보
                .serviceConfiguration(s3Config)                             // S3 설정 (path-style)
                .build();

        log.info("S3Client initialized successfully");
        return s3Client;
    }

    /**
     * S3Presigner Bean 생성
     *
     * <p>Pre-signed URL 생성을 위한 AWS SDK S3Presigner를 생성합니다.
     *
     * <p>용도:
     * <ul>
     *   <li>PresignedUrlAccessStrategy에서 사용</li>
     *   <li>Frontend가 SeaweedFS에 직접 접근할 수 있는 임시 URL 생성</li>
     *   <li>만료 시간: 1시간 (PresignedUrlAccessStrategy에서 설정)</li>
     * </ul>
     *
     * @return S3Presigner 인스턴스
     */
    @Bean
    public S3Presigner s3Presigner() {
        log.info("Initializing S3Presigner with endpoint: {}", s3Properties.getEndpoint());

        // 1. AWS 인증 정보 생성
        AwsBasicCredentials credentials = AwsBasicCredentials.create(
                s3Properties.getAccessKey(),
                s3Properties.getSecretKey()
        );

        // 2. S3Presigner 빌드
        S3Presigner presigner = S3Presigner.builder()
                .endpointOverride(URI.create(s3Properties.getEndpoint()))
                .region(Region.of(s3Properties.getRegion()))
                .credentialsProvider(StaticCredentialsProvider.create(credentials))
                .build();

        log.info("S3Presigner initialized successfully");
        return presigner;
    }
}
