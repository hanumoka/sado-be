package com.hanumoka.sado.minipacs.dto;

/**
 * 프레임 데이터 추출 결과 DTO
 *
 * <p>WADO-RS BulkData API에서 Transfer Syntax 유지 여부에 따라
 * 다른 형식의 데이터를 반환할 때 사용합니다.
 *
 * <p>압축 유지 시:
 * <ul>
 *   <li>data: 원본 압축 데이터 (JPEG 2000, JPEG 등)</li>
 *   <li>transferSyntaxUid: 원본 Transfer Syntax (예: 1.2.840.10008.1.2.4.90)</li>
 *   <li>mimeType: 압축 포맷에 맞는 MIME 타입 (예: image/jp2)</li>
 * </ul>
 *
 * <p>디코딩 시:
 * <ul>
 *   <li>data: Raw PixelData (비압축)</li>
 *   <li>transferSyntaxUid: Explicit VR Little Endian (1.2.840.10008.1.2.1)</li>
 *   <li>mimeType: application/octet-stream</li>
 * </ul>
 *
 * @param data 프레임 데이터 (압축 또는 비압축)
 * @param transferSyntaxUid Transfer Syntax UID
 * @param mimeType Content-Type MIME 타입
 */
public record FrameExtractionResult(
    byte[] data,
    String transferSyntaxUid,
    String mimeType
) {
    /**
     * 데이터 크기 반환 (로깅용)
     */
    public int dataSize() {
        return data != null ? data.length : 0;
    }

    /**
     * 압축 여부 확인
     */
    public boolean isCompressed() {
        return !"application/octet-stream".equals(mimeType);
    }
}
