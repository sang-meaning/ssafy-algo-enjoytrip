package com.ssafy.enjoytrip.service;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

import com.ssafy.enjoytrip.algo.geo.GeoUtil;
import com.ssafy.enjoytrip.dao.PlanDao;
import com.ssafy.enjoytrip.model.Attraction;
import com.ssafy.enjoytrip.model.Plan;

/**
 * 여행 경로 저장/불러오기.
 * 저장할 때 총 이동거리는 클라이언트 값을 믿지 않고 저장 순서대로 직선거리를 다시 계산한다.
 */
public class PlanService {

    private final PlanDao planDao;
    private final AttractionService attractions;

    public PlanService(PlanDao planDao, AttractionService attractions) {
        this.planDao = planDao;
        this.attractions = attractions;
    }

    public Map<String, Object> save(String title, String routeMode, boolean returnToStart, List<Integer> contentIds)
            throws SQLException {
        String t = title == null ? "" : title.trim();
        if (t.isEmpty() || t.length() > 100) {
            throw new IllegalArgumentException("경로 이름은 1~100자로 입력해 주세요");
        }
        List<Integer> ids = new ArrayList<>(new LinkedHashSet<>(contentIds));
        if (ids.isEmpty()) {
            throw new IllegalArgumentException("저장할 관광지가 없습니다");
        }
        if (ids.size() > RouteService.MAX_POINTS) {
            throw new IllegalArgumentException("최대 " + RouteService.MAX_POINTS + "곳까지 저장할 수 있습니다");
        }

        double total = 0;
        Attraction prev = null;
        for (int id : ids) {
            Attraction a = attractions.require(id);
            if (prev != null) {
                total += GeoUtil.haversine(prev.latitude(), prev.longitude(), a.latitude(), a.longitude());
            }
            prev = a;
        }
        if (returnToStart && ids.size() > 1) {
            Attraction first = attractions.require(ids.get(0));
            total += GeoUtil.haversine(prev.latitude(), prev.longitude(), first.latitude(), first.longitude());
        }

        String mode = (routeMode == null || routeMode.isBlank()) ? "straight" : routeMode;
        int planId = planDao.insert(new Plan(0, t, mode, returnToStart, total, null, ids));
        return get(planId);
    }

    public List<Map<String, Object>> list() throws SQLException {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Plan p : planDao.findAll()) {
            Map<String, Object> m = summary(p);
            List<String> names = new ArrayList<>();
            for (int id : p.contentIds()) {
                Attraction a = attractions.get(id);
                if (a != null) {
                    names.add(a.title());
                }
            }
            m.put("titles", names);
            result.add(m);
        }
        return result;
    }

    public Map<String, Object> get(int planId) throws SQLException {
        Plan p = planDao.findById(planId);
        if (p == null) {
            throw new NotFoundException("저장된 경로가 없습니다: " + planId);
        }
        Map<String, Object> m = summary(p);
        List<Attraction> items = new ArrayList<>();
        for (int id : p.contentIds()) {
            Attraction a = attractions.get(id);
            if (a != null) {
                items.add(a);
            }
        }
        m.put("attractions", items);
        return m;
    }

    public void delete(int planId) throws SQLException {
        if (!planDao.delete(planId)) {
            throw new NotFoundException("저장된 경로가 없습니다: " + planId);
        }
    }

    private static Map<String, Object> summary(Plan p) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("planId", p.planId());
        m.put("title", p.title());
        m.put("routeMode", p.routeMode());
        m.put("returnToStart", p.returnToStart());
        m.put("totalDistanceM", Math.round(p.totalDistanceM()));
        m.put("createdAt", p.createdAt());
        m.put("count", p.contentIds().size());
        return m;
    }
}
