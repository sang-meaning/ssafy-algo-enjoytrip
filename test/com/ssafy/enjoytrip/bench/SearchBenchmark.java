package com.ssafy.enjoytrip.bench;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import com.ssafy.enjoytrip.algo.geo.GeoUtil;
import com.ssafy.enjoytrip.algo.geo.GridIndex;
import com.ssafy.enjoytrip.algo.search.AutocompleteTrie;
import com.ssafy.enjoytrip.algo.search.HangulUtil;
import com.ssafy.enjoytrip.dao.AttractionDao;
import com.ssafy.enjoytrip.model.Attraction;

/**
 * 자동완성과 반경 검색 성능 비교. DB 에 관광지 데이터가 있어야 한다 (DataLoader 실행 후).
 *
 * 1) 자동완성: 트라이 top-K vs 전체 순회(이름이 검색어로 시작하는지 하나씩 확인)
 * 2) 반경 5km 검색: 격자 인덱스 vs 전체 순회(모든 지점과 거리 계산)
 */
public class SearchBenchmark {

    private static final String[] QUERIES = {
        "경", "경복", "경복궁", "해운대", "ㄱㅂㄱ", "ㅎㅇㄷ", "부산", "제주", "롯데", "국립",
    };

    public static void main(String[] args) throws Exception {
        List<Attraction> all = new AttractionDao().findAll();
        System.out.println("관광지 " + all.size() + "건");
        autocomplete(all);
        radius(all);
    }

    static void autocomplete(List<Attraction> all) {
        int n = all.size();
        String[] titles = new String[n];
        String[] norm = new String[n];
        String[] cho = new String[n];
        for (int i = 0; i < n; i++) {
            titles[i] = all.get(i).title();
            norm[i] = HangulUtil.normalize(titles[i]);
            cho[i] = HangulUtil.toChoseong(norm[i]);
        }

        System.gc();
        long memBefore = usedMemory();
        long s = System.nanoTime();
        AutocompleteTrie trie = new AutocompleteTrie(10);
        for (int i = 0; i < n; i++) {
            trie.add(i, titles[i]);
        }
        double buildMs = (System.nanoTime() - s) / 1e6;
        System.gc();
        long memAfter = usedMemory();

        System.out.println();
        System.out.printf("트라이 구축 %.0fms, 노드 %,d개, 메모리 약 %.1fMB%n",
                buildMs, trie.countNodes(), (memAfter - memBefore) / 1024.0 / 1024.0);
        System.out.println();
        System.out.println("| 검색어 | 트라이 (us) | 전체 순회 (us) | 배수 |");
        System.out.println("|---|---|---|---|");

        int repeat = 2000;
        // 워밍업
        for (int r = 0; r < 200; r++) {
            for (String q : QUERIES) {
                trie.search(q, 10);
                linearPrefix(q, norm, cho, 10);
            }
        }
        for (String q : QUERIES) {
            s = System.nanoTime();
            for (int r = 0; r < repeat; r++) {
                trie.search(q, 10);
            }
            double trieUs = (System.nanoTime() - s) / 1e3 / repeat;

            s = System.nanoTime();
            for (int r = 0; r < repeat / 20; r++) {
                linearPrefix(q, norm, cho, 10);
            }
            double linearUs = (System.nanoTime() - s) / 1e3 / (repeat / 20);
            System.out.printf("| %s | %.2f | %.1f | %.0f |%n", q, trieUs, linearUs, linearUs / trieUs);
        }
    }

    // 비교 대상: 전체를 돌며 접두어 일치 확인. 순위는 리스트 순서 그대로
    static List<Integer> linearPrefix(String query, String[] norm, String[] cho, int limit) {
        String q = HangulUtil.normalize(query);
        boolean choMode = HangulUtil.isAllChoseong(q);
        String[] target = choMode ? cho : norm;
        List<Integer> result = new ArrayList<>();
        for (int i = 0; i < target.length; i++) {
            if (target[i].startsWith(q)) {
                result.add(i);
            }
        }
        return result.size() > limit ? result.subList(0, limit) : result;
    }

    static void radius(List<Attraction> all) {
        int n = all.size();
        double[] lats = new double[n];
        double[] lngs = new double[n];
        for (int i = 0; i < n; i++) {
            lats[i] = all.get(i).latitude();
            lngs[i] = all.get(i).longitude();
        }
        long s = System.nanoTime();
        GridIndex grid = new GridIndex(lats, lngs);
        double buildMs = (System.nanoTime() - s) / 1e6;

        Random rnd = new Random(5);
        int queries = 500;
        int[] centers = new int[queries];
        for (int i = 0; i < queries; i++) {
            centers[i] = rnd.nextInt(n);
        }

        // 워밍업
        for (int i = 0; i < 100; i++) {
            grid.withinRadius(lats[centers[i]], lngs[centers[i]], 5000, null);
            linearRadius(lats, lngs, lats[centers[i]], lngs[centers[i]], 5000);
        }

        long found = 0;
        s = System.nanoTime();
        for (int c : centers) {
            found += grid.withinRadius(lats[c], lngs[c], 5000, null).size();
        }
        double gridUs = (System.nanoTime() - s) / 1e3 / queries;

        long foundLinear = 0;
        s = System.nanoTime();
        for (int c : centers) {
            foundLinear += linearRadius(lats, lngs, lats[c], lngs[c], 5000);
        }
        double linearUs = (System.nanoTime() - s) / 1e3 / queries;

        if (found != foundLinear) {
            throw new IllegalStateException("격자와 전체 순회 결과 개수가 다릅니다");
        }
        System.out.println();
        System.out.printf("격자 인덱스 구축 %.0fms, 칸 %,d개%n", buildMs, grid.cellCount());
        System.out.println();
        System.out.println("| 반경 5km 검색 (" + queries + "회 평균) | 시간 (us) | 평균 결과 수 |");
        System.out.println("|---|---|---|");
        System.out.printf("| 격자 인덱스 | %.1f | %.1f |%n", gridUs, (double) found / queries);
        System.out.printf("| 전체 순회 | %.1f | %.1f |%n", linearUs, (double) foundLinear / queries);
    }

    static int linearRadius(double[] lats, double[] lngs, double lat, double lng, double r) {
        int count = 0;
        for (int i = 0; i < lats.length; i++) {
            if (GeoUtil.haversine(lat, lng, lats[i], lngs[i]) <= r) {
                count++;
            }
        }
        return count;
    }

    private static long usedMemory() {
        Runtime rt = Runtime.getRuntime();
        return rt.totalMemory() - rt.freeMemory();
    }
}
