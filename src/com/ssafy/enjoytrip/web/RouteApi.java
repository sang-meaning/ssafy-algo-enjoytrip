package com.ssafy.enjoytrip.web;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import com.ssafy.enjoytrip.service.PlanService;
import com.ssafy.enjoytrip.service.RouteService;

/**
 * 경로 생성과 저장 API
 *
 *  POST   /api/routes/optimize   {contentIds:[], startId, endId, returnToStart}
 *  POST   /api/routes/random     {baseIds:[], count, types:[], seed}
 *  GET    /api/plans             저장 목록
 *  POST   /api/plans             {title, routeMode, returnToStart, contentIds:[]} 방문 순서대로
 *  GET    /api/plans/{planId}    불러오기
 *  DELETE /api/plans/{planId}    삭제
 */
public class RouteApi {

    private final RouteService routeService;
    private final PlanService planService;

    public RouteApi(RouteService routeService, PlanService planService) {
        this.routeService = routeService;
        this.planService = planService;
    }

    public void register(Router router) {
        router.post("/api/routes/optimize", req -> {
            Map<String, Object> b = req.body();
            return routeService.optimize(
                    Request.toIntList(b.get("contentIds"), "contentIds"),
                    Request.toIntOrNull(b.get("startId")),
                    Request.toIntOrNull(b.get("endId")),
                    Boolean.TRUE.equals(b.get("returnToStart")));
        });

        router.post("/api/routes/random", req -> {
            Map<String, Object> b = req.body();
            Set<Integer> types = b.get("types") == null
                    ? Set.of()
                    : new LinkedHashSet<>(Request.toIntList(b.get("types"), "types"));
            Object seed = b.get("seed");
            return routeService.random(
                    Request.toIntList(b.get("baseIds"), "baseIds"),
                    Request.toInt(b.get("count"), "count"),
                    types,
                    seed instanceof Number ? ((Number) seed).longValue() : null);
        });

        router.get("/api/plans", req -> planService.list());
        router.post("/api/plans", req -> {
            Map<String, Object> b = req.body();
            return planService.save(
                    (String) b.get("title"),
                    (String) b.get("routeMode"),
                    Boolean.TRUE.equals(b.get("returnToStart")),
                    Request.toIntList(b.get("contentIds"), "contentIds"));
        });
        router.get("/api/plans/{planId}", req -> planService.get(req.pathInt("planId")));
        router.delete("/api/plans/{planId}", req -> {
            planService.delete(req.pathInt("planId"));
            return Map.of("deleted", true);
        });
    }
}
