package com.hanumoka.sado.minipacs.dto.response.seaweedfs;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * SeaweedFS Filer 디렉토리 엔트리 응답 DTO
 *
 * <p>Filer의 파일 또는 디렉토리 정보
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FilerEntryResponse {

    /**
     * 파일/디렉토리 이름
     *
     * <p>예: "study123.dcm", "studies"
     */
    private String name;

    /**
     * 디렉토리 여부
     */
    private Boolean isDirectory;

    /**
     * 파일 크기 (bytes)
     *
     * <p>디렉토리인 경우 0
     */
    private Long size;

    /**
     * 수정 시각
     */
    private LocalDateTime modifiedTime;

    /**
     * MIME 타입
     *
     * <p>예: "application/dicom", "image/jpeg"
     */
    private String mimeType;

    /**
     * 전체 경로
     *
     * <p>예: "/minipacs/studies/1.2.3.4.5/series/1.2.3.4.5.6/instances/1.2.3.4.5.6.7.dcm"
     */
    private String fullPath;
}
