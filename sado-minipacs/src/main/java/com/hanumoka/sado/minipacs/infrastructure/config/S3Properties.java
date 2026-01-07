package com.hanumoka.sado.minipacs.infrastructure.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * SeaweedFS S3 설정 Properties
 *
 * <p>application.yml의 seaweedfs.s3.* 설정을 바인딩합니다.
 *
 * <p>사용 예시:
 * <pre>
 * seaweedfs:
 *   s3:
 *     endpoint: http://localhost:10405
 *     region: us-east-1
 *     access-key: any
 *     secret-key: any
 *     bucket: minipacs
 *     path-style-access: true
 * </pre>
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "seaweedfs.s3")
public class S3Properties {

    /**
     * S3 엔드포인트 URL
     * - 로컬 개발: http://localhost:10405
     * - 운영 (AWS S3): https://s3.us-east-1.amazonaws.com
     */
    private String endpoint;

    /**
     * S3 리전
     * - SeaweedFS는 무시 (AWS SDK 필수 파라미터)
     * - AWS S3: us-east-1, ap-northeast-2 등
     */
    private String region;

    /**
     * S3 Access Key
     * - SeaweedFS 로컬 개발: 임의값 (any)
     * - AWS S3: IAM 사용자의 Access Key ID
     */
    private String accessKey;

    /**
     * S3 Secret Key
     * - SeaweedFS 로컬 개발: 임의값 (any)
     * - AWS S3: IAM 사용자의 Secret Access Key
     */
    private String secretKey;

    /**
     * S3 버킷명
     * - DICOM 파일 저장용 버킷
     * - 기본값: minipacs
     */
    private String bucket;

    /**
     * Path-style 접근 활성화
     * - true: http://localhost:10405/bucket-name/key (SeaweedFS, MinIO)
     * - false: https://bucket-name.s3.amazonaws.com/key (AWS S3 기본값)
     */
    private Boolean pathStyleAccess;

    // ========== 타임아웃 설정 (2026-01-05 추가) ==========

    /**
     * 전체 API 호출 타임아웃 (밀리초)
     * - 전체 요청 완료까지 대기하는 최대 시간
     * - 재시도 포함, 모든 시도의 합계 시간
     * - 기본값: 300000 (5분) - 대용량 DICOM 파일 업로드용
     */
    private Long apiCallTimeout = 300000L;

    /**
     * 개별 API 호출 시도 타임아웃 (밀리초)
     * - 각 시도별 최대 대기 시간
     * - 초과 시 재시도 (재시도 횟수 남아있으면)
     * - 기본값: 60000 (60초)
     */
    private Long apiCallAttemptTimeout = 60000L;

    /**
     * 최대 재시도 횟수
     * - 일시적 오류 (503, 429, 네트워크 오류) 시 재시도
     * - 기본값: 3
     */
    private Integer maxRetries = 3;
}
