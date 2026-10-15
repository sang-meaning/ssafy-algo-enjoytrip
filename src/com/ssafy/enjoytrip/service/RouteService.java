package com.ssafy.enjoytrip.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import com.ssafy.enjoytrip.algo.geo.GeoPoint;
import com.ssafy.enjoytrip.algo.geo.GridIndex;
import com.ssafy.enjoytrip.algo.route.DistanceProvider;
import com.ssafy.enjoytrip.algo.route.RandomPicker;
import com.ssafy.enjoytrip.algo.tsp.AutoTsp;
import com.ssafy.enjoytrip.algo.tsp.TspProblem;
import com.ssafy.enjoytrip.algo.tsp.TspResult;
import com.ssafy.enjoytrip.algo.tsp.TspSolver;
import com.ssafy.enjoytrip.model.Attraction;

/**
 * 여행 경로 생성 서비스.
 *
 * 1) 경로 최적화: 사용자가 고른 관광지들의 방문 순서를 TSP 로 정한다.
 * 2) 랜덤 경로: 사용자가 고른 관광지(최소 1곳) 주변 반경 5km 안에서 관광지를 무작위로 더 뽑고
 *    전체를 TSP 로 정렬한다. 후보가 모자라면 있는 만큼만 뽑는다.
 */
public class RouteService {

    /** 경로 최적화에 넣을 수 있는 최대 지점 수. 15개까지는 DP(최적해), 그 위로는 2-opt(근사해) */
    public static final int MAX_POINTS = 30;
    /** 랜덤 경로는 최적해가 나오는 범위로 제한한다 */
    public static final int MAX_RANDOM_POINTS = AutoTsp.EXACT_LIMIT;
    public static final double RANDOM_RADIUS_M = 5000;
    /** 랜덤 경로 기본 후보: 관광지, 문화시설 */
    public static final Set<Integer> DEFAULT_RANDOM_TYPES = Set.of(12, 14);

    /** 구간 하나 */
    public record Leg(int fromId, int toId, double cost) {
    }

    /** 경로 결과 */
    public record RouteResult(
            List<Attraction> order,
            List<Leg> legs,
            double totalCost,
            String unit,
            String distanceProvider,
            boolean returnToStart,
            String algorithm,
            boolean exact,
            long work,
            double elapsedMs) {
    }

    private final AttractionService attractions;
    private final DistanceProvider distance;
    private final TspSolver solver = new AutoTsp();

    public RouteService(AttractionService attractions, DistanceProvider distance) {
        this.attractions = attractions;
        this.distance = distance;
    }

    public RouteResult optimize(List<Integer> contentIds, Integer startId, Integer endId, boolean returnToStart) {
        List<Integer> ids = new ArrayList<>(new LinkedHashSet<>(contentIds)); // 중복 제거, 순서 유지
        if (ids.isEmpty()) {
            throw new IllegalArgumentException("관광지를 하나 이상 선택해 주세요");
        }
        if (ids.size() > MAX_POINTS) {
            throw new IllegalArgumentException("한 번에 최대 " + MAX_POINTS + "곳까지 경로를 만들 수 있습니다");
        }

        List<Attraction> points = new ArrayList<>();
        List<GeoPoint> geo = new ArrayList<>();
        for (int id : ids) {
            Attraction a = attractions.require(id);
            points.add(a);
            geo.add(new GeoPoint(a.latitude(), a.longitude()));
        }
        int start = indexIn(ids, startId, "출발지");
        int end = indexIn(ids, endId, "도착지");

        long t0 = System.nanoTime();
        double[][] matrix = distance.matrix(geo);
        TspResult tsp = solver.solve(new TspProblem(matrix, start, end, returnToStart));
        double elapsedMs = (System.nanoTime() - t0) / 1e6;

        List<Attraction> ordered = new ArrayList<>();
        for (int i : tsp.order()) {
            ordered.add(points.get(i));
        }
        List<Leg> legs = new ArrayList<>();
        int[] o = tsp.order();
        for (int i = 0; i + 1 < o.length; i++) {
            legs.add(new Leg(points.get(o[i]).contentId(), points.get(o[i + 1]).contentId(), matrix[o[i]][o[i + 1]]));
        }
        if (returnToStart && o.length > 1) {
            int last = o[o.length - 1];
            legs.add(new Leg(points.get(last).contentId(), points.get(o[0]).contentId(), matrix[last][o[0]]));
        }
        return new RouteResult(ordered, legs, tsp.cost(), distance.unit(), distance.name(), returnToStart,
                tsp.algorithm(), tsp.exact(), tsp.work(), elapsedMs);
    }

