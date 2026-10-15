package com.ssafy.enjoytrip.algo;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import com.ssafy.enjoytrip.algo.geo.GeoUtil;
import com.ssafy.enjoytrip.algo.geo.GridIndex;
import com.ssafy.enjoytrip.algo.route.RandomPicker;
import com.ssafy.enjoytrip.support.Check;

/**
 * 격자 인덱스 결과가 전체 순회 결과와 같은지 확인한다.
 */
public class GeoIndexTest {

    public static void main(String[] args) {
        gridMatchesBruteForce();
        randomPicker();
        Check.done("GeoIndexTest");
    }

    static void gridMatchesBruteForce() {
        Random rnd = new Random(3);
        int n = 20000;
        double[] lats = new double[n];
        double[] lngs = new double[n];
        for (int i = 0; i < n; i++) {
            lats[i] = 33.2 + rnd.nextDouble() * 5.3;
            lngs[i] = 126.0 + rnd.nextDouble() * 3.5;
        }
        GridIndex grid = new GridIndex(lats, lngs);

        for (int t = 0; t < 100; t++) {
            double lat = 33.5 + rnd.nextDouble() * 4.5;
            double lng = 126.3 + rnd.nextDouble() * 3;
            double radius = 1000 + rnd.nextDouble() * 20000;

            Set<Integer> expected = new HashSet<>();
            for (int i = 0; i < n; i++) {
                if (GeoUtil.haversine(lat, lng, lats[i], lngs[i]) <= radius) {
                    expected.add(i);
                }
            }
            List<GridIndex.Hit> hits = grid.withinRadius(lat, lng, radius, null);
            Set<Integer> actual = new HashSet<>();
            for (GridIndex.Hit h : hits) {
                actual.add(h.id());
            }
            Check.equals(expected, actual, "반경 검색 결과 일치 #" + t);
            for (int i = 1; i < hits.size(); i++) {
                Check.isTrue(hits.get(i - 1).distance() <= hits.get(i).distance(), "거리순 정렬");
            }

            List<GridIndex.Hit> near = grid.nearest(lat, lng, 5, radius, null);
            int k = Math.min(5, hits.size());
            Check.equals(k, near.size(), "가까운 k개 개수");
            for (int i = 0; i < k; i++) {
                Check.near(hits.get(i).distance(), near.get(i).distance(), 1e-9, "가까운 k개 거리");
            }

            // 화면 영역 검색
            double swLat = lat - 0.05, swLng = lng - 0.05, neLat = lat + 0.05, neLng = lng + 0.05;
            int[] box = grid.inBounds(swLat, swLng, neLat, neLng, null, Integer.MAX_VALUE);
            long boxExpected = 0;
            for (int i = 0; i < n; i++) {
                if (lats[i] >= swLat && lats[i] <= neLat && lngs[i] >= swLng && lngs[i] <= neLng) {
                    boxExpected++;
                }
            }
            Check.equals(boxExpected, (long) box.length, "영역 검색 개수");
        }
        // 필터 적용
        List<GridIndex.Hit> even = grid.withinRadius(37.5, 127.0, 30000, id -> id % 2 == 0);
        Check.isTrue(even.stream().allMatch(h -> h.id() % 2 == 0), "필터 적용");
    }

    static void randomPicker() {
        Random rnd = new Random(1);
        int[] cand = {10, 20, 30, 40, 50, 60};
        int[] picked = RandomPicker.pick(cand, 4, rnd);
        Check.equals(4, picked.length, "요청 개수만큼 뽑기");
        Check.equals(4L, Arrays.stream(picked).distinct().count(), "중복 없음");
        Check.equals(3, RandomPicker.pick(new int[] {1, 2, 3}, 10, rnd).length, "후보가 적으면 있는 만큼");
        Check.equals(0, RandomPicker.pick(new int[0], 3, rnd).length, "후보 없음");
        Check.equals(6, cand.length, "원본 배열 유지");
        Check.equals(10, cand[0], "원본 배열 순서 유지");

        // 균등성: 6개 중 1개 뽑기를 60000번 하면 각각 약 10000번
        int[] freq = new int[6];
        for (int i = 0; i < 60000; i++) {
            int v = RandomPicker.pick(cand, 1, rnd)[0];
            freq[v / 10 - 1]++;
        }
        for (int f : freq) {
            Check.near(10000, f, 500, "균등 분포");
        }
    }
}
