package com.hanumoka.sado.minipacs.code;

import com.hanumoka.sado.common.code.ApiCode;
import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * MiniPACS 도메인 에러 코드
 *
 * <p>코드 범위:
 * <ul>
 *   <li>500100~500199: Storage 관련 에러</li>
 *   <li>500200~500299: DICOM 처리 에러</li>
 *   <li>500300~500399: Instance 관리 에러</li>
 * </ul>
 */
@Getter
public enum MiniPacsErrorCode implements ApiCode {

    // ========== Storage 에러 (500100~500199) ==========

    /**
     * 파일 업로드 실패
     *
     * <p>발생 시점: SeaweedFS S3 PutObject 실패
     * <p>원인: 네트워크 오류, S3 권한 부족, 디스크 용량 부족
     */
    STORAGE_UPLOAD_FAILED(
            HttpStatus.INTERNAL_SERVER_ERROR,
            500101,
            "파일 업로드에 실패했습니다"
    ),

    /**
     * 파일 다운로드 실패
     *
     * <p>발생 시점: SeaweedFS S3 GetObject 실패
     * <p>원인: 네트워크 오류, 파일 손상
     */
    STORAGE_DOWNLOAD_FAILED(
            HttpStatus.INTERNAL_SERVER_ERROR,
            500102,
            "파일 다운로드에 실패했습니다"
    ),

    /**
     * 파일 삭제 실패
     *
     * <p>발생 시점: SeaweedFS S3 DeleteObject 실패
     * <p>원인: 네트워크 오류, S3 권한 부족
     */
    STORAGE_DELETE_FAILED(
            HttpStatus.INTERNAL_SERVER_ERROR,
            500103,
            "파일 삭제에 실패했습니다"
    ),

