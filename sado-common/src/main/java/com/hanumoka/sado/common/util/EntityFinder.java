package com.hanumoka.sado.common.util;

import com.hanumoka.sado.common.exception.ResourceNotFoundException;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.function.Supplier;

/**
 * Entity 조회 유틸리티
 *
 * <p>Repository에서 엔티티를 조회할 때 일관된 예외 처리를 제공합니다.
 *
 * <p>사용 예시:
 * <pre>
 * // 기존 코드
 * return patientRepository.findById(id)
 *         .orElseThrow(() -> new ResourceNotFoundException("Patient not found with id: " + id));
 *
 * // EntityFinder 사용
 * return EntityFinder.findById(patientRepository, id, "Patient");
 * </pre>
 *
 * <p>장점:
 * <ul>
 *   <li>일관된 에러 메시지 형식</li>
 *   <li>코드 중복 제거</li>
 *   <li>기존 서비스 구조 변경 없음 (상속 불필요)</li>
 * </ul>
 */
public final class EntityFinder {

    private EntityFinder() {
        // 유틸리티 클래스 - 인스턴스화 방지
    }

    /**
     * ID로 엔티티 조회 (존재하지 않으면 ResourceNotFoundException)
     *
     * @param repository JPA Repository
     * @param id 엔티티 ID
     * @param entityName 에러 메시지에 표시할 엔티티 이름
     * @param <T> 엔티티 타입
     * @param <ID> ID 타입
     * @return 조회된 엔티티
     * @throws ResourceNotFoundException 엔티티가 존재하지 않는 경우
     */
    public static <T, ID> T findById(JpaRepository<T, ID> repository, ID id, String entityName) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        entityName + " not found with id: " + id));
    }

    /**
     * Optional에서 엔티티 추출 (존재하지 않으면 ResourceNotFoundException)
     *
     * @param optional Optional 객체
     * @param entityName 에러 메시지에 표시할 엔티티 이름
     * @param identifier 에러 메시지에 표시할 식별자 (예: UID)
     * @param <T> 엔티티 타입
     * @return 조회된 엔티티
     * @throws ResourceNotFoundException 엔티티가 존재하지 않는 경우
     */
    public static <T> T getOrThrow(Optional<T> optional, String entityName, Object identifier) {
        return optional.orElseThrow(() -> new ResourceNotFoundException(
                entityName + " not found with identifier: " + identifier));
    }

    /**
     * Optional에서 엔티티 추출 (커스텀 예외 메시지)
     *
     * @param optional Optional 객체
     * @param exceptionSupplier 예외 생성 Supplier
     * @param <T> 엔티티 타입
     * @return 조회된 엔티티
     * @throws ResourceNotFoundException 엔티티가 존재하지 않는 경우
     */
    public static <T> T getOrThrow(Optional<T> optional, Supplier<ResourceNotFoundException> exceptionSupplier) {
        return optional.orElseThrow(exceptionSupplier);
    }
}
