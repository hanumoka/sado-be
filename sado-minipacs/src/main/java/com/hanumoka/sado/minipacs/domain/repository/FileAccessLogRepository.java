package com.hanumoka.sado.minipacs.domain.repository;

import com.hanumoka.sado.minipacs.domain.entity.FileAccessLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * FileAccessLog Repository
 *
 * <p>파일 접근 감사 로그 조회
 */
@Repository
public interface FileAccessLogRepository extends JpaRepository<FileAccessLog, Long> {

    /**
     * 특정 리소스의 접근 로그 조회
     *
     * @param resourceType 리소스 타입
     * @param resourceId   리소스 ID
     * @param pageable     페이징 정보
     * @return 접근 로그 페이지 (최신순)
     */
    Page<FileAccessLog> findByResourceTypeAndResourceIdOrderByAccessedAtDesc(
        FileAccessLog.ResourceType resourceType,
        Long resourceId,
        Pageable pageable
    );

    /**
     * 특정 사용자의 접근 로그 조회
     *
     * @param userId   사용자 ID
     * @param pageable 페이징 정보
     * @return 접근 로그 페이지 (최신순)
     */
    Page<FileAccessLog> findByUserIdOrderByAccessedAtDesc(
        String userId,
        Pageable pageable
    );

    /**
     * 특정 기간의 접근 로그 조회
     *
     * @param startTime 시작 시각
     * @param endTime   종료 시각
     * @return 접근 로그 목록
     */
    List<FileAccessLog> findByAccessedAtBetweenOrderByAccessedAtDesc(
        LocalDateTime startTime,
        LocalDateTime endTime
    );

    /**
     * 특정 IP 주소의 접근 로그 조회 (보안 모니터링용)
     *
     * @param ipAddress IP 주소
     * @param pageable  페이징 정보
     * @return 접근 로그 페이지
     */
    Page<FileAccessLog> findByIpAddressOrderByAccessedAtDesc(
        String ipAddress,
        Pageable pageable
    );

    /**
     * 실패한 접근 시도 조회 (보안 모니터링용)
     *
     * @param eventType 이벤트 타입 (DOWNLOAD_FAILED, ACCESS_DENIED)
     * @param since     조회 시작 시각
     * @param pageable  페이징 정보
     * @return 실패 로그 페이지
     */
    Page<FileAccessLog> findByEventTypeAndAccessedAtAfterOrderByAccessedAtDesc(
        FileAccessLog.EventType eventType,
        LocalDateTime since,
        Pageable pageable
    );

    /**
     * 특정 사용자의 특정 기간 다운로드 총량 계산
     *
     * @param userId    사용자 ID
     * @param startTime 시작 시각
     * @param endTime   종료 시각
     * @return 총 다운로드 바이트 수
     */
    @Query("SELECT COALESCE(SUM(f.bytesTransferred), 0) FROM FileAccessLog f " +
           "WHERE f.userId = :userId " +
           "AND f.eventType = 'DOWNLOAD_SUCCESS' " +
           "AND f.accessedAt BETWEEN :startTime AND :endTime")
    Long sumBytesTransferredByUser(
        @Param("userId") String userId,
        @Param("startTime") LocalDateTime startTime,
        @Param("endTime") LocalDateTime endTime
    );

    /**
     * 오래된 로그 삭제 (데이터 보관 정책)
     *
     * @param cutoffDate 삭제 기준 날짜 (이 날짜 이전 로그 삭제)
     * @return 삭제된 레코드 수
     */
    int deleteByAccessedAtBefore(LocalDateTime cutoffDate);

    /**
     * 리소스별 접근 횟수 집계 (인기 파일 분석)
     *
     * @param resourceType 리소스 타입
     * @param since        조회 시작 시각
     * @param limit        상위 N개
     * @return 접근 횟수가 많은 리소스 ID 목록
     */
    @Query("SELECT f.resourceId, COUNT(f) as accessCount " +
           "FROM FileAccessLog f " +
           "WHERE f.resourceType = :resourceType " +
           "AND f.eventType = 'DOWNLOAD_SUCCESS' " +
           "AND f.accessedAt >= :since " +
           "GROUP BY f.resourceId " +
           "ORDER BY accessCount DESC")
    List<Object[]> findTopAccessedResources(
        @Param("resourceType") FileAccessLog.ResourceType resourceType,
        @Param("since") LocalDateTime since,
        Pageable pageable
    );
}
