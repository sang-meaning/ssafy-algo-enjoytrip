package com.ssafy.enjoytrip.algo.geo;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.function.IntPredicate;

/**
 * 위경도 격자(grid) 공간 인덱스.
 *
 * 지도를 일정한 크기의 칸으로 나누고 각 칸에 들어 있는 지점 번호를 저장한다.
 * 반경 검색을 할 때 원이 걸치는 칸만 보면 되므로 전체 N 개를 다 볼 필요가 없다.
 *
 *  - 구축 O(N)
 *  - 반경 r 검색 O(C + M), C = 원이 걸치는 칸 수, M = 그 칸들에 든 지점 수
 *  - 전체 순회 방식은 매번 O(N)
 *
 * 칸 크기는 기본 0.02도(약 2.2km). 반경 5km 검색이면 가로세로 5~6칸 정도를 본다.
 * KD-Tree 보다 구현이 단순하고, 관광지처럼 점이 고르게 퍼져 있지 않아도 실용적으로 충분히 빠르다.
 */
public class GridIndex {

    /** 검색 결과 한 건 */
    public record Hit(int id, double distance) {
    }

    private final double cellDeg;
    private final double[] lats;
    private final double[] lngs;
    private final Map<Long, int[]> cells = new HashMap<>();

    /**
     * @param lats    id 번째 지점의 위도
     * @param lngs    id 번째 지점의 경도
     * @param cellDeg 칸 크기 (도)
     */
    public GridIndex(double[] lats, double[] lngs, double cellDeg) {
        if (lats.length != lngs.length) {
            throw new IllegalArgumentException("위도와 경도 배열 길이가 다릅니다");
        }
        this.lats = lats;
        this.lngs = lngs;
        this.cellDeg = cellDeg;

        // 칸별 개수를 먼저 세고 배열을 한 번에 만든다 (리스트보다 메모리 절약)
        Map<Long, Integer> counts = new HashMap<>();
        for (int i = 0; i < lats.length; i++) {
            counts.merge(key(cellOf(lats[i]), cellOf(lngs[i])), 1, Integer::sum);
        }
        Map<Long, Integer> filled = new HashMap<>();
        for (Map.Entry<Long, Integer> e : counts.entrySet()) {
            cells.put(e.getKey(), new int[e.getValue()]);
            filled.put(e.getKey(), 0);
        }
        for (int i = 0; i < lats.length; i++) {
            long k = key(cellOf(lats[i]), cellOf(lngs[i]));
            int pos = filled.get(k);
            cells.get(k)[pos] = i;
            filled.put(k, pos + 1);
        }
    }

    public GridIndex(double[] lats, double[] lngs) {
        this(lats, lngs, 0.02);
    }

    /**
     * 중심에서 radius(m) 이내의 지점을 가까운 순으로 돌려준다.
     *
     * @param filter 추가 조건 (관광타입 등). null 이면 모두 통과
     */
    public List<Hit> withinRadius(double lat, double lng, double radius, IntPredicate filter) {
        double dLat = radius / GeoUtil.METERS_PER_LAT_DEGREE;
        double dLng = radius / GeoUtil.metersPerLngDegree(lat);
        int r0 = cellOf(lat - dLat);
        int r1 = cellOf(lat + dLat);
        int c0 = cellOf(lng - dLng);
        int c1 = cellOf(lng + dLng);

        List<Hit> hits = new ArrayList<>();
        for (int r = r0; r <= r1; r++) {
            for (int c = c0; c <= c1; c++) {
                int[] ids = cells.get(key(r, c));
                if (ids == null) {
                    continue;
                }
                for (int id : ids) {
                    if (filter != null && !filter.test(id)) {
                        continue;
                    }
                    double dist = GeoUtil.haversine(lat, lng, lats[id], lngs[id]);
                    if (dist <= radius) {
                        hits.add(new Hit(id, dist));
                    }
                }
            }
        }
        hits.sort((a, b) -> Double.compare(a.distance, b.distance));
        return hits;
    }

    /**
     * 가까운 k 개. 반경 안의 후보를 크기 k 의 최대 힙으로 거른다. O(M log k)
     */
    public List<Hit> nearest(double lat, double lng, int k, double maxRadius, IntPredicate filter) {
        PriorityQueue<Hit> heap = new PriorityQueue<>((a, b) -> Double.compare(b.distance, a.distance));
        double dLat = maxRadius / GeoUtil.METERS_PER_LAT_DEGREE;
        double dLng = maxRadius / GeoUtil.metersPerLngDegree(lat);
        for (int r = cellOf(lat - dLat); r <= cellOf(lat + dLat); r++) {
            for (int c = cellOf(lng - dLng); c <= cellOf(lng + dLng); c++) {
                int[] ids = cells.get(key(r, c));
                if (ids == null) {
                    continue;
                }
                for (int id : ids) {
                    if (filter != null && !filter.test(id)) {
                        continue;
                    }
                    double dist = GeoUtil.haversine(lat, lng, lats[id], lngs[id]);
                    if (dist > maxRadius) {
                        continue;
                    }
                    if (heap.size() < k) {
                        heap.add(new Hit(id, dist));
                    } else if (dist < heap.peek().distance) {
                        heap.poll();
                        heap.add(new Hit(id, dist));
                    }
                }
            }
        }
        List<Hit> result = new ArrayList<>(heap);
        result.sort((a, b) -> Double.compare(a.distance, b.distance));
        return result;
    }

    /**
     * 지도 화면 영역(남서쪽 ~ 북동쪽) 안의 지점. 지도 이동 시 마커를 불러올 때 쓴다.
     * limit 를 넘으면 거기서 멈춘다.
     */
    public int[] inBounds(double swLat, double swLng, double neLat, double neLng, IntPredicate filter, int limit) {
        if (limit <= 0) {
            return new int[0];
        }
        int[] out = new int[Math.min(limit, 1024)];
        int count = 0;
        for (int r = cellOf(swLat); r <= cellOf(neLat); r++) {
            for (int c = cellOf(swLng); c <= cellOf(neLng); c++) {
                int[] ids = cells.get(key(r, c));
                if (ids == null) {
                    continue;
                }
                for (int id : ids) {
                    if (lats[id] < swLat || lats[id] > neLat || lngs[id] < swLng || lngs[id] > neLng) {
                        continue;
                    }
                    if (filter != null && !filter.test(id)) {
                        continue;
                    }
                    if (count == out.length) {
                        out = Arrays.copyOf(out, Math.min(limit, out.length * 2));
                    }
                    out[count++] = id;
                    if (count >= limit) {
                        return Arrays.copyOf(out, count);
                    }
                }
            }
        }
        return Arrays.copyOf(out, count);
    }

    public int cellCount() {
        return cells.size();
    }

    private int cellOf(double deg) {
        return (int) Math.floor(deg / cellDeg);
    }

    private static long key(int row, int col) {
        return ((long) row << 32) | (col & 0xffffffffL);
    }
}
