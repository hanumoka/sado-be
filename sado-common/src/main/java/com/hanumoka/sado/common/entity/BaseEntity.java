package com.hanumoka.sado.common.entity;

import com.hanumoka.sado.common.util.UuidV7Generator;
import jakarta.persistence.*;
import lombok.Getter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 모든 Entity의 공통 필드를 제공하는 Base Entity
 *
 * [공통 필드]
 * - id: Primary Key (자동 증가)
 * - uuid: UUID v7 (외부 노출용 식별자)
 * - createdAt: 생성 시각 (자동 설정)
 * - updatedAt: 수정 시각 (자동 업데이트)
 *
 * [UUID v7 사용 이유]
 * - 외부 API에서 내부 PK(id) 대신 UUID 사용 (보안)
 * - 시간순 정렬 가능 (DB 인덱스 친화적)
 * - 분산 시스템에서 고유성 보장
 */

@Getter
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * UUID v7 (외부 노출용 고유 식별자)
     *
     * <p>특징:
     * <ul>
     *   <li>시간 기반 UUID - 생성 순서대로 정렬됨</li>
     *   <li>한번 생성되면 변경 불가 (updatable = false)</li>
     *   <li>Unique constraint로 고유성 보장</li>
     * </ul>
     */
    @Column(name = "uuid", nullable = false, updatable = false, unique = true, length = 36)
    private String uuid;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /**
     * 엔티티 저장 전 UUID v7 자동 생성
     */
    @PrePersist
    protected void prePersist() {
        if (this.uuid == null) {
            this.uuid = UuidV7Generator.generateString();
        }
    }
}
