package com.ssafy.enjoytrip.algo.search;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 관광지 이름 자동완성용 트라이.
 *
 * 구조
 *  - 일반 트라이와 초성 트라이 두 개를 둔다. 입력이 "ㄱㅂㄱ" 처럼 초성만이면 초성 트라이에서 찾는다.
 *  - 제목 맨 앞뿐 아니라 각 단어 시작 위치부터도 넣는다.
 *    "창덕궁과 후원" 은 "창덕궁과후원", "후원" 두 키로 들어가서 "후원" 으로도 찾을 수 있다.
 *  - 각 노드는 그 접두어로 시작하는 항목 중 상위 K 개를 미리 들고 있다 (top-K 캐시).
 *
 * 상위 K 를 싸게 유지하는 방법
 *  호출하는 쪽이 항목을 "추천 순위가 높은 것부터" add 하도록 약속한다.
 *  그러면 각 노드에는 먼저 들어온 K 개가 곧 상위 K 개이므로, 비교 없이 꽉 차기 전까지만 붙이면 된다.
 *
 * 복잡도 (L = 키 길이, K = 노드당 후보 수, F = 자식 수)
 *  - 삽입 O(L log F)
 *  - 검색 O(Q log F + K), Q = 검색어 길이. 전체 데이터 수 N 과 무관하다.
 *  - 비교 대상인 DB LIKE '%검색어%' 나 전체 순회는 O(N * L)
 *
 * 자식 노드는 HashMap 대신 정렬된 char 배열 + 이진 탐색으로 관리해서 메모리를 줄였다.
 */
public class AutocompleteTrie {

    private static final class Node {
        char[] keys = EMPTY_KEYS;
        Node[] children = EMPTY_NODES;
        int childCount;
        int[] top;
        int topCount;

        Node child(char c) {
            int i = Arrays.binarySearch(keys, 0, childCount, c);
            return i >= 0 ? children[i] : null;
        }

        Node getOrCreate(char c, int topK) {
            int i = Arrays.binarySearch(keys, 0, childCount, c);
            if (i >= 0) {
                return children[i];
            }
            int pos = -(i + 1);
            if (childCount == keys.length) {
                int cap = Math.max(2, childCount * 2);
                keys = Arrays.copyOf(keys, cap);
                children = Arrays.copyOf(children, cap);
            }
            System.arraycopy(keys, pos, keys, pos + 1, childCount - pos);
            System.arraycopy(children, pos, children, pos + 1, childCount - pos);
            Node node = new Node();
            node.top = new int[topK];
            keys[pos] = c;
            children[pos] = node;
            childCount++;
            return node;
        }

        void offer(int id) {
            if (topCount == top.length) {
                return;
            }
            // 한 항목이 여러 키로 같은 노드를 지날 수 있다. 같은 항목은 연속으로 들어오므로 마지막만 보면 된다.
            if (topCount > 0 && top[topCount - 1] == id) {
                return;
            }
            top[topCount++] = id;
        }
    }

    private static final char[] EMPTY_KEYS = new char[0];
    private static final Node[] EMPTY_NODES = new Node[0];

    private final int topK;
    private final Node wordRoot;
    private final Node choRoot;
    private int size;

    public AutocompleteTrie(int topK) {
        this.topK = topK;
        this.wordRoot = new Node();
        this.wordRoot.top = new int[topK];
        this.choRoot = new Node();
        this.choRoot.top = new int[topK];
    }

    /**
     * 항목 하나를 넣는다. 반드시 추천 순위가 높은 것부터 넣어야 한다.
     *
     * @param id   호출하는 쪽에서 쓰는 번호 (리스트 인덱스 등)
     * @param text 관광지 이름
     */
    public void add(int id, String text) {
        for (String key : keysOf(text)) {
            insert(wordRoot, key, id);
            insert(choRoot, HangulUtil.toChoseong(key), id);
        }
        size++;
    }

    private void insert(Node root, String key, int id) {
        Node node = root;
        for (int i = 0; i < key.length(); i++) {
            node = node.getOrCreate(key.charAt(i), topK);
            node.offer(id);
        }
    }

    /**
     * 접두어로 시작하는 항목 번호를 순위 순서대로 최대 limit 개 돌려준다.
     * 초성만 입력하면 초성 검색, 그 외에는 일반 검색.
     */
    public List<Integer> search(String query, int limit) {
        String q = HangulUtil.normalize(query);
        if (q.isEmpty()) {
            return List.of();
        }
        Node node = HangulUtil.isAllChoseong(q) ? choRoot : wordRoot;
        for (int i = 0; i < q.length() && node != null; i++) {
            node = node.child(q.charAt(i));
        }
        if (node == null) {
            return List.of();
        }
        int n = Math.min(limit, node.topCount);
        List<Integer> result = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            result.add(node.top[i]);
        }
        return result;
    }

    /**
     * 제목에서 키 목록을 만든다. 전체 제목 + 두 번째 단어부터 시작하는 나머지 부분.
     * "창덕궁과 후원 [유네스코 세계유산]" -> 창덕궁과후원유네스코세계유산, 후원유네스코세계유산, 유네스코세계유산, 세계유산
     */
    static List<String> keysOf(String text) {
        List<String> keys = new ArrayList<>();
        String lower = text.toLowerCase();
        int i = 0;
        while (i < lower.length()) {
            // 단어 시작 위치 찾기
            while (i < lower.length() && !Character.isLetterOrDigit(lower.charAt(i))) {
                i++;
            }
            if (i >= lower.length()) {
                break;
            }
            String key = HangulUtil.normalize(lower.substring(i));
            if (!key.isEmpty() && !keys.contains(key)) {
                keys.add(key);
            }
            // 다음 단어로 이동
            while (i < lower.length() && Character.isLetterOrDigit(lower.charAt(i))) {
                i++;
            }
        }
        return keys;
    }

    public int size() {
        return size;
    }

    /** 메모리 사용량 확인용. 전체 노드 수를 센다. */
    public int countNodes() {
        return count(wordRoot) + count(choRoot);
    }

    private int count(Node node) {
        int c = 1;
        for (int i = 0; i < node.childCount; i++) {
            c += count(node.children[i]);
        }
        return c;
    }
}
