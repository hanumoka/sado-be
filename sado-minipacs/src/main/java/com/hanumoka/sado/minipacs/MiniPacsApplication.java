package com.hanumoka.sado.minipacs;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * MiniPACS Application
 *
 * DICOM 영상 관리 시스템 (Standalone PACS)
 *
 * [Entity 스캔 범위]
 * - 기본: @SpringBootApplication 패키지 및 하위 패키지 자동 스캔
 * - com.hanumoka.sado.minipacs.domain.entity.* (모든 @Entity 클래스)
 * - @MappedSuperclass (BaseEntity, TenantAwareEntity)는 자동 인식됨
 *
 * [컴포넌트 스캔 범위]
 * - com.hanumoka.sado.common.* (@Service, @Repository, @Component 등)
 * - com.hanumoka.sado.minipacs.* (@Controller, @Service, @Repository 등)
 */
@SpringBootApplication(scanBasePackages = {
    "com.hanumoka.sado.common",     // Common 모듈
    "com.hanumoka.sado.minipacs"    // MiniPACS 모듈
})
@EnableJpaAuditing  // JPA Auditing 활성화 (@CreatedDate, @LastModifiedDate)
@EnableScheduling   // Scheduled 작업 활성화 (@Scheduled 어노테이션 사용)
@EnableRetry        // Spring Retry 활성화 (@Retryable 어노테이션 사용)
public class MiniPacsApplication {

    /**
     * OpenCV 네이티브 라이브러리 준비
     *
     * <p>DCM4CHE ImageIO OpenCV가 StreamSegment 클래스의 정적 초기화 블록에서
     * System.loadLibrary("opencv_java")를 호출할 때 성공하도록 DLL을 현재 작업 디렉토리로 복사합니다.
     *
     * <p>핵심 원리:
     * <ul>
     *   <li>System.load()와 System.loadLibrary()는 별개의 메커니즘 - 서로 인식 안함!</li>
     *   <li>java.library.path는 런타임에 수정해도 효과 없음 (JVM 시작 시점 고정)</li>
     *   <li>해결책: DLL을 현재 작업 디렉토리로 복사 (java.library.path에 "."가 포함됨)</li>
     * </ul>
     *
     * <p>동작 흐름:
     * <ol>
     *   <li>main() → prepareOpenCVNative() → DLL을 현재 디렉토리로 복사</li>
     *   <li>SpringApplication.run() 시작</li>
     *   <li>HTTP 요청 → DicomRenderingService → StreamSegment 클래스 로드</li>
     *   <li>StreamSegment.&lt;clinit&gt;() → System.loadLibrary("opencv_java")</li>
     *   <li>java.library.path의 "." 디렉토리에서 opencv_java.dll 발견!</li>
     * </ol>
     *
     * @see <a href="https://www.pixelstech.net/article/1549365534-The-difference-between-System-load-and-System-loadLibrary-in-Java">System.load vs System.loadLibrary</a>
     * @see <a href="https://rollbar.com/blog/java-unsatisfiedlinkerror-runtime-error/">UnsatisfiedLinkError Handling</a>
     */
    private static void prepareOpenCVNative() {
        String osName = System.getProperty("os.name").toLowerCase();
        String targetLibName;
        String sourcePattern;

        if (osName.contains("win")) {
            targetLibName = "opencv_java.dll";
            sourcePattern = "opencv_java";
        } else if (osName.contains("linux")) {
            targetLibName = "libopencv_java.so";
            sourcePattern = "libopencv_java";
        } else if (osName.contains("mac")) {
            targetLibName = "libopencv_java.dylib";
            sourcePattern = "libopencv_java";
        } else {
            System.err.println("[OpenCV] Unsupported OS: " + osName);
            return;
        }

        // 현재 작업 디렉토리에 DLL 배치 (java.library.path에 "."가 포함됨)
        Path targetPath = Path.of(targetLibName);

        // 이미 존재하면 스킵
        if (Files.exists(targetPath)) {
            System.out.println("[OpenCV] Native library already exists: " + targetPath.toAbsolutePath());
            return;
        }

        // 1. build/natives에서 복사 시도 (Gradle copyOpenCVNatives 실행 후)
        Path buildNatives = Path.of("build", "natives", targetLibName);
        if (Files.exists(buildNatives)) {
            copyNativeLibrary(buildNatives, targetPath);
            return;
        }

        // 2. Gradle 캐시에서 복사 시도 (IDE 실행 시 폴백)
        Path gradleCache = Path.of(System.getProperty("user.home"),
                ".gradle", "caches", "modules-2", "files-2.1",
                "org.weasis.thirdparty.org.opencv", "opencv_java", "4.9.0-dcm");

        if (Files.exists(gradleCache)) {
            try (var stream = Files.walk(gradleCache, 3)) {
                var sourceFile = stream
                        .filter(p -> p.getFileName().toString().contains(sourcePattern))
                        .filter(p -> {
                            String name = p.getFileName().toString();
                            return name.endsWith(".dll") || name.endsWith(".so") || name.endsWith(".dylib");
                        })
                        .findFirst();

                if (sourceFile.isPresent()) {
                    copyNativeLibrary(sourceFile.get(), targetPath);
                    return;
                }
            } catch (IOException e) {
                System.err.println("[OpenCV] Failed to search Gradle cache: " + e.getMessage());
            }
        }

        // 3. 찾지 못한 경우 경고
        System.err.println("[OpenCV] WARNING: Could not find OpenCV native library.");
        System.err.println("[OpenCV] JPEG/JPEG2000 compressed DICOM rendering will fail.");
        System.err.println("[OpenCV] Solutions:");
        System.err.println("[OpenCV]   1. Run: ./gradlew :sado-minipacs:copyOpenCVNatives");
        System.err.println("[OpenCV]   2. Then restart the application");
        System.err.println("[OpenCV] Or run via: ./gradlew :sado-minipacs:bootRun");
    }

    /**
     * 네이티브 라이브러리 파일 복사
     */
    private static void copyNativeLibrary(Path source, Path target) {
        try {
            Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
            System.out.println("[OpenCV] Copied native library:");
            System.out.println("[OpenCV]   Source: " + source.toAbsolutePath());
            System.out.println("[OpenCV]   Target: " + target.toAbsolutePath());
        } catch (IOException e) {
            System.err.println("[OpenCV] Failed to copy native library: " + e.getMessage());
        }
    }

    public static void main(String[] args) {
        // Spring 시작 전에 OpenCV 네이티브 라이브러리를 현재 디렉토리로 복사
        // DCM4CHE StreamSegment의 System.loadLibrary("opencv_java")가 성공하도록 함
        prepareOpenCVNative();

        SpringApplication.run(MiniPacsApplication.class, args);
    }
}
