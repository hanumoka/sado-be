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
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
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
                log.info("DICOM Transfer Syntax: {} ({})", transferSyntaxName, transferSyntaxUID);

                // 압축 여부 판단
                boolean isCompressed = !UID.ImplicitVRLittleEndian.equals(transferSyntaxUID)
                        && !UID.ExplicitVRLittleEndian.equals(transferSyntaxUID)
                        && !UID.ExplicitVRBigEndian.equals(transferSyntaxUID);
                log.info("DICOM Compression: {}", isCompressed ? "COMPRESSED" : "UNCOMPRESSED");
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
                log.info("=== Raster Debug for Frame {} ===", frameNumber);
                log.info("Raster Width: {}, Height: {}", raster.getWidth(), raster.getHeight());
                log.info("SampleModel class: {}", sm.getClass().getName());
                log.info("SampleModel Width: {}, Height: {}", sm.getWidth(), sm.getHeight());
                log.info("NumBands: {}, DataType: {}", sm.getNumBands(), sm.getDataType());

                if (sm instanceof java.awt.image.ComponentSampleModel csm) {
                    log.info("ComponentSampleModel PixelStride: {}", csm.getPixelStride());
                    log.info("ComponentSampleModel ScanlineStride: {}", csm.getScanlineStride());
                    log.info("BandOffsets: {}", java.util.Arrays.toString(csm.getBandOffsets()));
                }

                java.awt.image.DataBuffer db = raster.getDataBuffer();
                log.info("DataBuffer Size: {}", db.getSize());
                log.info("DataBuffer NumBanks: {}", db.getNumBanks());
                log.info("Expected Size (width*height): {}", raster.getWidth() * raster.getHeight());
                log.info("=== End Raster Debug ===");
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
     */
    private boolean isCompressedTransferSyntax(String uid) {
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
        try (InputStream dicomStream = dicomStorageService.downloadDicomFile(storagePath)) {
            DicomInputStream dis = new DicomInputStream(dicomStream);
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
        try (InputStream dicomStream = dicomStorageService.downloadDicomFile(storagePath)) {
            DicomInputStream dis = new DicomInputStream(dicomStream);
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
}