    /**
     * 파일을 찾을 수 없음
     *
     * <p>발생 시점: SeaweedFS에 파일이 존재하지 않음
     * <p>원인: 잘못된 fileId, 파일 삭제됨
     */
    FILE_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            404101,
            "파일을 찾을 수 없습니다"
    ),

    /**
     * 파일 읽기 실패
     *
     * <p>발생 시점: MultipartFile.getBytes() 실패
     * <p>원인: 업로드 중 네트워크 끊김
     */
    FILE_READ_FAILED(
            HttpStatus.BAD_REQUEST,
            400101,
            "파일 읽기에 실패했습니다"
    ),

    // ========== 업로드 정책 에러 (400100~400199) ==========

    /**
     * 파일 개수 제한 초과
     *
     * <p>발생 시점: 한 번에 100개 이상 업로드 시도
     * <p>원인: Frontend 검증 우회 시도, 직접 API 호출
     */
    FILE_COUNT_EXCEEDED(
            HttpStatus.BAD_REQUEST,
            400102,
            "파일 개수가 제한을 초과했습니다 (최대 100개)"
    ),

    /**
     * 전체 파일 크기 제한 초과
     *
     * <p>발생 시점: 전체 파일 크기가 2GB 초과
     * <p>원인: Frontend 검증 우회 시도, 직접 API 호출
     */
    TOTAL_SIZE_EXCEEDED(
            HttpStatus.BAD_REQUEST,
            400103,
            "전체 파일 크기가 제한을 초과했습니다 (최대 2GB)"
    ),

    /**
     * 개별 파일 크기 제한 초과
     *
     * <p>발생 시점: 단일 파일이 500MB 초과
     * <p>원인: Frontend 검증 우회 시도, 직접 API 호출
     * <p>참고: Spring의 max-file-size 검증은 413 Payload Too Large 반환
     */
    FILE_SIZE_EXCEEDED(
            HttpStatus.BAD_REQUEST,
            400104,
            "파일 크기가 제한을 초과했습니다 (최대 500MB)"
    ),

    // ========== DICOM 에러 (400200~400299, 500200~500299) ==========

    /**
     * DICOM 파싱 실패
     *
     * <p>발생 시점: DCM4CHE DicomInputStream 파싱 실패
     * <p>원인: 손상된 DICOM 파일, 잘못된 파일 형식
     */
    DICOM_PARSING_FAILED(
            HttpStatus.BAD_REQUEST,
            400201,
            "DICOM 파일 파싱에 실패했습니다"
    ),

    /**
     * 잘못된 DICOM 형식
     *
     * <p>발생 시점: DICOM 매직 넘버 검증 실패
     * <p>원인: DICOM이 아닌 파일 업로드
     */
    DICOM_INVALID_FORMAT(
            HttpStatus.BAD_REQUEST,
            400202,
            "유효하지 않은 DICOM 파일 형식입니다"
    ),

    /**
     * 지원하지 않는 파일 확장자
     *
     * <p>발생 시점: 파일 확장자 검증 실패
     * <p>원인: .dcm, .dicom, 확장자 없는 파일이 아님
     */
    UNSUPPORTED_FILE_EXTENSION(
            HttpStatus.BAD_REQUEST,
            400204,
            "지원하지 않는 파일 확장자입니다. .dcm, .dicom, 확장자 없는 파일만 허용됩니다"
    ),

    /**
     * DICOM 필수 태그 누락
     *
     * <p>발생 시점: 필수 DICOM 태그 미존재
     * <p>원인: 불완전한 DICOM 파일
     */
    DICOM_MISSING_REQUIRED_TAG(
            HttpStatus.BAD_REQUEST,
            400203,
            "DICOM 필수 태그가 누락되었습니다"
    ),

    /**
     * 유효하지 않은 프레임 번호
     *
     * <p>발생 시점: WADO-RS Rendered API에서 프레임 번호가 유효 범위 초과
     * <p>원인: 존재하지 않는 프레임 번호 요청
     */
    INVALID_FRAME_NUMBER(
            HttpStatus.BAD_REQUEST,
            400205,
            "유효하지 않은 프레임 번호입니다"
    ),

    /**
     * DICOM 이미지 렌더링 실패
     *
     * <p>발생 시점: DICOM → PNG/JPEG 변환 실패
     * <p>원인: DCM4CHE ImageIO 누락, 지원하지 않는 Transfer Syntax, 픽셀 데이터 손상
     */
    DICOM_RENDER_FAILED(
            HttpStatus.INTERNAL_SERVER_ERROR,
            500201,
            "DICOM 이미지 렌더링에 실패했습니다"
    ),

    /**
     * DICOM 중복
     *
     * <p>발생 시점: 동일한 SOP Instance UID가 이미 존재
     * <p>원인: 동일 파일 재업로드
     */
    DICOM_DUPLICATE(
            HttpStatus.CONFLICT,
            409201,
            "이미 존재하는 DICOM 파일입니다"
    ),

    // ========== Patient 에러 (404300~404399, 409300~409399) ==========

    /**
     * 환자를 찾을 수 없음
     */
    PATIENT_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            404301,
            "환자를 찾을 수 없습니다"
    ),

    /**
     * 환자 중복
     */
    PATIENT_DUPLICATE(
            HttpStatus.CONFLICT,
            409301,
            "이미 존재하는 환자입니다"
    ),

    // ========== Study 에러 (404400~404499) ==========

    /**
     * 검사를 찾을 수 없음
     */
    STUDY_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            404401,
            "검사를 찾을 수 없습니다"
    ),

    // ========== Series 에러 (404500~404599) ==========

    /**
     * 시리즈를 찾을 수 없음
     */
    SERIES_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            404501,
            "시리즈를 찾을 수 없습니다"
    ),

    // ========== Instance 에러 (404600~404699, 409600~409699) ==========

    /**
     * 영상을 찾을 수 없음
     */
    INSTANCE_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            404601,
            "영상을 찾을 수 없습니다"
    ),

    /**
     * 영상 중복
     */
    INSTANCE_DUPLICATE(
            HttpStatus.CONFLICT,
            409601,
            "이미 존재하는 영상입니다"
    ),

    /**
     * 트랜스코딩 미완료
     *
     * <p>발생 시점: MJPEG 스트리밍 요청 시 Pre-rendering 미완료
     * <p>원인: Pre-rendering 진행 중, 실패, 또는 멀티프레임이 아닌 영상
     */
    TRANSCODING_NOT_COMPLETED(
            HttpStatus.CONFLICT,
            409602,
            "트랜스코딩이 완료되지 않았습니다"
    ),

    // ========== Tiering 에러 (400700~400799) ==========

    /**
     * 잘못된 Tier 값
     *
     * <p>발생 시점: HOT/WARM/COLD가 아닌 값 입력
     * <p>원인: 잘못된 Tier 파라미터
     */
    INVALID_TIER(
            HttpStatus.BAD_REQUEST,
            400701,
            "유효하지 않은 Tier 값입니다"
    ),

    /**
     * 잘못된 파일 상태
     *
     * <p>발생 시점: ACTIVE가 아닌 파일의 Tier 변경 시도
     * <p>원인: 삭제/아카이브된 파일의 Tier 변경
     */
    INVALID_FILE_STATUS(
            HttpStatus.BAD_REQUEST,
            400702,
            "현재 파일 상태에서는 Tier 변경이 불가능합니다"
    ),

    /**
     * Tier가 이미 설정됨
     *
     * <p>발생 시점: 동일한 Tier로의 전환 시도
     * <p>원인: 불필요한 Tier 전환
     */
    TIER_ALREADY_SET(
            HttpStatus.BAD_REQUEST,
            400703,
            "파일이 이미 해당 Tier에 있습니다"
    ),

    /**
     * 잘못된 Tiering 정책
     *
     * <p>발생 시점: 정책 검증 실패
     * <p>원인: warmToColdDays <= hotToWarmDays, 범위 초과 등
     */
    INVALID_TIERING_POLICY(
            HttpStatus.BAD_REQUEST,
            400704,
            "유효하지 않은 Tiering 정책입니다"
    ),

    // ========== SeaweedFS 에러 (500800~500899, 503800~503899) ==========

    /**
     * SeaweedFS API 호출 실패
     *
     * <p>발생 시점: Master/Volume/Filer API 호출 실패
     * <p>원인: 네트워크 오류, SeaweedFS 서버 다운
     */
    SEAWEEDFS_API_ERROR(
            HttpStatus.BAD_GATEWAY,
            502801,
            "SeaweedFS API 호출에 실패했습니다"
    ),

    /**
     * SeaweedFS 서버 접근 불가
     *
     * <p>발생 시점: SeaweedFS 서버에 연결할 수 없음
     * <p>원인: SeaweedFS 서버 다운, 네트워크 문제
     */
    SEAWEEDFS_UNAVAILABLE(
            HttpStatus.SERVICE_UNAVAILABLE,
            503801,
            "SeaweedFS 서버에 접근할 수 없습니다"
    ),

    /**
     * SeaweedFS 응답 파싱 실패
     *
     * <p>발생 시점: SeaweedFS API 응답을 JSON으로 파싱 실패
     * <p>원인: 예상치 못한 응답 형식, API 버전 불일치
     */
    SEAWEEDFS_RESPONSE_PARSE_ERROR(
            HttpStatus.INTERNAL_SERVER_ERROR,
            500802,
            "SeaweedFS 응답 파싱에 실패했습니다"
    ),

    /**
     * Volume 생성 실패
     *
     * <p>발생 시점: Volume 생성 API 호출 실패
     * <p>원인: 디스크 공간 부족, 잘못된 파라미터
     */
    VOLUME_CREATE_FAILED(
            HttpStatus.INTERNAL_SERVER_ERROR,
            500803,
            "Volume 생성에 실패했습니다"
    ),

    /**
     * Volume 삭제 실패
     *
     * <p>발생 시점: Volume 삭제 API 호출 실패
     * <p>원인: Volume이 비어있지 않음, Volume이 존재하지 않음
     */
    VOLUME_DELETE_FAILED(
            HttpStatus.BAD_REQUEST,
            400805,
            "Volume 삭제에 실패했습니다"
    );

    // ========== 필드 및 생성자 ==========

    private final HttpStatus httpStatus;
    private final int code;
    private final String message;

    MiniPacsErrorCode(HttpStatus httpStatus, int code, String message) {
        this.httpStatus = httpStatus;
        this.code = code;
        this.message = message;
    }
}
