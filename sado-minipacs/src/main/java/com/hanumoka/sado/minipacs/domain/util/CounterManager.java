package com.hanumoka.sado.minipacs.domain.util;

/**
 * 역정규화된 카운터 필드 관리 유틸리티
 *
 * Entity의 numberOfXXX 필드에 대한 안전한 증감 연산 제공
 *
 * <p>주요 기능:
 * <ul>
 *   <li>null-safe 카운터 증가: null이면 1로 초기화, 아니면 +1</li>
 *   <li>null-safe 카운터 감소: null이거나 0 이하면 유지, 아니면 -1</li>
 *   <li>컬렉션 크기 기반 카운터 초기화</li>
 * </ul>
 *
 * <p>사용 예시:
 * <pre>
 * {@code
 * // 증가
 * numberOfSeries = CounterManager.increment(numberOfSeries);
 *
 * // 감소
 * numberOfSeries = CounterManager.decrement(numberOfSeries);
 *
 * // 컬렉션에서 초기화
 * numberOfSeries = CounterManager.fromCollectionSize(series);
 * }
 * </pre>
 */
public class CounterManager {

    /**
     * 카운터 증가 (null-safe)
     *
     * @param counter 현재 카운터 값 (null 가능)
     * @return 증가된 값 (null이면 1, 아니면 현재값 + 1)
     */
    public static Integer increment(Integer counter) {
        return (counter == null) ? 1 : counter + 1;
    }

    /**
     * 카운터 감소 (null-safe, 음수 방지)
     *
     * <p>카운터가 null이거나 0 이하일 경우 변경하지 않음
     * (음수로 가는 것을 방지)
     *
     * @param counter 현재 카운터 값 (null 가능)
     * @return 감소된 값 (null이거나 0 이하면 그대로, 아니면 현재값 - 1)
     */
    public static Integer decrement(Integer counter) {
        if (counter == null || counter <= 0) {
            return counter;
        }
        return counter - 1;
    }

    /**
     * 카운터 초기화 (컬렉션 크기 기반)
     *
     * <p>컬렉션의 실제 크기로 카운터를 재설정합니다.
     * recalculate 메서드에서 사용하기 위한 헬퍼 메서드입니다.
     *
     * @param collection 컬렉션 (null 가능)
     * @param <T> 컬렉션 요소 타입
     * @return 컬렉션 크기 (null이면 0, 아니면 collection.size())
     */
    public static <T> Integer fromCollectionSize(java.util.Collection<T> collection) {
        return (collection == null) ? 0 : collection.size();
    }
}
