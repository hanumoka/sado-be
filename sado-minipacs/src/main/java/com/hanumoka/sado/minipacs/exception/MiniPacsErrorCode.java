package com.hanumoka.sado.minipacs.exception;

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
