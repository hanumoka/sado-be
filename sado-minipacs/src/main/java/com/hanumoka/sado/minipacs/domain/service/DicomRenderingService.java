package com.hanumoka.sado.minipacs.domain.service;

import com.hanumoka.sado.common.exception.BusinessException;
import com.hanumoka.sado.minipacs.code.MiniPacsErrorCode;
import com.hanumoka.sado.minipacs.storage.service.DicomStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Fragments;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.UID;
import org.dcm4che3.data.VR;
import org.dcm4che3.imageio.plugins.dcm.DicomImageReadParam;
import org.dcm4che3.io.DicomInputStream;

import java.util.Arrays;
import java.util.List;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;

/**
 * DICOM 이미지 렌더링 서비스
 *
 * <p>DICOM 파일을 PNG/JPEG 이미지로 변환합니다.
 * DCM4CHE ImageIO를 사용하여 DICOM 픽셀 데이터를 BufferedImage로 추출합니다.
 *
 * <p>지원 기능:
 * <ul>
 *   <li>싱글프레임 DICOM 렌더링</li>
 *   <li>멀티프레임 DICOM 특정 프레임 렌더링</li>
 *   <li>자동 Window/Level 적용</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DicomRenderingService {

    private final DicomStorageService dicomStorageService;

    /**
     * DICOM 파일을 PNG 이미지로 렌더링
     *
     * @param storagePath S3 저장 경로
     * @param frameNumber 프레임 번호 (1부터 시작)
     * @return PNG 이미지 바이트 배열
     * @throws BusinessException DICOM_RENDER_FAILED - 렌더링 실패 시
     */
    public byte[] renderToPng(String storagePath, int frameNumber) {
        log.debug("Rendering DICOM to PNG: path={}, frame={}", storagePath, frameNumber);

        try (InputStream dicomStream = dicomStorageService.downloadDicomFile(storagePath)) {
            // DICOM 파일을 메모리로 읽기 (ImageIO가 seekable stream 필요)
            byte[] dicomBytes = dicomStream.readAllBytes();

            // BufferedImage 추출
            BufferedImage image = extractFrame(dicomBytes, frameNumber);

            // PNG로 인코딩
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            boolean written = ImageIO.write(image, "PNG", baos);

            if (!written) {
                throw new BusinessException(MiniPacsErrorCode.DICOM_RENDER_FAILED, "PNG 인코딩 실패");
            }

            byte[] pngBytes = baos.toByteArray();
            log.debug("Rendered PNG: {} bytes", pngBytes.length);

            return pngBytes;

        } catch (BusinessException e) {
            throw e;
        } catch (IOException e) {
            log.error("DICOM rendering failed: path={}, frame={}", storagePath, frameNumber, e);
            throw new BusinessException(MiniPacsErrorCode.DICOM_RENDER_FAILED, e.getMessage());
        }
    }

    /**
     * DICOM 바이트 배열에서 특정 프레임을 BufferedImage로 추출
     *
     * @param dicomBytes DICOM 파일 바이트 배열
     * @param frameNumber 프레임 번호 (1부터 시작)
     * @return BufferedImage
     */
    private BufferedImage extractFrame(byte[] dicomBytes, int frameNumber) throws IOException {
        // Transfer Syntax 로깅 (디버그용)
        logTransferSyntax(dicomBytes);

        // DCM4CHE ImageIO 사용
        Iterator<ImageReader> readers = ImageIO.getImageReadersByFormatName("DICOM");

        if (!readers.hasNext()) {
            throw new BusinessException(MiniPacsErrorCode.DICOM_RENDER_FAILED,
                    "DICOM ImageReader를 찾을 수 없습니다. DCM4CHE ImageIO가 설치되지 않았습니다.");
        }

        ImageReader reader = readers.next();

        try (ByteArrayInputStream bais = new ByteArrayInputStream(dicomBytes);
             ImageInputStream iis = ImageIO.createImageInputStream(bais)) {

            reader.setInput(iis);

            // 프레임 인덱스 (0-based)
            int frameIndex = frameNumber - 1;

            // 총 프레임 수 확인
            int numFrames = reader.getNumImages(true);
            if (frameIndex < 0 || frameIndex >= numFrames) {
                throw new BusinessException(MiniPacsErrorCode.INVALID_FRAME_NUMBER,
                        String.format("Frame %d out of range (1-%d)", frameNumber, numFrames));
            }

            // DicomImageReadParam 설정 (Window/Level 자동 적용)
            DicomImageReadParam param = (DicomImageReadParam) reader.getDefaultReadParam();

            // 이미지 읽기
            BufferedImage image = reader.read(frameIndex, param);

            log.debug("Extracted frame {}/{}: {}x{}", frameNumber, numFrames,
                    image.getWidth(), image.getHeight());

            return image;

        } finally {
            reader.dispose();
        }
    }

    /**
     * DICOM Transfer Syntax 로깅 (디버그용)
     */
    private void logTransferSyntax(byte[] dicomBytes) {
        try (ByteArrayInputStream bais = new ByteArrayInputStream(dicomBytes);
             DicomInputStream dis = new DicomInputStream(bais)) {

            // File Meta Information에서 Transfer Syntax 추출
            Attributes fmi = dis.readFileMetaInformation();
            if (fmi != null) {
                String transferSyntaxUID = fmi.getString(Tag.TransferSyntaxUID);
                String transferSyntaxName = getTransferSyntaxName(transferSyntaxUID);
                log.debug("DICOM Transfer Syntax: {} ({})", transferSyntaxName, transferSyntaxUID);

                // 압축 여부 판단
                boolean isCompressed = !UID.ImplicitVRLittleEndian.equals(transferSyntaxUID)
                        && !UID.ExplicitVRLittleEndian.equals(transferSyntaxUID)
                        && !UID.ExplicitVRBigEndian.equals(transferSyntaxUID);
                log.debug("DICOM Compression: {}", isCompressed ? "COMPRESSED" : "UNCOMPRESSED");
            } else {
                log.warn("No File Meta Information found in DICOM");
            }
        } catch (IOException e) {
            log.warn("Failed to read Transfer Syntax: {}", e.getMessage());
        }
    }

    /**
     * Transfer Syntax UID를 사람이 읽을 수 있는 이름으로 변환
     */
    private String getTransferSyntaxName(String uid) {
        if (uid == null) return "Unknown";

        return switch (uid) {
            case UID.ImplicitVRLittleEndian -> "Implicit VR Little Endian";
            case UID.ExplicitVRLittleEndian -> "Explicit VR Little Endian";
            case UID.ExplicitVRBigEndian -> "Explicit VR Big Endian";
            case UID.JPEGBaseline8Bit -> "JPEG Baseline (8-bit)";
            case "1.2.840.10008.1.2.4.51" -> "JPEG Extended (12-bit)";
            case UID.JPEGLossless -> "JPEG Lossless";
            case UID.JPEGLosslessSV1 -> "JPEG Lossless SV1";
            case UID.JPEGLSLossless -> "JPEG-LS Lossless";
            case UID.JPEGLSNearLossless -> "JPEG-LS Near Lossless";
            case UID.JPEG2000Lossless -> "JPEG 2000 Lossless";
            case UID.JPEG2000 -> "JPEG 2000 (Lossy)";
            case UID.RLELossless -> "RLE Lossless";
            default -> "Unknown (" + uid + ")";
        };
    }

    /**
     * DICOM 파일 바이트 조회 (WADO-RS BulkData 지원)
     *
     * <p>Cornerstone3D의 wadors: scheme에서 사용됩니다.
     * 클라이언트가 직접 DICOM을 파싱하여 프레임을 추출합니다.
     *
     * @param storagePath S3 저장 경로
     * @return DICOM 파일 바이트 배열
     * @throws BusinessException DICOM_DOWNLOAD_FAILED - 다운로드 실패 시
     */
    public byte[] getDicomFileBytes(String storagePath) {
        log.debug("Getting DICOM file bytes: path={}", storagePath);

        try (InputStream dicomStream = dicomStorageService.downloadDicomFile(storagePath)) {
            byte[] bytes = dicomStream.readAllBytes();
            log.debug("DICOM file bytes: {} bytes", bytes.length);
            return bytes;
        } catch (IOException e) {
            log.error("Failed to download DICOM file: path={}", storagePath, e);
            throw new BusinessException(MiniPacsErrorCode.STORAGE_DOWNLOAD_FAILED, e.getMessage());
        }
    }

    /**
     * DICOM 파일에서 특정 프레임의 PixelData만 추출 (WADO-RS BulkData 표준 준수)
     *
     * <p>DICOMweb WADO-RS RetrieveFrames 표준에 따라 개별 프레임의 raw PixelData를 반환합니다.
     * Cornerstone3D의 wadors: 로더가 기대하는 형식입니다.
     *
     * <p>DCM4CHE ImageIO의 readRaster()를 사용하여 압축 해제된 raw pixels 반환.
     * readRaster()는 Window/Level 적용 없이 원본 픽셀값을 반환합니다.
     *
     * @param storagePath S3 저장 경로
     * @param frameNumber 프레임 번호 (1-based)
     * @return 해당 프레임의 raw (decompressed) PixelData 바이트
     * @throws BusinessException DICOM_RENDER_FAILED - 추출 실패 시
     */
    public byte[] extractFramePixelData(String storagePath, int frameNumber) {
        log.debug("Extracting frame PixelData (using DCM4CHE ImageIO): path={}, frame={}", storagePath, frameNumber);

        try (InputStream dicomStream = dicomStorageService.downloadDicomFile(storagePath)) {
            byte[] dicomBytes = dicomStream.readAllBytes();

            // Transfer Syntax 로깅
            logTransferSyntax(dicomBytes);

            // DCM4CHE ImageIO 사용
            Iterator<ImageReader> readers = ImageIO.getImageReadersByFormatName("DICOM");
            if (!readers.hasNext()) {
                throw new BusinessException(MiniPacsErrorCode.DICOM_RENDER_FAILED,
                        "DICOM ImageReader not found. DCM4CHE ImageIO is not installed.");
            }

            ImageReader reader = readers.next();

            try (ByteArrayInputStream bais = new ByteArrayInputStream(dicomBytes);
                 ImageInputStream iis = ImageIO.createImageInputStream(bais)) {

                reader.setInput(iis);

                // 프레임 인덱스 (0-based)
                int frameIndex = frameNumber - 1;
                int numFrames = reader.getNumImages(true);

                if (frameIndex < 0 || frameIndex >= numFrames) {
                    throw new BusinessException(MiniPacsErrorCode.INVALID_FRAME_NUMBER,
                            String.format("Frame %d out of range (1-%d)", frameNumber, numFrames));
                }

                // CRITICAL: readRaster()로 W/L 미적용 raw pixels 추출
                // read()는 BufferedImage로 변환하며 W/L이 적용됨
                java.awt.image.Raster raster = reader.readRaster(frameIndex, null);

                // === Raster 디버깅 로그 (이미지 분리 문제 진단용) ===
                java.awt.image.SampleModel sm = raster.getSampleModel();
                log.debug("=== Raster Debug for Frame {} ===", frameNumber);
                log.debug("Raster Width: {}, Height: {}", raster.getWidth(), raster.getHeight());
                log.debug("SampleModel class: {}", sm.getClass().getName());
                log.debug("SampleModel Width: {}, Height: {}", sm.getWidth(), sm.getHeight());
                log.debug("NumBands: {}, DataType: {}", sm.getNumBands(), sm.getDataType());

                if (sm instanceof java.awt.image.ComponentSampleModel csm) {
                    log.debug("ComponentSampleModel PixelStride: {}", csm.getPixelStride());
                    log.debug("ComponentSampleModel ScanlineStride: {}", csm.getScanlineStride());
                    log.debug("BandOffsets: {}", java.util.Arrays.toString(csm.getBandOffsets()));
                }

                java.awt.image.DataBuffer db = raster.getDataBuffer();
                log.debug("DataBuffer Size: {}", db.getSize());
                log.debug("DataBuffer NumBanks: {}", db.getNumBanks());
                log.debug("Expected Size (width*height): {}", raster.getWidth() * raster.getHeight());
                log.debug("=== End Raster Debug ===");
                // === 디버깅 로그 끝 ===

                // Raster에서 raw bytes 추출
                byte[] rawPixels = extractRawBytesFromRaster(raster, dicomBytes);

                log.info("Extracted decompressed frame {}/{}: {} bytes ({}x{})",
                        frameNumber, numFrames, rawPixels.length,
                        raster.getWidth(), raster.getHeight());

                return rawPixels;

            } finally {
                reader.dispose();
            }

        } catch (BusinessException e) {
            throw e;
        } catch (IOException e) {
            log.error("Failed to extract frame PixelData: path={}, frame={}", storagePath, frameNumber, e);
            throw new BusinessException(MiniPacsErrorCode.DICOM_RENDER_FAILED, e.getMessage());
        }
    }

    /**
     * Raster에서 raw 바이트 추출
     *
     * <p>readRaster()로 얻은 Raster에서 Window/Level 적용 없이 원본 픽셀값 추출.
     * 이 방법이 BufferedImage 대신 사용되어야 하는 이유:
     * - reader.read()는 BufferedImage 생성 시 자동 W/L 적용
     * - reader.readRaster()는 raw 픽셀값 유지
     *
     * @param raster DCM4CHE ImageReader에서 얻은 Raster
     * @param dicomBytes 메타데이터 확인용 DICOM 바이트
     * @return Little Endian raw bytes
     */
    private byte[] extractRawBytesFromRaster(java.awt.image.Raster raster, byte[] dicomBytes) throws IOException {
        int width = raster.getWidth();
        int height = raster.getHeight();
        java.awt.image.DataBuffer dataBuffer = raster.getDataBuffer();

        // DICOM 메타데이터에서 원본 정보 확인
        try (ByteArrayInputStream bais = new ByteArrayInputStream(dicomBytes);
             DicomInputStream dis = new DicomInputStream(bais)) {

            dis.readFileMetaInformation();
            Attributes attrs = dis.readDataset();

            int bitsAllocated = attrs.getInt(Tag.BitsAllocated, 16);
            int samplesPerPixel = attrs.getInt(Tag.SamplesPerPixel, 1);

            log.debug("Raster extraction: {}x{}, bitsAllocated={}, samplesPerPixel={}, bufferType={}",
                    width, height, bitsAllocated, samplesPerPixel, dataBuffer.getDataType());

            // 16-bit Grayscale (가장 일반적)
            if (dataBuffer.getDataType() == java.awt.image.DataBuffer.TYPE_USHORT ||
                dataBuffer.getDataType() == java.awt.image.DataBuffer.TYPE_SHORT) {

                // CRITICAL: DataBuffer 직접 접근 대신 Raster API 사용 (stride 자동 처리)
                // DataBuffer.getData()는 scanline stride를 고려하지 않아 이미지 아티팩트 발생
                // raster.getSamples()는 SampleModel의 stride를 고려하여 올바른 픽셀값 반환
                int[] samples = raster.getSamples(0, 0, width, height, 0, (int[]) null);

                // int[] → byte[] (Little Endian, 16-bit)
                byte[] pixelData = new byte[samples.length * 2];
                for (int i = 0; i < samples.length; i++) {
                    int value = samples[i] & 0xFFFF;  // unsigned 16-bit
                    pixelData[i * 2] = (byte) (value & 0xFF);        // LSB
                    pixelData[i * 2 + 1] = (byte) ((value >> 8) & 0xFF);  // MSB
                }

                log.debug("Extracted 16-bit raw pixels via getSamples(): {} samples → {} bytes",
                        samples.length, pixelData.length);
                return pixelData;
            }

            // 8-bit (RGB 또는 8-bit Grayscale)
            if (dataBuffer.getDataType() == java.awt.image.DataBuffer.TYPE_BYTE) {
                // CRITICAL: DataBuffer 직접 접근 대신 Raster API 사용 (stride 자동 처리)
                int[] samples;
                if (samplesPerPixel == 1) {
                    // Grayscale: band 0만
                    samples = raster.getSamples(0, 0, width, height, 0, (int[]) null);
                } else {
                    // RGB: 모든 band
                    samples = raster.getPixels(0, 0, width, height, (int[]) null);
                }

                byte[] pixelData = new byte[samples.length];
                for (int i = 0; i < samples.length; i++) {
                    pixelData[i] = (byte) (samples[i] & 0xFF);
                }

                log.debug("Extracted 8-bit raw pixels via getSamples(): {} bytes", pixelData.length);
                return pixelData;
            }

            // INT 타입 (드묾)
            if (dataBuffer.getDataType() == java.awt.image.DataBuffer.TYPE_INT) {
                // CRITICAL: DataBuffer 직접 접근 대신 Raster API 사용 (stride 자동 처리)
                int[] samples = raster.getPixels(0, 0, width, height, (int[]) null);

                // int[] → byte[] (RGB or 32-bit grayscale)
                if (samplesPerPixel == 3) {
                    // RGB - extract as 8-bit per channel (samples are already separated)
                    byte[] pixelData = new byte[samples.length];
                    for (int i = 0; i < samples.length; i++) {
                        pixelData[i] = (byte) (samples[i] & 0xFF);
                    }
                    log.debug("Extracted INT RGB pixels via getPixels(): {} bytes", pixelData.length);
                    return pixelData;
                }

                // 32-bit grayscale (매우 드묾)
                byte[] pixelData = new byte[samples.length * 4];
                for (int i = 0; i < samples.length; i++) {
                    int value = samples[i];
                    pixelData[i * 4] = (byte) (value & 0xFF);
                    pixelData[i * 4 + 1] = (byte) ((value >> 8) & 0xFF);
                    pixelData[i * 4 + 2] = (byte) ((value >> 16) & 0xFF);
                    pixelData[i * 4 + 3] = (byte) ((value >> 24) & 0xFF);
                }
                log.debug("Extracted 32-bit grayscale pixels via getPixels(): {} bytes", pixelData.length);
                return pixelData;
            }

            throw new BusinessException(MiniPacsErrorCode.DICOM_RENDER_FAILED,
                    "Unsupported DataBuffer type: " + dataBuffer.getDataType());
        }
    }

    /**
     * BufferedImage에서 raw PixelData 추출 (Legacy - extractRawBytesFromRaster 사용 권장)
     *
     * <p>WARNING: BufferedImage는 Window/Level이 이미 적용되어 있어 16-bit 데이터 손실 발생.
     * extractRawBytesFromRaster()를 대신 사용해야 함.
     *
     * @deprecated Use extractRawBytesFromRaster instead
     */
    @SuppressWarnings("unused")
    @Deprecated
    private byte[] extractRawPixelDataFromImage(BufferedImage image, byte[] dicomBytes) throws IOException {
        // DICOM 메타데이터에서 원본 정보 확인
        try (ByteArrayInputStream bais = new ByteArrayInputStream(dicomBytes);
             DicomInputStream dis = new DicomInputStream(bais)) {

            dis.readFileMetaInformation();
            Attributes attrs = dis.readDataset();

            int bitsAllocated = attrs.getInt(Tag.BitsAllocated, 16);
            int samplesPerPixel = attrs.getInt(Tag.SamplesPerPixel, 1);
            String photometricInterpretation = attrs.getString(Tag.PhotometricInterpretation, "MONOCHROME2");

            int width = image.getWidth();
            int height = image.getHeight();

            log.debug("Extracting raw pixels: {}x{}, bitsAllocated={}, samplesPerPixel={}, photometric={}",
                    width, height, bitsAllocated, samplesPerPixel, photometricInterpretation);

            // Grayscale (MONOCHROME1, MONOCHROME2)
            if (samplesPerPixel == 1) {
                return extractGrayscalePixelData(image, bitsAllocated);
            }

            // RGB
            if (samplesPerPixel == 3) {
                return extractRgbPixelData(image);
            }

            // Fallback: Grayscale 16-bit
            return extractGrayscalePixelData(image, 16);
        }
    }

    /**
     * Grayscale 이미지에서 raw PixelData 추출
     *
     * <p>BufferedImage의 Raster에서 직접 픽셀 데이터를 추출하여
     * 원본 비트 깊이를 유지합니다.
     *
     * @param image BufferedImage
     * @param bitsAllocated 픽셀당 비트 수 (8 또는 16)
     * @return Little Endian raw bytes
     */
    private byte[] extractGrayscalePixelData(BufferedImage image, int bitsAllocated) {
        int width = image.getWidth();
        int height = image.getHeight();

        // BufferedImage Raster에서 직접 데이터 추출 (원본 비트 깊이 유지)
        java.awt.image.Raster raster = image.getRaster();
        java.awt.image.DataBuffer dataBuffer = raster.getDataBuffer();

        log.debug("BufferedImage type: {}, DataBuffer type: {}",
                image.getType(), dataBuffer.getDataType());

        // 16-bit grayscale (DataBuffer.TYPE_USHORT 또는 TYPE_SHORT)
        if (dataBuffer.getDataType() == java.awt.image.DataBuffer.TYPE_USHORT ||
            dataBuffer.getDataType() == java.awt.image.DataBuffer.TYPE_SHORT) {

            short[] shortData;
            if (dataBuffer instanceof java.awt.image.DataBufferUShort) {
                shortData = ((java.awt.image.DataBufferUShort) dataBuffer).getData();
            } else if (dataBuffer instanceof java.awt.image.DataBufferShort) {
                shortData = ((java.awt.image.DataBufferShort) dataBuffer).getData();
            } else {
                // Fallback: 수동 추출
                shortData = new short[width * height];
                for (int y = 0; y < height; y++) {
                    for (int x = 0; x < width; x++) {
                        shortData[y * width + x] = (short) raster.getSample(x, y, 0);
                    }
                }
            }

            // short[] → byte[] (Little Endian)
            byte[] pixelData = new byte[shortData.length * 2];
            for (int i = 0; i < shortData.length; i++) {
                int value = shortData[i] & 0xFFFF;  // unsigned
                pixelData[i * 2] = (byte) (value & 0xFF);      // LSB
                pixelData[i * 2 + 1] = (byte) ((value >> 8) & 0xFF);  // MSB
            }

            log.debug("Extracted 16-bit grayscale: {} pixels → {} bytes", shortData.length, pixelData.length);
            return pixelData;
        }

        // 8-bit grayscale (DataBuffer.TYPE_BYTE)
        if (dataBuffer.getDataType() == java.awt.image.DataBuffer.TYPE_BYTE) {
            byte[] byteData;
            if (dataBuffer instanceof java.awt.image.DataBufferByte) {
                byteData = ((java.awt.image.DataBufferByte) dataBuffer).getData();
            } else {
                // Fallback: 수동 추출
                byteData = new byte[width * height];
                for (int y = 0; y < height; y++) {
                    for (int x = 0; x < width; x++) {
                        byteData[y * width + x] = (byte) raster.getSample(x, y, 0);
                    }
                }
            }

            // bitsAllocated가 16인데 8-bit로 디코딩된 경우 → 16-bit로 확장
            if (bitsAllocated > 8) {
                byte[] pixelData16 = new byte[byteData.length * 2];
                for (int i = 0; i < byteData.length; i++) {
                    int value = (byteData[i] & 0xFF) << 8;  // Scale 8-bit to 16-bit
                    pixelData16[i * 2] = (byte) (value & 0xFF);
                    pixelData16[i * 2 + 1] = (byte) ((value >> 8) & 0xFF);
                }
                log.debug("Extracted 8-bit→16-bit grayscale: {} pixels → {} bytes", byteData.length, pixelData16.length);
                return pixelData16;
            }

            log.debug("Extracted 8-bit grayscale: {} bytes", byteData.length);
            return byteData;
        }

        // Fallback: getRGB() 사용 (정확도 떨어질 수 있음)
        log.warn("Unknown DataBuffer type: {}, falling back to getRGB()", dataBuffer.getDataType());
        byte[] pixelData = new byte[width * height * 2];
        int idx = 0;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int rgb = image.getRGB(x, y);
                int gray = (rgb >> 16) & 0xFF;
                int gray16 = gray << 8;
                pixelData[idx++] = (byte) (gray16 & 0xFF);
                pixelData[idx++] = (byte) ((gray16 >> 8) & 0xFF);
            }
        }
        return pixelData;
    }

    /**
     * RGB 이미지에서 raw PixelData 추출 (interleaved RGB)
     */
    private byte[] extractRgbPixelData(BufferedImage image) {
        int width = image.getWidth();
        int height = image.getHeight();
        byte[] pixelData = new byte[width * height * 3];
        int idx = 0;

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int rgb = image.getRGB(x, y);
                pixelData[idx++] = (byte) ((rgb >> 16) & 0xFF);  // R
                pixelData[idx++] = (byte) ((rgb >> 8) & 0xFF);   // G
                pixelData[idx++] = (byte) (rgb & 0xFF);          // B
            }
        }
        return pixelData;
    }

    /**
     * (Legacy) 비압축 PixelData에서 특정 프레임 추출
     * 현재는 extractFramePixelData에서 DCM4CHE ImageIO 사용
     */
    @SuppressWarnings("unused")
    private byte[] extractFramePixelDataLegacy(String storagePath, int frameNumber) throws IOException {
        try (InputStream dicomStream = dicomStorageService.downloadDicomFile(storagePath)) {
            byte[] dicomBytes = dicomStream.readAllBytes();

            try (ByteArrayInputStream bais = new ByteArrayInputStream(dicomBytes);
                 DicomInputStream dis = new DicomInputStream(bais)) {

                Attributes fmi = dis.readFileMetaInformation();
                String transferSyntaxUid = fmi != null ? fmi.getString(Tag.TransferSyntaxUID) : UID.ExplicitVRLittleEndian;
                Attributes attrs = dis.readDataset();

                int frameIndex = frameNumber - 1;
                int totalFrames = attrs.getInt(Tag.NumberOfFrames, 1);

                if (frameIndex < 0 || frameIndex >= totalFrames) {
                    throw new BusinessException(MiniPacsErrorCode.INVALID_FRAME_NUMBER,
                            String.format("Frame %d out of range (1-%d)", frameNumber, totalFrames));
                }

                boolean isCompressed = isCompressedTransferSyntax(transferSyntaxUid);

                byte[] frameData;
                if (isCompressed) {
                    frameData = extractCompressedFrameData(attrs, frameIndex);
                } else {
                    frameData = extractUncompressedFrameData(attrs, frameIndex);
                }

                return frameData;
            }
        }
    }

    /**
     * Transfer Syntax가 압축인지 확인
     *
     * @param uid Transfer Syntax UID
     * @return 압축이면 true, 비압축이면 false
     */
    public boolean isCompressedTransferSyntax(String uid) {
        if (uid == null) return false;
        return !UID.ImplicitVRLittleEndian.equals(uid)
                && !UID.ExplicitVRLittleEndian.equals(uid)
                && !UID.ExplicitVRBigEndian.equals(uid);
    }

    /**
     * 비압축 PixelData에서 특정 프레임 추출
     */
    private byte[] extractUncompressedFrameData(Attributes attrs, int frameIndex) {
        // 이미지 파라미터
        int rows = attrs.getInt(Tag.Rows, 0);
        int cols = attrs.getInt(Tag.Columns, 0);
        int bitsAllocated = attrs.getInt(Tag.BitsAllocated, 16);
        int samplesPerPixel = attrs.getInt(Tag.SamplesPerPixel, 1);

        // 프레임 크기 계산
        int bytesPerPixel = bitsAllocated / 8;
        int frameSize = rows * cols * bytesPerPixel * samplesPerPixel;

        log.debug("Uncompressed frame params: {}x{}, bitsAllocated={}, samplesPerPixel={}, frameSize={}",
                rows, cols, bitsAllocated, samplesPerPixel, frameSize);

        // PixelData 추출
        byte[] pixelData = attrs.getSafeBytes(Tag.PixelData);
        if (pixelData == null || pixelData.length == 0) {
            throw new BusinessException(MiniPacsErrorCode.DICOM_RENDER_FAILED, "PixelData not found");
        }

        // 프레임 오프셋 계산
        int frameOffset = frameIndex * frameSize;

        if (frameOffset + frameSize > pixelData.length) {
            throw new BusinessException(MiniPacsErrorCode.DICOM_RENDER_FAILED,
                    String.format("Frame offset out of bounds: offset=%d, frameSize=%d, totalSize=%d",
                            frameOffset, frameSize, pixelData.length));
        }

        return Arrays.copyOfRange(pixelData, frameOffset, frameOffset + frameSize);
    }

    /**
     * 압축된 PixelData (Encapsulated)에서 특정 프레임 추출
     */
    private byte[] extractCompressedFrameData(Attributes attrs, int frameIndex) {
        // PixelData가 Fragments인지 확인
        Object pixelDataValue = attrs.getValue(Tag.PixelData);

        if (pixelDataValue instanceof Fragments fragments) {
            // Fragments 구조: [Basic Offset Table, Frame1, Frame2, ...]
            // Basic Offset Table은 index 0, 실제 프레임은 index 1부터
            int fragmentIndex = frameIndex + 1;  // Skip Basic Offset Table

            if (fragmentIndex >= fragments.size()) {
                throw new BusinessException(MiniPacsErrorCode.DICOM_RENDER_FAILED,
                        String.format("Frame fragment %d not found (total fragments: %d)",
                                fragmentIndex, fragments.size()));
            }

            Object fragment = fragments.get(fragmentIndex);
            if (fragment instanceof byte[] frameData) {
                return frameData;
            } else {
                throw new BusinessException(MiniPacsErrorCode.DICOM_RENDER_FAILED,
                        "Fragment is not byte array: " + fragment.getClass().getName());
            }

        } else if (pixelDataValue instanceof byte[] rawData) {
            // 일부 단일 프레임 압축 DICOM은 Fragments가 아닐 수 있음
            log.warn("Compressed PixelData is not Fragments, returning raw data");
            return rawData;

        } else {
            throw new BusinessException(MiniPacsErrorCode.DICOM_RENDER_FAILED,
                    "Unknown PixelData type: " + (pixelDataValue != null ? pixelDataValue.getClass().getName() : "null"));
        }
    }

    /**
     * DICOM 파일의 Transfer Syntax UID 조회
     *
     * @param storagePath S3 저장 경로
     * @return Transfer Syntax UID (없으면 Explicit VR Little Endian 반환)
     */
    public String getTransferSyntaxUid(String storagePath) {
        try (InputStream dicomStream = dicomStorageService.downloadDicomFile(storagePath);
             DicomInputStream dis = new DicomInputStream(dicomStream)) {
            Attributes fmi = dis.readFileMetaInformation();

            if (fmi != null) {
                return fmi.getString(Tag.TransferSyntaxUID, UID.ExplicitVRLittleEndian);
            }
            return UID.ExplicitVRLittleEndian;

        } catch (IOException e) {
            log.error("Failed to get transfer syntax: path={}", storagePath, e);
            return UID.ExplicitVRLittleEndian;
        }
    }

    /**
     * DICOM 파일의 총 프레임 수 조회
     *
     * @param storagePath S3 저장 경로
     * @return 프레임 수 (싱글프레임이면 1)
     */
    public int getNumberOfFrames(String storagePath) {
        try (InputStream dicomStream = dicomStorageService.downloadDicomFile(storagePath);
             DicomInputStream dis = new DicomInputStream(dicomStream)) {
            Attributes attrs = dis.readDataset();

            int frames = attrs.getInt(Tag.NumberOfFrames, 1);
            log.debug("Number of frames: {}", frames);

            return frames;

        } catch (IOException e) {
            log.error("Failed to get number of frames: path={}", storagePath, e);
            return 1;
        }
    }

    // ==================== 메모리 최적화: Streaming Methods ====================

    /**
     * DICOM 파일을 PNG 이미지로 렌더링하여 OutputStream에 직접 쓰기 (메모리 최적화)
     *
     * <p>성능 최적화 (2026-01-09):
     * <ul>
     *   <li>기존: byte[] 전체 메모리 로드 후 응답 → 동시 10개 요청 시 2.5GB 메모리</li>
     *   <li>개선: StreamingResponseBody로 직접 스트리밍 → 메모리 90% 절감</li>
     * </ul>
     *
     * <p>WARNING: 이 메서드는 호출자가 OutputStream을 관리해야 합니다.
     * Spring StreamingResponseBody와 함께 사용하면 자동으로 관리됩니다.
     *
     * @param storagePath S3 저장 경로
     * @param frameNumber 프레임 번호 (1부터 시작)
     * @param outputStream 결과를 쓸 OutputStream
     * @throws BusinessException DICOM_RENDER_FAILED - 렌더링 실패 시
     */
    public void renderToPngStream(String storagePath, int frameNumber, java.io.OutputStream outputStream) {
        log.debug("Streaming DICOM to PNG: path={}, frame={}", storagePath, frameNumber);

        try (InputStream dicomStream = dicomStorageService.downloadDicomFile(storagePath)) {
            // DICOM 파일을 메모리로 읽기 (ImageIO가 seekable stream 필요)
            byte[] dicomBytes = dicomStream.readAllBytes();

            // BufferedImage 추출
            BufferedImage image = extractFrame(dicomBytes, frameNumber);

            // PNG로 직접 스트리밍 (byte[] 중간 버퍼 없음)
            boolean written = ImageIO.write(image, "PNG", outputStream);

            if (!written) {
                throw new BusinessException(MiniPacsErrorCode.DICOM_RENDER_FAILED, "PNG 인코딩 실패");
            }

            outputStream.flush();
            log.debug("Streamed PNG for frame {} successfully", frameNumber);

        } catch (BusinessException e) {
            throw e;
        } catch (IOException e) {
            log.error("DICOM streaming failed: path={}, frame={}", storagePath, frameNumber, e);
            throw new BusinessException(MiniPacsErrorCode.DICOM_RENDER_FAILED, e.getMessage());
        }
    }

    /**
     * Multipart/Related 형식으로 Frame PixelData를 OutputStream에 직접 쓰기 (메모리 최적화)
     *
     * <p>성능 최적화 (2026-01-09):
     * <ul>
     *   <li>기존: byte[] pixelData + byte[] multipartBody 두 번 복사</li>
     *   <li>개선: StreamingResponseBody로 한 번만 쓰기 → 메모리 50% 절감</li>
     * </ul>
     *
     * @param storagePath S3 저장 경로
     * @param frameNumber 프레임 번호 (1부터 시작)
     * @param boundary multipart boundary 문자열
     * @param transferSyntax Transfer Syntax UID
     * @param mimeType Content-Type MIME 타입
     * @param outputStream 결과를 쓸 OutputStream
     * @return 추출된 pixelData 크기 (bytes)
     * @throws BusinessException DICOM_RENDER_FAILED - 추출 실패 시
     */
    public int extractFramePixelDataToStream(
            String storagePath,
            int frameNumber,
            String boundary,
            String transferSyntax,
            String mimeType,
            java.io.OutputStream outputStream) {

        log.debug("Streaming Frame PixelData: path={}, frame={}", storagePath, frameNumber);

        try {
            // PixelData 추출
            byte[] pixelData = extractFramePixelData(storagePath, frameNumber);

            // Multipart 헤더 작성
            String partHeader = "--" + boundary + "\r\n"
                    + "Content-Type: " + mimeType + "; transfer-syntax=" + transferSyntax + "\r\n"
                    + "Content-Length: " + pixelData.length + "\r\n"
                    + "\r\n";

            outputStream.write(partHeader.getBytes(java.nio.charset.StandardCharsets.UTF_8));

            // PixelData 본문 직접 쓰기 (복사 없음)
            outputStream.write(pixelData);

            // Part 종료
            String partFooter = "\r\n--" + boundary + "--\r\n";
            outputStream.write(partFooter.getBytes(java.nio.charset.StandardCharsets.UTF_8));

            outputStream.flush();
            log.debug("Streamed Frame {} PixelData: {} bytes", frameNumber, pixelData.length);

            return pixelData.length;

        } catch (BusinessException e) {
            throw e;
        } catch (IOException e) {
            log.error("Frame PixelData streaming failed: path={}, frame={}", storagePath, frameNumber, e);
            throw new BusinessException(MiniPacsErrorCode.DICOM_RENDER_FAILED, e.getMessage());
        }
    }

    // ==================== 다중 프레임 최적화: DICOMweb FrameList 지원 ====================

    /**
     * 다중 프레임 PixelData를 Multipart 형식으로 OutputStream에 직접 쓰기 (DICOMweb Part 18 표준)
     *
     * <p>DICOMweb FrameList 표준 (/frames/1,2,3,4,5) 지원으로 HTTP 요청 최적화.
     *
     * <p>성능 최적화:
     * <ul>
     *   <li>DICOM 파일 1회만 로드 (프레임 10개 요청 시 기존 10회 I/O → 1회)</li>
     *   <li>ImageReader 1회 초기화로 다중 프레임 추출</li>
     *   <li>StreamingResponseBody로 메모리 최적화</li>
     * </ul>
     *
     * <p>응답 형식 (DICOMweb Part 18 Section 8.7.3.4):
     * <pre>
     * --boundary
     * Content-Type: application/octet-stream; transfer-syntax=...
     * Content-Location: frames/1
     * Content-Length: ...
     *
     * [PixelData bytes]
     * --boundary
     * Content-Type: application/octet-stream; transfer-syntax=...
     * Content-Location: frames/2
     * ...
     * --boundary--
     * </pre>
     *
     * @param storagePath S3 저장 경로
     * @param frameNumbers 프레임 번호 목록 (1부터 시작)
     * @param boundary multipart boundary 문자열
     * @param transferSyntax Transfer Syntax UID
     * @param mimeType Content-Type MIME 타입
     * @param outputStream 결과를 쓸 OutputStream
     * @return 추출된 총 pixelData 크기 (bytes)
     * @throws BusinessException DICOM_RENDER_FAILED - 추출 실패 시
     */
    public int extractMultipleFramesToStream(
            String storagePath,
            List<Integer> frameNumbers,
            String boundary,
            String transferSyntax,
            String mimeType,
            java.io.OutputStream outputStream) {

        log.info("Extracting multiple frames (DICOMweb FrameList): path={}, frames={}", storagePath, frameNumbers);

        try (InputStream dicomStream = dicomStorageService.downloadDicomFile(storagePath)) {
            // CRITICAL: DICOM 파일 1회만 로드 (기존: 프레임 수만큼 반복 로드)
            byte[] dicomBytes = dicomStream.readAllBytes();

            // Transfer Syntax 로깅
            logTransferSyntax(dicomBytes);

            // DCM4CHE ImageIO 사용
            Iterator<ImageReader> readers = ImageIO.getImageReadersByFormatName("DICOM");
            if (!readers.hasNext()) {
                throw new BusinessException(MiniPacsErrorCode.DICOM_RENDER_FAILED,
                        "DICOM ImageReader not found. DCM4CHE ImageIO is not installed.");
            }

            ImageReader reader = readers.next();
            int totalPixelDataSize = 0;

            try (ByteArrayInputStream bais = new ByteArrayInputStream(dicomBytes);
                 ImageInputStream iis = ImageIO.createImageInputStream(bais)) {

                reader.setInput(iis);
                int numFrames = reader.getNumImages(true);

                log.debug("Multi-frame extraction: {} frames requested, {} total frames in DICOM",
                        frameNumbers.size(), numFrames);

                // 각 프레임 추출 및 multipart 파트로 출력
                for (int frameNumber : frameNumbers) {
                    int frameIndex = frameNumber - 1;

                    if (frameIndex < 0 || frameIndex >= numFrames) {
                        throw new BusinessException(MiniPacsErrorCode.INVALID_FRAME_NUMBER,
                                String.format("Frame %d out of range (1-%d)", frameNumber, numFrames));
                    }

                    // CRITICAL: readRaster()로 W/L 미적용 raw pixels 추출
                    java.awt.image.Raster raster = reader.readRaster(frameIndex, null);
                    byte[] pixelData = extractRawBytesFromRaster(raster, dicomBytes);

                    // Multipart 파트 헤더 작성 (DICOMweb Part 18 표준)
                    String partHeader = "--" + boundary + "\r\n"
                            + "Content-Type: " + mimeType + "; transfer-syntax=" + transferSyntax + "\r\n"
                            + "Content-Location: /frames/" + frameNumber + "\r\n"
                            + "Content-Length: " + pixelData.length + "\r\n"
                            + "\r\n";

                    outputStream.write(partHeader.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                    outputStream.write(pixelData);
                    outputStream.write("\r\n".getBytes(java.nio.charset.StandardCharsets.UTF_8));

                    totalPixelDataSize += pixelData.length;
                    log.debug("Extracted frame {}/{}: {} bytes", frameNumber, numFrames, pixelData.length);
                }

                // 종료 boundary
                String endBoundary = "--" + boundary + "--\r\n";
                outputStream.write(endBoundary.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                outputStream.flush();

                log.info("Multi-frame extraction complete: {} frames, {} total bytes",
                        frameNumbers.size(), totalPixelDataSize);
                return totalPixelDataSize;

            } finally {
                reader.dispose();
            }

        } catch (BusinessException e) {
            throw e;
        } catch (IOException e) {
            log.error("Failed to extract multiple frames: path={}, frames={}", storagePath, frameNumbers, e);
            throw new BusinessException(MiniPacsErrorCode.DICOM_RENDER_FAILED, e.getMessage());
        }
    }

    /**
     * 다중 프레임을 PNG로 렌더링하여 Multipart 형식으로 출력 (DICOMweb Part 18 표준)
     *
     * <p>WADO-RS Rendered API의 FrameList 지원.
     *
     * @param storagePath S3 저장 경로
     * @param frameNumbers 프레임 번호 목록 (1부터 시작)
     * @param boundary multipart boundary 문자열
     * @param outputStream 결과를 쓸 OutputStream
     * @throws BusinessException DICOM_RENDER_FAILED - 렌더링 실패 시
     */
    public void renderMultipleFramesToStream(
            String storagePath,
            List<Integer> frameNumbers,
            String boundary,
            java.io.OutputStream outputStream) {

        log.info("Rendering multiple frames to PNG (DICOMweb FrameList): path={}, frames={}",
                storagePath, frameNumbers);

        try (InputStream dicomStream = dicomStorageService.downloadDicomFile(storagePath)) {
            // CRITICAL: DICOM 파일 1회만 로드
            byte[] dicomBytes = dicomStream.readAllBytes();

            Iterator<ImageReader> readers = ImageIO.getImageReadersByFormatName("DICOM");
            if (!readers.hasNext()) {
                throw new BusinessException(MiniPacsErrorCode.DICOM_RENDER_FAILED,
                        "DICOM ImageReader not found. DCM4CHE ImageIO is not installed.");
            }

            ImageReader reader = readers.next();

            try (ByteArrayInputStream bais = new ByteArrayInputStream(dicomBytes);
                 ImageInputStream iis = ImageIO.createImageInputStream(bais)) {

                reader.setInput(iis);
                int numFrames = reader.getNumImages(true);

                DicomImageReadParam param = (DicomImageReadParam) reader.getDefaultReadParam();

                int totalPngSize = 0;
                for (int frameNumber : frameNumbers) {
                    int frameIndex = frameNumber - 1;

                    if (frameIndex < 0 || frameIndex >= numFrames) {
                        throw new BusinessException(MiniPacsErrorCode.INVALID_FRAME_NUMBER,
                                String.format("Frame %d out of range (1-%d)", frameNumber, numFrames));
                    }

                    // BufferedImage로 렌더링 (W/L 적용)
                    BufferedImage image = reader.read(frameIndex, param);

                    // PNG로 변환
                    ByteArrayOutputStream pngBuffer = new ByteArrayOutputStream();
                    boolean written = ImageIO.write(image, "PNG", pngBuffer);
                    if (!written) {
                        throw new BusinessException(MiniPacsErrorCode.DICOM_RENDER_FAILED,
                                "Failed to encode frame " + frameNumber + " to PNG");
                    }
                    byte[] pngData = pngBuffer.toByteArray();

                    // Multipart 파트 작성
                    String partHeader = "--" + boundary + "\r\n"
                            + "Content-Type: image/png\r\n"
                            + "Content-Location: /frames/" + frameNumber + "/rendered\r\n"
                            + "Content-Length: " + pngData.length + "\r\n"
                            + "\r\n";

                    outputStream.write(partHeader.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                    outputStream.write(pngData);
                    outputStream.write("\r\n".getBytes(java.nio.charset.StandardCharsets.UTF_8));

                    totalPngSize += pngData.length;
                    log.debug("Rendered frame {}/{} to PNG: {} bytes", frameNumber, numFrames, pngData.length);
                }

                // 종료 boundary
                String endBoundary = "--" + boundary + "--\r\n";
                outputStream.write(endBoundary.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                outputStream.flush();

                log.info("Multi-frame PNG rendering complete: {} frames, {} total bytes",
                        frameNumbers.size(), totalPngSize);

            } finally {
                reader.dispose();
            }

        } catch (BusinessException e) {
            throw e;
        } catch (IOException e) {
            log.error("Failed to render multiple frames: path={}, frames={}", storagePath, frameNumbers, e);
            throw new BusinessException(MiniPacsErrorCode.DICOM_RENDER_FAILED, e.getMessage());
        }
    }

    // ==================== WADO-URI 썸네일 지원 ====================

    /**
     * DICOM을 지정 크기의 JPEG로 렌더링하여 스트리밍 (WADO-URI 썸네일용)
     *
     * <p>WADO-URI contentType=image/jpeg, rows, columns 파라미터 지원
     * 비율을 유지하면서 지정된 크기로 리사이징합니다.
     *
     * @param storagePath S3 저장 경로
     * @param frameNumber 프레임 번호 (1부터 시작)
     * @param targetWidth 목표 너비 (pixels)
     * @param targetHeight 목표 높이 (pixels)
     * @param outputStream 결과를 쓸 OutputStream
     * @throws BusinessException DICOM_RENDER_FAILED - 렌더링 실패 시
     */
    public void renderToJpegStream(
            String storagePath,
            int frameNumber,
            int targetWidth,
            int targetHeight,
            java.io.OutputStream outputStream) {

        log.debug("Rendering DICOM to JPEG thumbnail: path={}, frame={}, size={}x{}",
                storagePath, frameNumber, targetWidth, targetHeight);

        try (InputStream dicomStream = dicomStorageService.downloadDicomFile(storagePath)) {
            // DICOM 파일을 메모리로 읽기 (ImageIO가 seekable stream 필요)
            byte[] dicomBytes = dicomStream.readAllBytes();

            // BufferedImage 추출 (기존 extractFrame 재사용)
            BufferedImage original = extractFrame(dicomBytes, frameNumber);

            // 리사이징 (비율 유지)
            BufferedImage resized = resizeImage(original, targetWidth, targetHeight);

            // JPEG로 직접 스트리밍
            boolean written = ImageIO.write(resized, "JPEG", outputStream);

            if (!written) {
                throw new BusinessException(MiniPacsErrorCode.DICOM_RENDER_FAILED, "JPEG 인코딩 실패");
            }

            outputStream.flush();
            log.debug("Rendered JPEG thumbnail: {}x{} → {}x{}",
                    original.getWidth(), original.getHeight(),
                    resized.getWidth(), resized.getHeight());

        } catch (BusinessException e) {
            throw e;
        } catch (IOException e) {
            log.error("DICOM to JPEG rendering failed: path={}, frame={}", storagePath, frameNumber, e);
            throw new BusinessException(MiniPacsErrorCode.DICOM_RENDER_FAILED, e.getMessage());
        }
    }

    /**
     * DICOM을 지정 품질의 JPEG로 렌더링 (WADO-RS Rendered API용)
     *
     * <p>DICOMweb 표준 quality 파라미터 지원.
     * quality 1-100: 1=최저품질/최소크기, 100=최고품질/최대크기
     *
     * @param storagePath S3 저장 경로
     * @param frameNumber 프레임 번호 (1부터 시작)
     * @param quality JPEG 품질 (1-100)
     * @param outputStream 결과를 쓸 OutputStream
     * @throws BusinessException DICOM_RENDER_FAILED - 렌더링 실패 시
     */
    public void renderToJpegStreamWithQuality(
            String storagePath,
            int frameNumber,
            int quality,
            java.io.OutputStream outputStream) {

        log.debug("Rendering DICOM to JPEG: path={}, frame={}, quality={}",
                storagePath, frameNumber, quality);

        try (InputStream dicomStream = dicomStorageService.downloadDicomFile(storagePath)) {
            byte[] dicomBytes = dicomStream.readAllBytes();
            BufferedImage image = extractFrame(dicomBytes, frameNumber);

            // JPEG 품질 설정하여 스트리밍
            writeJpegWithQuality(image, quality, outputStream);

            outputStream.flush();
            log.debug("Rendered JPEG: quality={}, size={}x{}",
                    quality, image.getWidth(), image.getHeight());

        } catch (BusinessException e) {
            throw e;
        } catch (IOException e) {
            log.error("DICOM to JPEG rendering failed: path={}, frame={}, quality={}",
                    storagePath, frameNumber, quality, e);
            throw new BusinessException(MiniPacsErrorCode.DICOM_RENDER_FAILED, e.getMessage());
        }
    }

    /**
     * BufferedImage를 지정 품질의 JPEG로 스트리밍
     *
     * @param image 원본 이미지
     * @param quality JPEG 품질 (1-100)
     * @param outputStream 출력 스트림
     * @throws IOException 인코딩 실패
     */
    private void writeJpegWithQuality(BufferedImage image, int quality, java.io.OutputStream outputStream)
            throws IOException {

        // JPEG Writer 획득
        javax.imageio.ImageWriter jpegWriter = javax.imageio.ImageIO.getImageWritersByFormatName("JPEG").next();

        // 품질 설정 (0.0 ~ 1.0)
        javax.imageio.ImageWriteParam writeParam = jpegWriter.getDefaultWriteParam();
        writeParam.setCompressionMode(javax.imageio.ImageWriteParam.MODE_EXPLICIT);
        writeParam.setCompressionQuality(quality / 100.0f);  // 1-100 → 0.01-1.0

        // OutputStream에 직접 쓰기
        try (javax.imageio.stream.ImageOutputStream imageOutputStream =
                javax.imageio.ImageIO.createImageOutputStream(outputStream)) {

            jpegWriter.setOutput(imageOutputStream);
            jpegWriter.write(null, new javax.imageio.IIOImage(image, null, null), writeParam);
        } finally {
            jpegWriter.dispose();
        }
    }

    /**
     * DICOM을 이미지로 렌더링 (quality + viewport 통합 메서드)
     *
     * <p>DICOMweb 표준 quality/rows/columns 파라미터 지원.
     * - quality 1-99: JPEG 출력
     * - quality 100: PNG 출력
     * - rows/columns: 다운샘플링 (비율 유지)
     *
     * @param storagePath S3 저장 경로
     * @param frameNumber 프레임 번호 (1부터 시작)
     * @param quality 이미지 품질 (1-100). 100=PNG, <100=JPEG
     * @param rows 목표 높이 (null이면 원본 유지)
     * @param columns 목표 너비 (null이면 원본 유지)
     * @param outputStream 결과를 쓸 OutputStream
     * @throws BusinessException DICOM_RENDER_FAILED - 렌더링 실패 시
     */
    public void renderToStreamWithOptions(
            String storagePath,
            int frameNumber,
            int quality,
            Integer rows,
            Integer columns,
            java.io.OutputStream outputStream) {

        log.debug("Rendering DICOM with options: path={}, frame={}, quality={}, rows={}, columns={}",
                storagePath, frameNumber, quality, rows, columns);

        try (InputStream dicomStream = dicomStorageService.downloadDicomFile(storagePath)) {
            byte[] dicomBytes = dicomStream.readAllBytes();
            BufferedImage image = extractFrame(dicomBytes, frameNumber);

            // 리사이징 (rows/columns 지정 시)
            if (rows != null || columns != null) {
                int targetWidth = columns != null ? columns : image.getWidth();
                int targetHeight = rows != null ? rows : image.getHeight();
                image = resizeImage(image, targetWidth, targetHeight);
            }

            // 품질에 따라 JPEG 또는 PNG 출력
            if (quality < 100) {
                writeJpegWithQuality(image, quality, outputStream);
            } else {
                javax.imageio.ImageIO.write(image, "PNG", outputStream);
            }

            outputStream.flush();
            log.debug("Rendered image: quality={}, finalSize={}x{}",
                    quality, image.getWidth(), image.getHeight());

        } catch (BusinessException e) {
            throw e;
        } catch (IOException e) {
            log.error("DICOM rendering failed: path={}, frame={}, quality={}, rows={}, columns={}",
                    storagePath, frameNumber, quality, rows, columns, e);
            throw new BusinessException(MiniPacsErrorCode.DICOM_RENDER_FAILED, e.getMessage());
        }
    }

    /**
     * 이미지 리사이징 (비율 유지)
     *
     * <p>원본 비율을 유지하면서 지정된 크기에 맞게 축소합니다.
     * 고품질 Bilinear Interpolation 사용.
     *
     * @param original 원본 BufferedImage
     * @param targetWidth 목표 너비
     * @param targetHeight 목표 높이
     * @return 리사이징된 BufferedImage
     */
    private BufferedImage resizeImage(BufferedImage original, int targetWidth, int targetHeight) {
        // 비율 계산 (축소만, 확대하지 않음)
        double widthRatio = (double) targetWidth / original.getWidth();
        double heightRatio = (double) targetHeight / original.getHeight();
        double ratio = Math.min(widthRatio, heightRatio);

        // 원본이 목표보다 작으면 리사이징 하지 않음
        if (ratio >= 1.0) {
            return original;
        }

        int newWidth = (int) (original.getWidth() * ratio);
        int newHeight = (int) (original.getHeight() * ratio);

        // 최소 1픽셀 보장
        newWidth = Math.max(1, newWidth);
        newHeight = Math.max(1, newHeight);

        // 고품질 리사이징
        BufferedImage resized = new BufferedImage(newWidth, newHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = resized.createGraphics();

        // 렌더링 힌트 설정 (고품질)
        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        g2d.drawImage(original, 0, 0, newWidth, newHeight, null);
        g2d.dispose();

        log.debug("Resized image: {}x{} -> {}x{} (ratio: {})",
                original.getWidth(), original.getHeight(), newWidth, newHeight,
                String.format("%.2f", ratio));

        return resized;
    }

    // ==================== Transfer Syntax 유지 최적화 ====================

    /**
     * Transfer Syntax UID를 MIME Type으로 변환
     *
     * <p>DICOMweb Part 18, Section 8.7.3.4 표준 준수:
     * 압축된 프레임 데이터는 해당 압축 포맷의 MIME 타입으로 반환해야 함
     *
     * @param tsUid Transfer Syntax UID
     * @return 해당하는 MIME Type (비압축/알 수 없는 경우 application/octet-stream)
     */
    public String getMimeTypeForTransferSyntax(String tsUid) {
        if (tsUid == null) return "application/octet-stream";

        return switch (tsUid) {
            // JPEG 2000 (Lossless 및 Lossy)
            case UID.JPEG2000Lossless, UID.JPEG2000 -> "image/jp2";
            // JPEG (Baseline, Extended, Lossless)
            case UID.JPEGBaseline8Bit, UID.JPEGExtended12Bit,
                 UID.JPEGLossless, UID.JPEGLosslessSV1 -> "image/jpeg";
            // JPEG-LS
            case UID.JPEGLSLossless, UID.JPEGLSNearLossless -> "image/jls";
            // RLE (별도 MIME 타입 없음)
            case UID.RLELossless -> "application/octet-stream";
            // 비압축 (Implicit/Explicit VR Little/Big Endian)
            default -> "application/octet-stream";
        };
    }

    /**
     * 압축 유지/해제 선택 가능한 프레임 데이터 추출
     *
     * <p>WADO-RS BulkData API에서 Transfer Syntax 유지 최적화에 사용됩니다.
     * 압축된 DICOM의 경우 원본 압축 데이터를 그대로 반환하여 네트워크 전송량을 80-90% 감소시킵니다.
     *
     * <p>동작 방식:
     * <ul>
     *   <li>preserveCompression=true + 압축 DICOM: 원본 압축 데이터 반환 (50KB)</li>
     *   <li>preserveCompression=false 또는 비압축 DICOM: Raw PixelData 반환 (512KB)</li>
     * </ul>
     *
     * @param storagePath S3 저장 경로
     * @param frameNumber 프레임 번호 (1-based)
     * @param preserveCompression true면 압축 유지, false면 디코딩
     * @return FrameExtractionResult (데이터 + Transfer Syntax + MIME 타입)
     * @throws BusinessException DICOM_RENDER_FAILED - 추출 실패 시
     */
    public com.hanumoka.sado.minipacs.dto.FrameExtractionResult extractFrameDataPreservingTransferSyntax(
            String storagePath, int frameNumber, boolean preserveCompression) {

        log.debug("Extracting frame with compression preservation: path={}, frame={}, preserve={}",
                storagePath, frameNumber, preserveCompression);

        try (InputStream dicomStream = dicomStorageService.downloadDicomFile(storagePath)) {
            byte[] dicomBytes = dicomStream.readAllBytes();

            try (ByteArrayInputStream bais = new ByteArrayInputStream(dicomBytes);
                 DicomInputStream dis = new DicomInputStream(bais)) {

                // 1. File Meta Information에서 Transfer Syntax 확인
                Attributes fmi = dis.readFileMetaInformation();
                String transferSyntaxUid = fmi != null
                        ? fmi.getString(Tag.TransferSyntaxUID, UID.ExplicitVRLittleEndian)
                        : UID.ExplicitVRLittleEndian;

                boolean isCompressed = isCompressedTransferSyntax(transferSyntaxUid);

                // 2. Dataset 읽기
                Attributes attrs = dis.readDataset();
                int frameIndex = frameNumber - 1;
                int totalFrames = attrs.getInt(Tag.NumberOfFrames, 1);

                if (frameIndex < 0 || frameIndex >= totalFrames) {
                    throw new BusinessException(MiniPacsErrorCode.INVALID_FRAME_NUMBER,
                            String.format("Frame %d out of range (1-%d)", frameNumber, totalFrames));
                }

                // 3. 압축 유지 요청 + 실제로 압축된 경우
                if (preserveCompression && isCompressed) {
                    byte[] compressedData = extractCompressedFrameData(attrs, frameIndex);
                    String mimeType = getMimeTypeForTransferSyntax(transferSyntaxUid);

                    log.info("Extracted compressed frame {}/{}: {} bytes, ts={}, mime={}",
                            frameNumber, totalFrames, compressedData.length, transferSyntaxUid, mimeType);

                    return new com.hanumoka.sado.minipacs.dto.FrameExtractionResult(
                            compressedData,
                            transferSyntaxUid,
                            mimeType
                    );
                }

                // 4. 비압축 또는 디코딩 요청: 기존 extractFramePixelData 사용
                byte[] rawPixels = extractFramePixelData(storagePath, frameNumber);

                log.info("Extracted decompressed frame {}/{}: {} bytes (from compressed={})",
                        frameNumber, totalFrames, rawPixels.length, isCompressed);

                return new com.hanumoka.sado.minipacs.dto.FrameExtractionResult(
                        rawPixels,
                        UID.ExplicitVRLittleEndian,
                        "application/octet-stream"
                );
            }

        } catch (BusinessException e) {
            throw e;
        } catch (IOException e) {
            log.error("Failed to extract frame with compression preservation: path={}, frame={}",
                    storagePath, frameNumber, e);
            throw new BusinessException(MiniPacsErrorCode.DICOM_RENDER_FAILED, e.getMessage());
        }
    }

    /**
     * 압축 유지 프레임 데이터를 Multipart 형식으로 스트리밍
     *
     * @param storagePath S3 저장 경로
     * @param frameNumber 프레임 번호 (1-based)
     * @param boundary multipart boundary 문자열
     * @param preserveCompression 압축 유지 여부
     * @param outputStream 출력 스트림
     * @return 추출된 데이터 크기 (bytes)
     */
    public int extractFrameDataPreservingTransferSyntaxToStream(
            String storagePath,
            int frameNumber,
            String boundary,
            boolean preserveCompression,
            java.io.OutputStream outputStream) {

        log.debug("Streaming frame with compression preservation: path={}, frame={}, preserve={}",
                storagePath, frameNumber, preserveCompression);

        try {
            // 프레임 추출
            var result = extractFrameDataPreservingTransferSyntax(storagePath, frameNumber, preserveCompression);

            // Multipart 헤더 작성
            String partHeader = "--" + boundary + "\r\n"
                    + "Content-Type: " + result.mimeType() + "; transfer-syntax=" + result.transferSyntaxUid() + "\r\n"
                    + "Content-Length: " + result.dataSize() + "\r\n"
                    + "\r\n";

            outputStream.write(partHeader.getBytes(java.nio.charset.StandardCharsets.UTF_8));

            // 데이터 본문
            outputStream.write(result.data());

            // Part 종료
            String partFooter = "\r\n--" + boundary + "--\r\n";
            outputStream.write(partFooter.getBytes(java.nio.charset.StandardCharsets.UTF_8));

            outputStream.flush();

            log.debug("Streamed frame {} with compression={}: {} bytes, mime={}",
                    frameNumber, result.isCompressed(), result.dataSize(), result.mimeType());

            return result.dataSize();

        } catch (BusinessException e) {
            throw e;
        } catch (IOException e) {
            log.error("Failed to stream frame: path={}, frame={}", storagePath, frameNumber, e);
            throw new BusinessException(MiniPacsErrorCode.DICOM_RENDER_FAILED, e.getMessage());
        }
    }

    /**
     * 다중 프레임 압축 유지 추출 및 스트리밍
     *
     * @param storagePath S3 저장 경로
     * @param frameNumbers 프레임 번호 목록 (1-based)
     * @param boundary multipart boundary 문자열
     * @param preserveCompression 압축 유지 여부
     * @param outputStream 출력 스트림
     * @return 총 데이터 크기 (bytes)
     */
    public int extractMultipleFramesPreservingTransferSyntaxToStream(
            String storagePath,
            java.util.List<Integer> frameNumbers,
            String boundary,
            boolean preserveCompression,
            java.io.OutputStream outputStream) {

        log.info("Extracting multiple frames with compression preservation: path={}, frames={}, preserve={}",
                storagePath, frameNumbers, preserveCompression);

        try (InputStream dicomStream = dicomStorageService.downloadDicomFile(storagePath)) {
            byte[] dicomBytes = dicomStream.readAllBytes();

            try (ByteArrayInputStream bais = new ByteArrayInputStream(dicomBytes);
                 DicomInputStream dis = new DicomInputStream(bais)) {

                // 1. Transfer Syntax 확인
                Attributes fmi = dis.readFileMetaInformation();
                String transferSyntaxUid = fmi != null
                        ? fmi.getString(Tag.TransferSyntaxUID, UID.ExplicitVRLittleEndian)
                        : UID.ExplicitVRLittleEndian;

                boolean isCompressed = isCompressedTransferSyntax(transferSyntaxUid);

                // 2. Dataset 읽기
                Attributes attrs = dis.readDataset();
                int totalFrames = attrs.getInt(Tag.NumberOfFrames, 1);

                int totalSize = 0;
                int successCount = 0;
                int failCount = 0;

                // 기본 출력 설정 (에러 마커용)
                String defaultMimeType = preserveCompression && isCompressed
                        ? getMimeTypeForTransferSyntax(transferSyntaxUid)
                        : "application/octet-stream";
                String defaultTransferSyntax = preserveCompression && isCompressed
                        ? transferSyntaxUid
                        : UID.ExplicitVRLittleEndian;

                // 3. 각 프레임 처리 (개별 예외 처리)
                for (int frameNumber : frameNumbers) {
                    int frameIndex = frameNumber - 1;

                    try {
                        if (frameIndex < 0 || frameIndex >= totalFrames) {
                            throw new BusinessException(MiniPacsErrorCode.INVALID_FRAME_NUMBER,
                                    String.format("Frame %d out of range (1-%d)", frameNumber, totalFrames));
                        }

                        byte[] frameData;
                        String outputTransferSyntax;
                        String mimeType;

                        if (preserveCompression && isCompressed) {
                            // 압축 유지
                            frameData = extractCompressedFrameData(attrs, frameIndex);
                            outputTransferSyntax = transferSyntaxUid;
                            mimeType = getMimeTypeForTransferSyntax(transferSyntaxUid);
                        } else {
                            // 비압축으로 반환 (기존 로직 재사용 불가 - attrs에서 직접 추출)
                            if (isCompressed) {
                                // 압축된 데이터를 디코딩해야 함 - DCM4CHE ImageIO 사용
                                frameData = extractDecompressedFrameFromBytes(dicomBytes, frameNumber);
                            } else {
                                frameData = extractUncompressedFrameData(attrs, frameIndex);
                            }
                            outputTransferSyntax = UID.ExplicitVRLittleEndian;
                            mimeType = "application/octet-stream";
                        }

                        // Multipart 파트 헤더 작성
                        String partHeader = "--" + boundary + "\r\n"
                                + "Content-Type: " + mimeType + "; transfer-syntax=" + outputTransferSyntax + "\r\n"
                                + "Content-Location: /frames/" + frameNumber + "\r\n"
                                + "Content-Length: " + frameData.length + "\r\n"
                                + "\r\n";

                        outputStream.write(partHeader.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                        outputStream.write(frameData);
                        outputStream.write("\r\n".getBytes(java.nio.charset.StandardCharsets.UTF_8));

                        totalSize += frameData.length;
                        successCount++;
                        log.debug("Extracted frame {}/{}: {} bytes, compressed={}",
                                frameNumber, totalFrames, frameData.length, preserveCompression && isCompressed);

                    } catch (Exception e) {
                        // 개별 프레임 실패 시 에러 마커 프레임 반환 (빈 데이터)
                        log.error("Failed to extract frame {}/{}: error={}",
                                frameNumber, totalFrames, e.getMessage());

                        // 에러 마커 파트 작성 (Content-Length: 0)
                        String errorPartHeader = "--" + boundary + "\r\n"
                                + "Content-Type: " + defaultMimeType + "; transfer-syntax=" + defaultTransferSyntax + "\r\n"
                                + "Content-Location: /frames/" + frameNumber + "\r\n"
                                + "Content-Length: 0\r\n"
                                + "\r\n";
                        outputStream.write(errorPartHeader.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                        outputStream.write("\r\n".getBytes(java.nio.charset.StandardCharsets.UTF_8));
                        failCount++;
                    }
                }

                // 종료 boundary (항상 작성)
                String endBoundary = "--" + boundary + "--\r\n";
                outputStream.write(endBoundary.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                outputStream.flush();

                log.info("Multi-frame extraction complete: frames={}, success={}, failed={}, totalBytes={}, preserveCompression={}",
                        frameNumbers.size(), successCount, failCount, totalSize, preserveCompression && isCompressed);

                return totalSize;
            }

        } catch (BusinessException e) {
            throw e;
        } catch (IOException e) {
            log.error("Failed to extract multiple frames: path={}, frames={}", storagePath, frameNumbers, e);
            throw new BusinessException(MiniPacsErrorCode.DICOM_RENDER_FAILED, e.getMessage());
        }
    }

    // ==================== JPEG Baseline Transcoding ====================

    /**
     * 원본 DICOM을 JPEG Baseline으로 변환하여 PixelData 반환
     *
     * <p>원본 Transfer Syntax와 관계없이 JPEG Baseline (8-bit)로 변환합니다.
     * DICOM 메타데이터의 Window/Level을 적용하여 적절한 밝기/대비로 변환합니다.
     *
     * <p>변환 과정:
     * <ol>
     *   <li>원본 DICOM에서 raw pixel data 추출</li>
     *   <li>Window/Level 적용 (메타데이터 우선, 없으면 Auto W/L)</li>
     *   <li>16-bit → 8-bit 변환</li>
     *   <li>JPEG Baseline으로 인코딩</li>
     * </ol>
     *
     * @param storagePath S3 저장 경로
     * @param frameNumber 프레임 번호 (1-based)
     * @param quality JPEG 품질 (0.0-1.0, 권장: 0.9)
     * @return JPEG Baseline 압축된 PixelData 바이트 배열
     * @throws BusinessException DICOM_RENDER_FAILED - 변환 실패 시
     */
    public byte[] transcodeToJpegBaseline(String storagePath, int frameNumber, float quality) {
        log.debug("Transcoding DICOM to JPEG Baseline: path={}, frame={}, quality={}",
                storagePath, frameNumber, quality);

        try (InputStream dicomStream = dicomStorageService.downloadDicomFile(storagePath)) {
            byte[] dicomBytes = dicomStream.readAllBytes();

            // Transfer Syntax 확인
            String transferSyntax = getTransferSyntaxFromBytes(dicomBytes);
            log.debug("Source Transfer Syntax: {}", transferSyntax);

            // 원본이 이미 JPEG Baseline이면 raw fragment 직접 반환 (decode→re-encode 건너뛰기)
            // DCM4CHE의 JPEG Baseline 디코딩 시 Color Model 문제 회피 (GitHub Issue #346)
            if (UID.JPEGBaseline8Bit.equals(transferSyntax)) {
                log.info("Source is already JPEG Baseline, extracting raw fragment directly: path={}, frame={}",
                        storagePath, frameNumber);
                return extractJpegFragmentDirectly(dicomBytes, frameNumber);
            }

            // 1. DICOM 메타데이터 확인 (Window/Level 포함) - JPEG Baseline 외 Transfer Syntax
            int bitsAllocated;
            int bitsStored;
            int samplesPerPixel;
            double windowCenter = 0;
            double windowWidth = 0;
            try (ByteArrayInputStream metaBais = new ByteArrayInputStream(dicomBytes);
                 DicomInputStream metaDis = new DicomInputStream(metaBais)) {
                metaDis.readFileMetaInformation();
                Attributes attrs = metaDis.readDataset();
                bitsAllocated = attrs.getInt(Tag.BitsAllocated, 16);
                bitsStored = attrs.getInt(Tag.BitsStored, 12);
                samplesPerPixel = attrs.getInt(Tag.SamplesPerPixel, 1);

                // Window/Level 메타데이터 읽기 (0028,1050 / 0028,1051)
                // 다중 값인 경우 첫 번째 값 사용
                double[] wcValues = attrs.getDoubles(Tag.WindowCenter);
                double[] wwValues = attrs.getDoubles(Tag.WindowWidth);
                if (wcValues != null && wcValues.length > 0) {
                    windowCenter = wcValues[0];
                }
                if (wwValues != null && wwValues.length > 0) {
                    windowWidth = wwValues[0];
                }

                log.debug("DICOM pixel params: bitsAllocated={}, bitsStored={}, samplesPerPixel={}, windowCenter={}, windowWidth={}",
                        bitsAllocated, bitsStored, samplesPerPixel, windowCenter, windowWidth);
            }

            // 2. DCM4CHE ImageIO로 raw pixel 추출 (W/L 미적용)
            Iterator<ImageReader> readers = ImageIO.getImageReadersByFormatName("DICOM");
            if (!readers.hasNext()) {
                throw new BusinessException(MiniPacsErrorCode.DICOM_RENDER_FAILED,
                        "DICOM ImageReader not found. DCM4CHE ImageIO is not installed.");
            }

            ImageReader reader = readers.next();

            try (ByteArrayInputStream bais = new ByteArrayInputStream(dicomBytes);
                 ImageInputStream iis = ImageIO.createImageInputStream(bais)) {

                reader.setInput(iis);

                int frameIndex = frameNumber - 1;
                int numFrames = reader.getNumImages(true);

                if (frameIndex < 0 || frameIndex >= numFrames) {
                    throw new BusinessException(MiniPacsErrorCode.INVALID_FRAME_NUMBER,
                            String.format("Frame %d out of range (1-%d)", frameNumber, numFrames));
                }

                // readRaster()로 W/L 미적용 raw pixels 추출
                java.awt.image.Raster raster = reader.readRaster(frameIndex, null);
                int width = raster.getWidth();
                int height = raster.getHeight();

                // 3. 8-bit BufferedImage 생성
                BufferedImage jpegImage;
                if (samplesPerPixel == 1) {
                    // Grayscale 처리
                    if (bitsStored <= 8) {
                        // 8-bit 이미지: W/L 미적용 (이미 0-255 범위로 정규화됨)
                        // 원본이 JPEG Baseline인 경우 이미 시각화를 위해 W/L이 적용된 상태
                        log.debug("8-bit grayscale: skipping W/L (bitsStored={})", bitsStored);
                        jpegImage = createGrayscale8BitImageNoWL(raster);
                    } else {
                        // 16-bit 이상: Window/Level 적용하여 8-bit 변환
                        log.debug("High-bit grayscale: applying W/L (bitsStored={}, wc={}, ww={})",
                                bitsStored, windowCenter, windowWidth);
                        jpegImage = createGrayscale8BitImageWithWL(raster, bitsStored, windowCenter, windowWidth);
                    }
                } else {
                    // RGB: 이미 8-bit인 경우 그대로 사용
                    jpegImage = createRgb8BitImage(raster);
                }

                // 4. JPEG Baseline으로 인코딩
                ByteArrayOutputStream jpegOutput = new ByteArrayOutputStream();
                writeJpegWithQuality(jpegImage, (int) (quality * 100), jpegOutput);

                byte[] jpegBytes = jpegOutput.toByteArray();

                log.info("Transcoded to JPEG Baseline: frame={}, {}x{}, {} bytes (quality={}, wc={}, ww={})",
                        frameNumber, width, height, jpegBytes.length, quality, windowCenter, windowWidth);

                return jpegBytes;

            } finally {
                reader.dispose();
            }

        } catch (BusinessException e) {
            throw e;
        } catch (IOException e) {
            log.error("JPEG Baseline transcoding failed: path={}, frame={}", storagePath, frameNumber, e);
            throw new BusinessException(MiniPacsErrorCode.DICOM_RENDER_FAILED, e.getMessage());
        }
    }

    /**
     * Raster에서 8-bit Grayscale BufferedImage 생성 (W/L 적용)
     *
     * <p>Window/Level 변환을 적용하여 의료 영상이 정상적으로 표시되도록 합니다.
     * W/L 값이 0이면 픽셀 min/max 기반으로 Auto W/L을 계산합니다.
     *
     * @param raster 원본 Raster (16-bit 또는 8-bit)
     * @param bitsStored 실제 사용 비트 수 (예: 12-bit)
     * @param windowCenter Window Center (0028,1050) - 0이면 Auto 계산
     * @param windowWidth Window Width (0028,1051) - 0이면 Auto 계산
     * @return 8-bit Grayscale BufferedImage (W/L 적용됨)
     */
    private BufferedImage createGrayscale8BitImageWithWL(
            java.awt.image.Raster raster,
            int bitsStored,
            double windowCenter,
            double windowWidth) {

        int width = raster.getWidth();
        int height = raster.getHeight();

        // W/L이 0이면 Auto W/L 계산 (픽셀 min/max 기반)
        if (windowCenter == 0 && windowWidth == 0) {
            int minPixel = Integer.MAX_VALUE;
            int maxPixel = Integer.MIN_VALUE;

            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    int pixelValue = raster.getSample(x, y, 0);
                    if (pixelValue < minPixel) minPixel = pixelValue;
                    if (pixelValue > maxPixel) maxPixel = pixelValue;
                }
            }

            // Auto W/L 계산
            windowWidth = maxPixel - minPixel;
            windowCenter = (maxPixel + minPixel) / 2.0;

            // width가 0이면 최소값 설정 (divide by zero 방지)
            if (windowWidth < 1) {
                windowWidth = 1;
            }

            log.debug("Auto W/L calculated: center={}, width={}, minPixel={}, maxPixel={}",
                    windowCenter, windowWidth, minPixel, maxPixel);
        }

        // 8-bit Grayscale BufferedImage 생성
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_BYTE_GRAY);

        // W/L 범위 계산
        double windowLow = windowCenter - (windowWidth / 2.0);
        double windowHigh = windowCenter + (windowWidth / 2.0);

        // 각 픽셀 변환 (W/L 적용)
        // 공식: output = ((input - windowLow) / windowWidth) * 255
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int originalValue = raster.getSample(x, y, 0);

                // W/L 변환
                double normalized;
                if (originalValue <= windowLow) {
                    normalized = 0;
                } else if (originalValue >= windowHigh) {
                    normalized = 255;
                } else {
                    normalized = ((originalValue - windowLow) / windowWidth) * 255.0;
                }

                // 클램핑 (안전장치)
                int scaledValue = (int) Math.round(normalized);
                scaledValue = Math.max(0, Math.min(255, scaledValue));
                image.getRaster().setSample(x, y, 0, scaledValue);
            }
        }

        log.debug("Created 8-bit grayscale image with W/L: {}x{}, bitsStored={}, WC={}, WW={}",
                width, height, bitsStored, windowCenter, windowWidth);

        return image;
    }

    /**
     * Raster에서 8-bit Grayscale BufferedImage 생성 (W/L 미적용)
     *
     * <p>이미 8-bit인 원본 데이터를 그대로 복사합니다.
     * JPEG Baseline Transfer Syntax 등 이미 시각화를 위해 정규화된 데이터에 사용합니다.
     *
     * @param raster 원본 Raster (이미 8-bit)
     * @return 8-bit Grayscale BufferedImage
     */
    private BufferedImage createGrayscale8BitImageNoWL(java.awt.image.Raster raster) {
        int width = raster.getWidth();
        int height = raster.getHeight();

        // 8-bit Grayscale BufferedImage 생성
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_BYTE_GRAY);

        // 픽셀값 직접 복사 (W/L 미적용)
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int value = raster.getSample(x, y, 0) & 0xFF;  // 8-bit로 마스킹
                image.getRaster().setSample(x, y, 0, value);
            }
        }

        log.debug("Created 8-bit grayscale image (no W/L): {}x{}", width, height);

        return image;
    }

    /**
     * Raster에서 8-bit RGB BufferedImage 생성
     *
     * @param raster 원본 Raster (RGB)
     * @return 8-bit RGB BufferedImage
     */
    private BufferedImage createRgb8BitImage(java.awt.image.Raster raster) {
        int width = raster.getWidth();
        int height = raster.getHeight();

        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int r = raster.getSample(x, y, 0) & 0xFF;
                int g = raster.getSample(x, y, 1) & 0xFF;
                int b = raster.getSample(x, y, 2) & 0xFF;
                int rgb = (r << 16) | (g << 8) | b;
                image.setRGB(x, y, rgb);
            }
        }

        log.debug("Created 8-bit RGB image: {}x{}", width, height);

        return image;
    }

    /**
     * DICOM 바이트 배열에서 Transfer Syntax UID 추출
     *
     * @param dicomBytes DICOM 파일 바이트 배열
     * @return Transfer Syntax UID (없으면 null)
     */
    private String getTransferSyntaxFromBytes(byte[] dicomBytes) {
        try (ByteArrayInputStream bais = new ByteArrayInputStream(dicomBytes);
             DicomInputStream dis = new DicomInputStream(bais)) {
            Attributes fmi = dis.readFileMetaInformation();
            return fmi != null ? fmi.getString(Tag.TransferSyntaxUID) : null;
        } catch (IOException e) {
            log.warn("Failed to read transfer syntax from bytes", e);
            return null;
        }
    }

    /**
     * JPEG Baseline DICOM에서 raw JPEG fragment 직접 추출
     *
     * <p>디코딩/재인코딩 없이 원본 JPEG 바이트를 그대로 반환합니다.
     * DICOM 표준에 따르면 각 Fragment는 완전한 JPEG 파일 (SOI ~ EOI)입니다.
     *
     * <p>이 메서드는 DCM4CHE의 JPEG Baseline 디코딩 시 발생하는 Color Model 문제를 회피합니다.
     * (참조: https://github.com/dcm4che/dcm4che/issues/346)
     *
     * @param dicomBytes DICOM 파일 바이트 배열
     * @param frameNumber 프레임 번호 (1-based)
     * @return raw JPEG 바이트 배열
     * @throws BusinessException 추출 실패 시
     */
    private byte[] extractJpegFragmentDirectly(byte[] dicomBytes, int frameNumber) {
        try (ByteArrayInputStream bais = new ByteArrayInputStream(dicomBytes);
             DicomInputStream dis = new DicomInputStream(bais)) {
            dis.readFileMetaInformation();
            Attributes attrs = dis.readDataset();

            int frameIndex = frameNumber - 1;
            byte[] jpegBytes = extractCompressedFrameData(attrs, frameIndex);

            log.info("Extracted raw JPEG fragment directly: frame={}, size={} bytes",
                    frameNumber, jpegBytes.length);

            return jpegBytes;
        } catch (IOException e) {
            throw new BusinessException(MiniPacsErrorCode.DICOM_RENDER_FAILED,
                    "Failed to extract JPEG fragment: " + e.getMessage());
        }
    }

    /**
     * 압축된 DICOM에서 디코딩된 프레임 추출 (내부 헬퍼)
     */
    private byte[] extractDecompressedFrameFromBytes(byte[] dicomBytes, int frameNumber) throws IOException {
        Iterator<ImageReader> readers = ImageIO.getImageReadersByFormatName("DICOM");
        if (!readers.hasNext()) {
            throw new BusinessException(MiniPacsErrorCode.DICOM_RENDER_FAILED,
                    "DICOM ImageReader not found. DCM4CHE ImageIO is not installed.");
        }

        ImageReader reader = readers.next();

        try (ByteArrayInputStream bais = new ByteArrayInputStream(dicomBytes);
             ImageInputStream iis = ImageIO.createImageInputStream(bais)) {

            reader.setInput(iis);
            int frameIndex = frameNumber - 1;

            // readRaster로 raw pixels 추출
            java.awt.image.Raster raster = reader.readRaster(frameIndex, null);
            return extractRawBytesFromRaster(raster, dicomBytes);

        } finally {
            reader.dispose();
        }
    }
}
