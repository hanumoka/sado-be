package com.hanumoka.sado.minipacs.domain.service;

import com.hanumoka.sado.common.exception.BusinessException;
import com.hanumoka.sado.minipacs.code.MiniPacsErrorCode;
import com.hanumoka.sado.minipacs.storage.service.DicomStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.UID;
import org.dcm4che3.imageio.plugins.dcm.DicomImageReadParam;
import org.dcm4che3.io.DicomInputStream;
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
}
