package com.hanumoka.sado.minipacs.infrastructure.config;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.ListBucketsResponse;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.net.URI;
import java.time.Duration;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.core.retry.RetryPolicy;
import software.amazon.awssdk.core.retry.backoff.FullJitterBackoffStrategy;
import software.amazon.awssdk.core.retry.conditions.RetryCondition;

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
     *   <li>Timeout: apiCallTimeout (전체), apiCallAttemptTimeout (개별)</li>
     *   <li>Retry: maxRetries, exponential backoff</li>
     * </ol>
     *
     * <p>타임아웃/재시도 설정 (2026-01-05 추가):
     * <ul>
     *   <li>apiCallTimeout: 전체 API 호출 타임아웃 (기본 5분)</li>
     *   <li>apiCallAttemptTimeout: 개별 시도 타임아웃 (기본 60초)</li>
     *   <li>maxRetries: 최대 재시도 횟수 (기본 3회)</li>
     *   <li>Exponential backoff: 500ms ~ 10s</li>
     * </ul>
     *
     * @return S3Client 인스턴스
     */
    @Bean
    public S3Client s3Client() {
        log.info("Initializing S3Client with endpoint: {}, bucket: {}, pathStyleAccess: {}, " +
                        "apiCallTimeout: {}ms, apiCallAttemptTimeout: {}ms, maxRetries: {}",
                s3Properties.getEndpoint(),
                s3Properties.getBucket(),
                s3Properties.getPathStyleAccess(),
                s3Properties.getApiCallTimeout(),
                s3Properties.getApiCallAttemptTimeout(),
                s3Properties.getMaxRetries());

        // 1. AWS 인증 정보 생성 (Access Key + Secret Key)
        AwsBasicCredentials credentials = AwsBasicCredentials.create(
                s3Properties.getAccessKey(),
                s3Properties.getSecretKey()
        );

        // 2. S3 클라이언트 설정
        S3Configuration s3Config = S3Configuration.builder()
                .pathStyleAccessEnabled(s3Properties.getPathStyleAccess())  // Path-style 활성화
                .build();

        // 3. 재시도 정책 설정
        // - Full Jitter Backoff: 500ms ~ 10s (AWS 권장 전략)
        // - 일시적 오류 (503, 429, 네트워크 오류) 시 재시도
        // - Jitter로 thundering herd 문제 방지
        FullJitterBackoffStrategy backoffStrategy = FullJitterBackoffStrategy.builder()
                .baseDelay(Duration.ofMillis(500))    // 초기 지연: 500ms
                .maxBackoffTime(Duration.ofSeconds(10)) // 최대 지연: 10s
                .build();

        RetryPolicy retryPolicy = RetryPolicy.builder()
                .numRetries(s3Properties.getMaxRetries())
                .retryCondition(RetryCondition.defaultRetryCondition())
                .backoffStrategy(backoffStrategy)
                .build();

        // 4. 클라이언트 오버라이드 설정 (타임아웃 + 재시도)
        ClientOverrideConfiguration overrideConfig = ClientOverrideConfiguration.builder()
                .apiCallTimeout(Duration.ofMillis(s3Properties.getApiCallTimeout()))
                .apiCallAttemptTimeout(Duration.ofMillis(s3Properties.getApiCallAttemptTimeout()))
                .retryPolicy(retryPolicy)
                .build();

        // 5. S3Client 빌드
        S3Client s3Client = S3Client.builder()
                .endpointOverride(URI.create(s3Properties.getEndpoint()))  // SeaweedFS 엔드포인트
                .region(Region.of(s3Properties.getRegion()))                // 리전 설정
                .credentialsProvider(StaticCredentialsProvider.create(credentials))  // 인증 정보
                .serviceConfiguration(s3Config)                             // S3 설정 (path-style)
                .overrideConfiguration(overrideConfig)                      // 타임아웃 + 재시도
                .build();

        log.info("S3Client initialized successfully with timeout and retry configuration");
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

    /**
     * 애플리케이션 시작 시 S3 버킷 자동 생성
     *
     * <p>개발 환경에서 SeaweedFS 버킷이 없으면 자동으로 생성합니다.
     * <p>프로덕션 환경에서는 버킷이 이미 존재하므로 생성을 건너뜁니다.
     *
     * <p>동작 방식:
     * <ol>
     *   <li>ListBuckets로 전체 버킷 목록 조회</li>
     *   <li>목록에 없으면 CreateBucket으로 생성</li>
     *   <li>BucketAlreadyExistsException 발생 시에도 정상 처리 (SeaweedFS collection 충돌)</li>
     *   <li>이미 존재하면 로그만 출력</li>
     * </ol>
     */
    @EventListener(ApplicationReadyEvent.class)
    public void createBucketIfNotExists() {

        // CRITICAL FIX: try-with-resources를 사용하면 Spring Bean이 닫혀서 "Connection pool shut down" 에러 발생
        // Spring이 @Bean 메서드를 프록시하여 싱글톤을 반환하므로, try-with-resources는 싱글톤 bean을 닫아버림
        // 해결: try-with-resources 제거, Spring이 bean lifecycle 관리하도록 함
        S3Client client = s3Client();  // Spring이 싱글톤 bean 반환

        // 1. ListBuckets로 실제 S3 버킷 목록 조회
        ListBucketsResponse listBucketsResponse = client.listBuckets();

        // 2. Stream API로 버킷 존재 여부 확인
        boolean bucketExists = listBucketsResponse.buckets().stream()
                .anyMatch(bucket -> bucket.name().equals(s3Properties.getBucket()));

        if (bucketExists) {
            // 버킷이 이미 존재함
            log.info("S3 bucket already exists: {}", s3Properties.getBucket());

        } else {
            // 3. 버킷이 ListBuckets에 없으면 CreateBucket 호출
            log.info("S3 bucket does not exist. Creating bucket: {}", s3Properties.getBucket());

            try {
                CreateBucketRequest createBucketRequest = CreateBucketRequest.builder()
                        .bucket(s3Properties.getBucket())
                        .build();

                client.createBucket(createBucketRequest);

                log.info("S3 bucket created successfully: {}", s3Properties.getBucket());

            } catch (software.amazon.awssdk.services.s3.model.BucketAlreadyExistsException e) {
                // SeaweedFS에서 collection 등으로 이미 존재하는 경우
                // ListBuckets에는 없지만 실제로는 사용 가능한 상태
                log.info("S3 bucket already exists (collection conflict resolved): {}", s3Properties.getBucket());
            } catch (Exception e) {
                // 4. 기타 예외 처리
                log.error("Failed to check or create S3 bucket: {}", s3Properties.getBucket(), e);
                throw new RuntimeException("S3 bucket initialization failed", e);
            }
        }
    }


}
