package com.hanumoka.sado.minipacs.domain.repository;

import com.hanumoka.sado.minipacs.domain.entity.TieringPolicy;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * TieringPolicy Repository
 *
 * <p>Tenant당 1개의 정책만 존재합니다 (Singleton Pattern).
 */
@Repository
public interface TieringPolicyRepository extends JpaRepository<TieringPolicy, Long> {

    /**
     * 현재 Tenant의 Tiering 정책 조회
     *
     * <p>Hibernate Filter에 의해 자동으로 tenantId 필터링됨
     *
     * @return Tiering 정책 (없으면 empty)
     */
    @Query("SELECT p FROM TieringPolicy p")
    Optional<TieringPolicy> findCurrentPolicy();

    /**
     * 현재 Tenant의 Tiering 정책 존재 여부 확인
     *
     * @return 정책 존재 여부
     */
    @Query("SELECT CASE WHEN COUNT(p) > 0 THEN true ELSE false END FROM TieringPolicy p")
    boolean existsCurrentPolicy();
}
