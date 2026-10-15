package com.ssafy.enjoytrip.service;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.IntPredicate;

import com.ssafy.enjoytrip.algo.geo.GridIndex;
import com.ssafy.enjoytrip.algo.search.AutocompleteTrie;
import com.ssafy.enjoytrip.algo.search.HangulUtil;
import com.ssafy.enjoytrip.dao.AttractionDao;
import com.ssafy.enjoytrip.dao.RegionDao;
import com.ssafy.enjoytrip.external.TourApiClient;
import com.ssafy.enjoytrip.model.Attraction;
import com.ssafy.enjoytrip.model.Region;

/**
 * 관광지 조회 서비스.
 *
 * 서버 시작 시 DB 의 관광지 전체(약 5만 건)를 메모리에 올리고
 * 자동완성 트라이와 격자 공간 인덱스를 만든다. 이후 조회는 DB 를 거치지 않는다.
 *
 * 내부 번호(idx)는 "추천 순위" 순서로 정렬한 리스트의 인덱스다.
 * 트라이는 순위 높은 것부터 넣어야 top-K 가 맞기 때문에 이렇게 맞춰 두었다.
 */
public class AttractionService {

    /** 관광타입별 노출 우선순위. 작을수록 먼저 보인다. */
    private static final Map<Integer, Integer> TYPE_PRIORITY = Map.of(
            12, 0, 14, 1, 28, 2, 15, 3, 38, 4, 39, 5, 32, 6, 25, 7);

    public static final Map<Integer, String> CONTENT_TYPES = new LinkedHashMap<>();
    static {
        CONTENT_TYPES.put(12, "관광지");
        CONTENT_TYPES.put(14, "문화시설");
        CONTENT_TYPES.put(15, "축제공연행사");
        CONTENT_TYPES.put(28, "레포츠");
        CONTENT_TYPES.put(32, "숙박");
        CONTENT_TYPES.put(38, "쇼핑");
        CONTENT_TYPES.put(39, "음식점");
    }

    private final List<Attraction> items;
    private final String[] normalizedTitles;
    private final Map<Integer, Integer> idxByContentId = new HashMap<>();
    private final AutocompleteTrie trie;
    private final GridIndex grid;
    private final List<Region> regions;

    private final TourApiClient tourApi;
    private final Map<Integer, Map<String, Object>> detailCache = new ConcurrentHashMap<>();

    public AttractionService(AttractionDao attractionDao, RegionDao regionDao, TourApiClient tourApi) throws SQLException {
        this(attractionDao.findAll(), regionDao.findAll(), tourApi);
    }

    public AttractionService(List<Attraction> all, List<Region> regions, TourApiClient tourApi) {
        List<Attraction> sorted = new ArrayList<>(all);
        sorted.sort(rankOrder());
        this.items = Collections.unmodifiableList(sorted);
        this.regions = regions;
        this.tourApi = tourApi;

        int n = items.size();
        double[] lats = new double[n];
        double[] lngs = new double[n];
        normalizedTitles = new String[n];
        trie = new AutocompleteTrie(10);
        for (int i = 0; i < n; i++) {
            Attraction a = items.get(i);
            idxByContentId.put(a.contentId(), i);
            lats[i] = a.latitude();
            lngs[i] = a.longitude();
            normalizedTitles[i] = HangulUtil.normalize(a.title());
            trie.add(i, a.title());
        }
        grid = new GridIndex(lats, lngs);
    }

    /** 추천 순위: 관광타입 우선순위 > 대표 이미지 있음 > 이름이 짧음 > contentId */
    static Comparator<Attraction> rankOrder() {
        return Comparator
                .comparingInt((Attraction a) -> TYPE_PRIORITY.getOrDefault(a.contentTypeId(), 9))
                .thenComparing(a -> a.hasImage() ? 0 : 1)
                .thenComparingInt(a -> a.title().length())
                .thenComparingInt(Attraction::contentId);
    }

    public int size() {
        return items.size();
    }

    public Attraction get(int contentId) {
        Integer idx = idxByContentId.get(contentId);
        return idx == null ? null : items.get(idx);
    }

    public Attraction require(int contentId) {
        Attraction a = get(contentId);
        if (a == null) {
            throw new NotFoundException("관광지를 찾을 수 없습니다: " + contentId);
        }
        return a;
    }

