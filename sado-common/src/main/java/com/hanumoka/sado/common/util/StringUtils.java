package com.hanumoka.sado.common.util;

/**
 * 문자열 유틸리티 클래스
 *
 * <p>Null-safe 문자열 검증 메서드 제공
 *
 * <p>사용 예시:
 * <pre>
 * // 기존 코드
 * if (str != null && !str.isEmpty()) { ... }
 *
 * // StringUtils 사용
 * if (StringUtils.hasText(str)) { ... }
 *
 * // static import 사용
 * import static com.hanumoka.sado.common.util.StringUtils.hasText;
 * if (hasText(str)) { ... }
 * </pre>
 */
public final class StringUtils {

    private StringUtils() {
        // 유틸리티 클래스 - 인스턴스화 방지
    }

    /**
     * 문자열이 null이 아니고, 비어있지 않고, 공백만 있지 않은지 확인
     *
     * @param str 검사할 문자열
     * @return 유효한 텍스트가 있으면 true
     */
    public static boolean hasText(String str) {
        return str != null && !str.isEmpty() && !str.isBlank();
    }

    /**
     * 문자열이 null이거나 비어있거나 공백만 있는지 확인
     *
     * @param str 검사할 문자열
     * @return 텍스트가 없으면 true
     */
    public static boolean isBlank(String str) {
        return str == null || str.isEmpty() || str.isBlank();
    }

    /**
     * 여러 문자열 중 하나라도 유효한 텍스트가 있는지 확인
     *
     * @param strings 검사할 문자열들
     * @return 하나라도 유효한 텍스트가 있으면 true
     */
    public static boolean hasAnyText(String... strings) {
        if (strings == null) {
            return false;
        }
        for (String s : strings) {
            if (hasText(s)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 모든 문자열이 유효한 텍스트인지 확인
     *
     * @param strings 검사할 문자열들
     * @return 모두 유효한 텍스트이면 true
     */
    public static boolean hasAllText(String... strings) {
        if (strings == null || strings.length == 0) {
            return false;
        }
        for (String s : strings) {
            if (!hasText(s)) {
                return false;
            }
        }
        return true;
    }
}
