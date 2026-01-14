package com.hanumoka.sado.minipacs.infrastructure.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 사전 렌더링 설정 Properties
 *
 * <p>application.yml의 pre-rendering 섹션을 바인딩합니다.
 *
 * <p>각 렌더링 대상을 개별적으로 on/off 할 수 있습니다.
 *
 * <p>설정 예시:
 * <pre>
 * pre-rendering:
 *   enabled: true                    # 사전 렌더링 전체 활성화/비활성화
 *   targets:
 *     thumbnail:
 *       enabled: true                # 썸네일 (128x128 JPEG)
 *     bulkdata:
 *       enabled: true                # Raw PixelData (.raw)
 *     compressed:
 *       enabled: true                # 원본 압축 데이터 (압축 DICOM인 경우만)
 *     jpeg:
 *       enabled: true                # JPEG Baseline (원본 해상도)
 *       quality: 90                  # JPEG 품질 (1-100)
 *     rendered:
 *       enabled: true                # PNG 이미지 (512px)
 *     cine:
 *       enabled: true                # Cine JPEG (256/128/64/32px)
 *       quality: 80                  # Cine JPEG 품질 (1-100)
 * </pre>
 *
 * @see com.hanumoka.sado.minipacs.domain.service.PreRenderingService
 */
@Component
@ConfigurationProperties(prefix = "pre-rendering")
@Data
public class PreRenderingProperties {

    /**
     * 사전 렌더링 전체 활성화/비활성화
     * false인 경우 모든 사전 렌더링을 건너뜁니다.
     */
    private boolean enabled = true;

    /**
     * 개별 렌더링 대상 설정
     */
    private Targets targets = new Targets();

    @Data
    public static class Targets {
        /**
         * 썸네일 설정 (128x128 JPEG)
         */
        private ThumbnailConfig thumbnail = new ThumbnailConfig();

        /**
         * Raw PixelData 설정 (.raw)
         */
        private EnabledConfig bulkdata = new EnabledConfig();

        /**
         * 원본 압축 데이터 설정 (압축 DICOM인 경우만 생성)
         */
        private EnabledConfig compressed = new EnabledConfig();

        /**
         * JPEG Baseline 설정 (원본 해상도)
         */
        private JpegConfig jpeg = new JpegConfig();

        /**
         * PNG 렌더링 설정 (512px)
         */
        private EnabledConfig rendered = new EnabledConfig();

        /**
         * Cine JPEG 설정 (256/128/64/32px)
         */
        private CineConfig cine = new CineConfig();
    }

    /**
     * 기본 활성화 설정
     */
    @Data
    public static class EnabledConfig {
        private boolean enabled = true;
    }

    /**
     * 썸네일 설정
     */
    @Data
    public static class ThumbnailConfig {
        private boolean enabled = true;
        private int size = 128;  // 썸네일 크기 (픽셀)
    }

    /**
     * JPEG Baseline 설정
     */
    @Data
    public static class JpegConfig {
        private boolean enabled = true;
        private int quality = 90;  // JPEG 품질 (1-100, 기본값 90%)
    }

    /**
     * Cine JPEG 설정
     */
    @Data
    public static class CineConfig {
        private boolean enabled = true;
        private int quality = 80;  // JPEG 품질 (1-100, 기본값 80%)
        private boolean size256 = true;   // 256px 생성 여부
        private boolean size128 = true;   // 128px 생성 여부
        private boolean size64 = true;    // 64px 생성 여부
        private boolean size32 = true;    // 32px 생성 여부
    }

    // ========== 편의 메서드 ==========

    /**
     * 썸네일 생성 여부
     */
    public boolean isThumbnailEnabled() {
        return enabled && targets.thumbnail.enabled;
    }

    /**
     * Raw PixelData 생성 여부
     */
    public boolean isBulkdataEnabled() {
        return enabled && targets.bulkdata.enabled;
    }

    /**
     * 원본 압축 데이터 생성 여부
     */
    public boolean isCompressedEnabled() {
        return enabled && targets.compressed.enabled;
    }

    /**
     * JPEG Baseline 생성 여부
     */
    public boolean isJpegEnabled() {
        return enabled && targets.jpeg.enabled;
    }

    /**
     * JPEG Baseline 품질 (0.0f ~ 1.0f)
     */
    public float getJpegQualityAsFloat() {
        return targets.jpeg.quality / 100.0f;
    }

    /**
     * PNG 렌더링 생성 여부
     */
    public boolean isRenderedEnabled() {
        return enabled && targets.rendered.enabled;
    }

    /**
     * Cine JPEG 생성 여부
     */
    public boolean isCineEnabled() {
        return enabled && targets.cine.enabled;
    }

    /**
     * Cine JPEG 품질 (0.0f ~ 1.0f)
     */
    public float getCineQualityAsFloat() {
        return targets.cine.quality / 100.0f;
    }
}