    /**
     * 랜덤 경로.
     *
     * @param baseIds    사용자가 고른 관광지. 첫 번째 곳이 반경의 기준점이자 출발지가 된다
     * @param totalCount 고른 곳을 포함한 전체 관광지 수
     * @param types      후보 관광타입. 비어 있으면 관광지, 문화시설
     * @param seed       같은 결과를 다시 보고 싶을 때 쓰는 시드. null 이면 매번 다름
     */
    public Map<String, Object> random(List<Integer> baseIds, int totalCount, Set<Integer> types, Long seed) {
        List<Integer> base = new ArrayList<>(new LinkedHashSet<>(baseIds));
        if (base.isEmpty()) {
            throw new IllegalArgumentException("기준이 될 관광지를 하나 이상 선택해 주세요");
        }
        if (totalCount < base.size()) {
            totalCount = base.size();
        }
        if (totalCount > MAX_RANDOM_POINTS) {
            throw new IllegalArgumentException("랜덤 경로는 최대 " + MAX_RANDOM_POINTS + "곳까지 만들 수 있습니다");
        }
        Set<Integer> candidateTypes = (types == null || types.isEmpty()) ? DEFAULT_RANDOM_TYPES : types;

        Attraction center = attractions.require(base.get(0));
        Set<Integer> baseIdx = new LinkedHashSet<>();
        for (int id : base) {
            baseIdx.add(attractions.indexOf(id));
        }

        // 1) 격자 인덱스로 반경 5km 안의 후보를 모은다 (이미 고른 곳 제외)
        List<GridIndex.Hit> hits = attractions.withinRadius(center.latitude(), center.longitude(),
                RANDOM_RADIUS_M, candidateTypes);
        int[] candidates = hits.stream().mapToInt(GridIndex.Hit::id).filter(i -> !baseIdx.contains(i)).toArray();

        // 2) 부분 Fisher-Yates 로 필요한 수만큼 무작위 선택. 후보가 적으면 있는 만큼만
        int need = totalCount - base.size();
        Random rnd = seed == null ? new Random() : new Random(seed);
        int[] picked = RandomPicker.pick(candidates, need, rnd);

        List<Integer> all = new ArrayList<>(base);
        for (int idx : picked) {
            all.add(attractions.byIndex(idx).contentId());
        }

        // 3) 첫 번째로 고른 곳에서 출발하도록 TSP
        RouteResult route = optimize(all, base.get(0), null, false);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("route", route);
        result.put("centerId", center.contentId());
        result.put("radius", RANDOM_RADIUS_M);
        result.put("candidateCount", candidates.length);
        result.put("requestedCount", totalCount);
        result.put("actualCount", all.size());
        result.put("pickedIds", all.subList(base.size(), all.size()));
        if (all.size() < totalCount) {
            result.put("message", "반경 " + (int) (RANDOM_RADIUS_M / 1000) + "km 안에 후보가 " + candidates.length
                    + "곳뿐이라 " + all.size() + "곳으로 만들었습니다");
        }
        return result;
    }

    private static int indexIn(List<Integer> ids, Integer contentId, String label) {
        if (contentId == null) {
            return -1;
        }
        int i = ids.indexOf(contentId);
        if (i < 0) {
            throw new IllegalArgumentException(label + "는 선택한 관광지 중 하나여야 합니다");
        }
        return i;
    }
}
