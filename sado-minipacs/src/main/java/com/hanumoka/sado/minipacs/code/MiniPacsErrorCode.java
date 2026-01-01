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
