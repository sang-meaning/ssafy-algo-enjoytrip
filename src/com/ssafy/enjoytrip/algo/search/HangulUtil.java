package com.ssafy.enjoytrip.algo.search;

/**
 * 한글 처리 유틸. 초성 검색을 위해 완성형 글자에서 초성을 뽑는다.
 *
 * 유니코드 완성형 한글은 0xAC00(가) 부터 0xD7A3(힣) 까지
 *   (초성 * 21 + 중성) * 28 + 종성
 * 규칙으로 배치되어 있다. 그래서 (글자 - 0xAC00) / (21 * 28) 이 초성 번호다.
 */
public final class HangulUtil {

    private static final char HANGUL_BEGIN = 0xAC00;
    private static final char HANGUL_END = 0xD7A3;
    private static final int JUNG_JONG = 21 * 28;

    /** 키보드로 입력되는 호환용 자모 (ㄱ = U+3131) 기준 초성 19개 */
    private static final char[] CHOSEONG = {
        'ㄱ', 'ㄲ', 'ㄴ', 'ㄷ', 'ㄸ', 'ㄹ', 'ㅁ', 'ㅂ', 'ㅃ', 'ㅅ',
        'ㅆ', 'ㅇ', 'ㅈ', 'ㅉ', 'ㅊ', 'ㅋ', 'ㅌ', 'ㅍ', 'ㅎ'
    };

    private HangulUtil() {
    }

    public static boolean isSyllable(char c) {
        return c >= HANGUL_BEGIN && c <= HANGUL_END;
    }

    /** 초성으로 쓰일 수 있는 자음인지 (ㄱ ~ ㅎ) */
    public static boolean isChoseong(char c) {
        for (char ch : CHOSEONG) {
            if (ch == c) {
                return true;
            }
        }
        return false;
    }

    /** "경복궁" -> "ㄱㅂㄱ". 한글이 아닌 글자는 그대로 둔다. */
    public static String toChoseong(String s) {
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (isSyllable(c)) {
                sb.append(CHOSEONG[(c - HANGUL_BEGIN) / JUNG_JONG]);
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    /** 문자열 전체가 초성 자음으로만 되어 있는지. 초성 검색으로 처리할지 판단할 때 쓴다. */
    public static boolean isAllChoseong(String s) {
        if (s.isEmpty()) {
            return false;
        }
        for (int i = 0; i < s.length(); i++) {
            if (!isChoseong(s.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    /**
     * 검색용 정규화. 소문자로 바꾸고 글자, 숫자만 남긴다.
     * "창덕궁과 후원 [유네스코]" -> "창덕궁과후원유네스코"
     */
    public static String normalize(String s) {
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = Character.toLowerCase(s.charAt(i));
            if (Character.isLetterOrDigit(c)) {
                sb.append(c);
            }
        }
        return sb.toString();
    }
}
