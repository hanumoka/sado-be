package com.hanumoka.sado.common.util;

import com.fasterxml.uuid.Generators;
import com.fasterxml.uuid.impl.TimeBasedEpochGenerator;

import java.util.UUID;

/**
 * UUID v7 생성 유틸리티
 *
 * <p>UUID v7 특징:
 * <ul>
 *   <li>시간 기반 UUID (Unix Epoch milliseconds)</li>
 *   <li>시간순 정렬 가능 (DB 인덱스 친화적)</li>
 *   <li>분산 시스템에서 고유성 보장</li>
 *   <li>UUID v4보다 DB 인덱스 성능 우수</li>
 * </ul>
 *
 * <p>UUID v7 구조 (128bit):
 * <pre>
 * 0                   1                   2                   3
 * 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |                         unix_ts_ms (48 bits)                  |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |  ver  |         rand_a (12 bits)        |var|   rand_b        |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |                         rand_b (62 bits)                      |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * </pre>
 */
public final class UuidV7Generator {

    /**
     * Thread-safe UUID v7 generator
     * TimeBasedEpochGenerator는 내부적으로 동기화되어 있음
     */
    private static final TimeBasedEpochGenerator GENERATOR = Generators.timeBasedEpochGenerator();

    private UuidV7Generator() {
        // 유틸리티 클래스 - 인스턴스화 방지
    }

    /**
     * UUID v7 생성
     *
     * @return 새로운 UUID v7
     */
    public static UUID generate() {
        return GENERATOR.generate();
    }

    /**
     * UUID v7 문자열 생성
     *
     * @return UUID v7 문자열 (36자리, 하이픈 포함)
     */
    public static String generateString() {
        return GENERATOR.generate().toString();
    }
}
