package com.ssafy.enjoytrip.algo;

import java.util.List;

import com.ssafy.enjoytrip.algo.search.AutocompleteTrie;
import com.ssafy.enjoytrip.algo.search.HangulUtil;
import com.ssafy.enjoytrip.support.Check;

public class SearchTest {

    public static void main(String[] args) {
        hangul();
        trie();
        Check.done("SearchTest");
    }

    static void hangul() {
        Check.equals("ㄱㅂㄱ", HangulUtil.toChoseong("경복궁"), "초성 추출");
        Check.equals("ㄴㅅㅌㅇㄹ n", HangulUtil.toChoseong("남산타워로 n"), "한글 외 문자는 유지");
        Check.isTrue(HangulUtil.isAllChoseong("ㄱㅂㄱ"), "초성만 입력");
        Check.isTrue(!HangulUtil.isAllChoseong("경ㅂ"), "완성형 섞이면 초성 검색 아님");
        Check.equals("창덕궁과후원유네스코", HangulUtil.normalize("창덕궁과 후원 [유네스코]"), "정규화");
        Check.equals("n서울타워", HangulUtil.normalize("N 서울타워"), "영문 소문자화");
    }

    static void trie() {
        // 순위가 높은 것부터 넣는다
        String[] titles = {
            "경복궁",                         // 0
            "창덕궁과 후원 [유네스코 세계유산]", // 1
            "경복궁 별빛야행",                  // 2
            "경주 불국사",                      // 3
            "경복궁 생과방",                    // 4
            "N서울타워",                        // 5
        };
        AutocompleteTrie trie = new AutocompleteTrie(3);
        for (int i = 0; i < titles.length; i++) {
            trie.add(i, titles[i]);
        }

        Check.equals(List.of(0, 2, 4), trie.search("경복", 10), "접두어 검색, 순위 순");
        Check.equals(List.of(0, 2, 4), trie.search("경복", 10), "같은 검색 반복");
        Check.equals(List.of(0, 2), trie.search("경복", 2), "limit 적용");
        Check.equals(List.of(0, 2, 3), trie.search("경", 10), "top-K 는 3개까지만 보관");
        Check.equals(List.of(1), trie.search("후원", 10), "두 번째 단어로 검색");
        Check.equals(List.of(1), trie.search("세계", 10), "괄호 안 단어로 검색");
        Check.equals(List.of(0, 2, 4), trie.search("ㄱㅂㄱ", 10), "초성 검색");
        Check.equals(List.of(1), trie.search("ㅎㅇ", 10), "초성 단어 검색");
        Check.equals(List.of(5), trie.search("n서울", 10), "영문 대소문자 무시");
        Check.equals(List.of(2), trie.search("경복궁 별빛", 10), "공백 포함 검색");
        Check.equals(List.of(), trie.search("없는장소", 10), "결과 없음");
        Check.equals(List.of(), trie.search("  ", 10), "빈 검색어");
    }
}
