package com.ssafy.enjoytrip.web;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.ssafy.enjoytrip.algo.geo.GridIndex;
import com.ssafy.enjoytrip.model.Attraction;
import com.ssafy.enjoytrip.model.Region;
import com.ssafy.enjoytrip.service.AttractionService;

/**
 * 관광지 조회 API
 *
 *  GET /api/content-types                       관광타입 목록
 *  GET /api/regions                             시도 목록 (시군구 포함)
 *  GET /api/attractions/suggest?q=&limit=       자동완성 (트라이)
 *  GET /api/attractions/search?q=&types=&regnCd=&limit=   이름 검색
 *  GET /api/attractions/bounds?swLat=&swLng=&neLat=&neLng=&types=&limit=   지도 영역
 *  GET /api/attractions/nearby?lat=&lng=&radius=&types=&limit=   주변 (가까운 순)
 *  GET /api/attractions?ids=1,2,3               여러 건 기본 정보
 *  GET /api/attractions/{contentId}             상세
 */
public class AttractionApi {

    private static final int MAX_LIMIT = 500;

    private final AttractionService service;

    public AttractionApi(AttractionService service) {
        this.service = service;
    }

    public void register(Router router) {
        router.get("/api/content-types", req -> contentTypes());
        router.get("/api/regions", req -> regions());
        router.get("/api/attractions/suggest", req ->
                service.suggest(req.param("q", ""), clamp(req.intParam("limit", 10), 10)));
        router.get("/api/attractions/search", req ->
                service.search(req.param("q", ""), req.intSetParam("types"), req.param("regnCd"),
                        clamp(req.intParam("limit", 50), MAX_LIMIT)));
        router.get("/api/attractions/bounds", req ->
                service.inBounds(req.doubleParam("swLat"), req.doubleParam("swLng"),
                        req.doubleParam("neLat"), req.doubleParam("neLng"),
                        req.intSetParam("types"), clamp(req.intParam("limit", 300), MAX_LIMIT)));
        router.get("/api/attractions/nearby", req -> nearby(req));
        router.get("/api/attractions", req -> byIds(req));
        router.get("/api/attractions/{contentId}", req -> service.detail(req.pathInt("contentId")));
    }

    private List<Map<String, Object>> contentTypes() {
        List<Map<String, Object>> list = new ArrayList<>();
        for (Map.Entry<Integer, String> e : AttractionService.CONTENT_TYPES.entrySet()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("contentTypeId", e.getKey());
            m.put("name", e.getValue());
            list.add(m);
        }
        return list;
    }

    /** 시도별로 묶어서 돌려준다 */
    private List<Map<String, Object>> regions() {
        Map<String, Map<String, Object>> byRegn = new LinkedHashMap<>();
        for (Region r : service.regions()) {
            Map<String, Object> regn = byRegn.computeIfAbsent(r.regnCd(), k -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("regnCd", r.regnCd());
                m.put("regnNm", r.regnNm());
                m.put("signgu", new ArrayList<Map<String, String>>());
                return m;
            });
            @SuppressWarnings("unchecked")
            List<Map<String, String>> signgu = (List<Map<String, String>>) regn.get("signgu");
            signgu.add(Map.of("signguCd", r.signguCd(), "signguNm", r.signguNm()));
        }
        return new ArrayList<>(byRegn.values());
    }

    private List<Map<String, Object>> nearby(Request req) {
        double lat = req.doubleParam("lat");
        double lng = req.doubleParam("lng");
        int radius = Math.min(req.intParam("radius", 5000), 20000);
        int limit = clamp(req.intParam("limit", 30), MAX_LIMIT);
        Set<Integer> types = req.intSetParam("types");

        List<Map<String, Object>> list = new ArrayList<>();
        for (GridIndex.Hit h : service.nearest(lat, lng, limit, radius, types)) {
            Attraction a = service.byIndex(h.id());
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("attraction", a);
            m.put("distance", Math.round(h.distance()));
            list.add(m);
        }
        return list;
    }

    /** ids=1,2,3 순서대로 기본 정보만. 없는 id 는 건너뛴다. 상세 API(외부 호출)를 쓰지 않는다. */
    private List<Attraction> byIds(Request req) {
        List<Attraction> list = new ArrayList<>();
        for (int id : req.intSetParam("ids")) {
            Attraction a = service.get(id);
            if (a != null) {
                list.add(a);
            }
            if (list.size() >= MAX_LIMIT) {
                break;
            }
        }
        return list;
    }

    private static int clamp(int v, int max) {
        return Math.max(1, Math.min(v, max));
    }
}
