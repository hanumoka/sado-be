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
}