    public List<Region> regions() {
        return regions;
    }

    /** 자동완성. 트라이 top-K 캐시에서 바로 꺼낸다. */
    public List<Attraction> suggest(String query, int limit) {
        List<Attraction> result = new ArrayList<>();
        for (int idx : trie.search(query, limit)) {
            result.add(items.get(idx));
        }
        return result;
    }

    /**
     * 검색 버튼을 눌렀을 때의 목록 검색. 이름 어디에든 검색어가 들어 있으면 찾는다.
     * 중간 일치는 트라이로 처리할 수 없어서 전체를 순회한다. O(N * L), 5만 건에서 수 ms.
     */
    public List<Attraction> search(String query, Set<Integer> types, String regnCd, int limit) {
        String q = HangulUtil.normalize(query == null ? "" : query);
        List<Attraction> result = new ArrayList<>();
        for (int i = 0; i < items.size() && result.size() < limit; i++) {
            Attraction a = items.get(i);
            if (!types.isEmpty() && !types.contains(a.contentTypeId())) {
                continue;
            }
            if (regnCd != null && !regnCd.isEmpty() && !regnCd.equals(a.ldongRegnCd())) {
                continue;
            }
            if (!q.isEmpty() && !normalizedTitles[i].contains(q)) {
                continue;
            }
            result.add(a);
        }
        return result;
    }

    /** 지도 화면 영역 안의 관광지 (마커 표시용) */
    public List<Attraction> inBounds(double swLat, double swLng, double neLat, double neLng,
                                     Set<Integer> types, int limit) {
        int[] ids = grid.inBounds(swLat, swLng, neLat, neLng, typeFilter(types), Integer.MAX_VALUE);
        // 칸 순서로 나온 결과를 순위 순으로 정렬해서 limit 만큼 자른다. idx 가 곧 순위다.
        Arrays.sort(ids);
        List<Attraction> result = new ArrayList<>();
        for (int i = 0; i < ids.length && i < limit; i++) {
            result.add(items.get(ids[i]));
        }
        return result;
    }

    /** 반경 안의 관광지와 거리 */
    public List<GridIndex.Hit> withinRadius(double lat, double lng, double radius, Set<Integer> types) {
        return grid.withinRadius(lat, lng, radius, typeFilter(types));
    }

    public List<GridIndex.Hit> nearest(double lat, double lng, int k, double maxRadius, Set<Integer> types) {
        return grid.nearest(lat, lng, k, maxRadius, typeFilter(types));
    }

    public Attraction byIndex(int idx) {
        return items.get(idx);
    }

    public int indexOf(int contentId) {
        Integer idx = idxByContentId.get(contentId);
        if (idx == null) {
            throw new NotFoundException("관광지를 찾을 수 없습니다: " + contentId);
        }
        return idx;
    }

    /**
     * 상세 정보. 기본 정보에 개요(overview), 홈페이지를 TourAPI 에서 받아 붙인다.
     * API 호출 제한 때문에 한 번 받은 결과는 메모리에 캐시한다. 호출에 실패해도 기본 정보는 돌려준다.
     */
    public Map<String, Object> detail(int contentId) {
        Attraction a = require(contentId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("attraction", a);
        result.put("contentTypeName", CONTENT_TYPES.getOrDefault(a.contentTypeId(), ""));

        Map<String, Object> extra = detailCache.get(contentId);
        if (extra == null && tourApi != null) {
            try {
                TourApiClient.Page page = tourApi.call("detailCommon2", Map.of("contentId", String.valueOf(contentId)));
                if (!page.items().isEmpty()) {
                    Map<String, Object> item = page.items().get(0);
                    extra = new LinkedHashMap<>();
                    extra.put("overview", item.get("overview"));
                    extra.put("homepage", item.get("homepage"));
                    detailCache.put(contentId, extra);
                }
            } catch (Exception e) {
                System.out.println("상세 정보 조회 실패 " + contentId + ": " + e.getMessage());
            }
        }
        if (extra != null) {
            result.putAll(extra);
        }
        return result;
    }

    private IntPredicate typeFilter(Set<Integer> types) {
        if (types == null || types.isEmpty()) {
            return null;
        }
        return idx -> types.contains(items.get(idx).contentTypeId());
    }
}
