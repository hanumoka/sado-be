package com.hanumoka.sado.minipacs.domain.service;

import com.hanumoka.sado.minipacs.domain.entity.FileAccessLog;
import com.hanumoka.sado.minipacs.domain.repository.FileAccessLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 파일 접근 감사 로그 서비스
 *
 * <p>모든 파일 다운로드 이벤트를 감사 로그로 기록합니다.
 *
 * <p>주요 특징:
 * <ul>
 *   <li>REQUIRES_NEW Transaction: 감사 로그 실패가 다운로드를 롤백하지 않도록</li>
 *   <li>예외 처리: 로그 실패 시에도 다운로드는 계속 진행</li>
 *   <li>자동 정리: 3년 초과 로그 자동 삭제 (HIPAA 최소 요구사항)</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class FileAccessLogService {

    private final FileAccessLogRepository accessLogRepository;

    /**
     * 다운로드 시작 로그
     *
     * @param resourceType 리소스 타입
     * @param resourceId   리소스 ID
     * @param userId       사용자 ID
     * @param ipAddress    클라이언트 IP
     * @param userAgent    User-Agent 헤더
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logDownloadStart(
        FileAccessLog.ResourceType resourceType,
        Long resourceId,
        String userId,
        String ipAddress,
        String userAgent
    ) {
        try {
            FileAccessLog accessLog = FileAccessLog.builder()
                .eventType(FileAccessLog.EventType.DOWNLOAD_START)
                .resourceType(resourceType)
                .resourceId(resourceId)
                .userId(userId)
                .ipAddress(ipAddress)
                .userAgent(userAgent)
                .accessedAt(LocalDateTime.now())
                .build();

            accessLogRepository.save(accessLog);

            log.debug("Download started: resourceType={}, resourceId={}, userId={}, ip={}",
                     resourceType, resourceId, userId, ipAddress);
        } catch (Exception e) {
            // 감사 로그 실패가 다운로드를 막아서는 안 됨
            log.error("Failed to log download start: resourceType={}, resourceId={}",
                     resourceType, resourceId, e);
        }
    }

    /**
     * 다운로드 성공 로그
     *
     * @param resourceType     리소스 타입
     * @param resourceId       리소스 ID
     * @param userId           사용자 ID
     * @param ipAddress        클라이언트 IP
     * @param userAgent        User-Agent 헤더
     * @param bytesTransferred 전송된 바이트 수
     * @param durationMs       소요 시간 (밀리초)
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logDownloadSuccess(
        FileAccessLog.ResourceType resourceType,
        Long resourceId,
        String userId,
        String ipAddress,
        String userAgent,
        long bytesTransferred,
        long durationMs
    ) {
        try {
            FileAccessLog accessLog = FileAccessLog.builder()
                .eventType(FileAccessLog.EventType.DOWNLOAD_SUCCESS)
                .resourceType(resourceType)
                .resourceId(resourceId)
                .userId(userId)
                .ipAddress(ipAddress)
                .userAgent(userAgent)
                .bytesTransferred(bytesTransferred)
                .durationMs(durationMs)
                .accessedAt(LocalDateTime.now())
                .build();

            accessLogRepository.save(accessLog);

            log.info("Download completed: resourceType={}, resourceId={}, userId={}, bytes={}, duration={}ms",
                    resourceType, resourceId, userId, bytesTransferred, durationMs);
        } catch (Exception e) {
            log.error("Failed to log download success: resourceType={}, resourceId={}",
                     resourceType, resourceId, e);
        }
    }

    /**
     * 다운로드 실패 로그
     *
     * @param resourceType 리소스 타입
     * @param resourceId   리소스 ID
     * @param userId       사용자 ID
     * @param ipAddress    클라이언트 IP
     * @param userAgent    User-Agent 헤더
     * @param errorMessage 에러 메시지
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logDownloadFailure(
        FileAccessLog.ResourceType resourceType,
        Long resourceId,
        String userId,
        String ipAddress,
        String userAgent,
        String errorMessage
    ) {
        try {
            FileAccessLog accessLog = FileAccessLog.builder()
                .eventType(FileAccessLog.EventType.DOWNLOAD_FAILED)
                .resourceType(resourceType)
                .resourceId(resourceId)
                .userId(userId)
                .ipAddress(ipAddress)
                .userAgent(userAgent)
                .errorMessage(errorMessage)
                .accessedAt(LocalDateTime.now())
                .build();

            accessLogRepository.save(accessLog);

            log.warn("Download failed: resourceType={}, resourceId={}, userId={}, error={}",
                    resourceType, resourceId, userId, errorMessage);
        } catch (Exception e) {
            log.error("Failed to log download failure: resourceType={}, resourceId={}",
                     resourceType, resourceId, e);
        }
    }

    /**
     * 접근 거부 로그 (권한 없음)
     *
     * @param resourceType 리소스 타입
     * @param resourceId   리소스 ID
     * @param userId       사용자 ID
     * @param ipAddress    클라이언트 IP
     * @param userAgent    User-Agent 헤더
     * @param reason       거부 사유
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logAccessDenied(
        FileAccessLog.ResourceType resourceType,
        Long resourceId,
        String userId,
        String ipAddress,
        String userAgent,
        String reason
    ) {
        try {
            FileAccessLog accessLog = FileAccessLog.builder()
                .eventType(FileAccessLog.EventType.ACCESS_DENIED)
                .resourceType(resourceType)
                .resourceId(resourceId)
                .userId(userId)
                .ipAddress(ipAddress)
                .userAgent(userAgent)
                .errorMessage(reason)
                .accessedAt(LocalDateTime.now())
                .build();

            accessLogRepository.save(accessLog);

            log.warn("Access denied: resourceType={}, resourceId={}, userId={}, reason={}",
                    resourceType, resourceId, userId, reason);
        } catch (Exception e) {
            log.error("Failed to log access denied: resourceType={}, resourceId={}",
                     resourceType, resourceId, e);
        }
    }

    /**
     * 오래된 감사 로그 정리
     *
     * <p>HIPAA 최소 요구사항: 3년 보관
     * <p>매월 1일 02:00에 자동 실행
     */
    @Scheduled(cron = "0 0 2 1 * *") // 매월 1일 02:00
    @Transactional
    public void cleanupOldLogs() {
        log.info("Scheduled access log cleanup started");

        try {
            // 3년 초과 로그 삭제
            LocalDateTime cutoffDate = LocalDateTime.now().minusYears(3);
            int deletedCount = accessLogRepository.deleteByAccessedAtBefore(cutoffDate);

            log.info("Access log cleanup completed: deleted {} old logs (before {})",
                    deletedCount, cutoffDate);
        } catch (Exception e) {
            log.error("Failed to cleanup old access logs", e);
        }
    }

    /**
     * 특정 사용자의 다운로드 총량 조회 (대역폭 모니터링)
     *
     * @param userId    사용자 ID
     * @param startTime 시작 시각
     * @param endTime   종료 시각
     * @return 총 다운로드 바이트 수
     */
    public long getUserDownloadVolume(String userId, LocalDateTime startTime, LocalDateTime endTime) {
        return accessLogRepository.sumBytesTransferredByUser(userId, startTime, endTime);
    }
}
