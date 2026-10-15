package com.ssafy.enjoytrip;

import java.net.InetSocketAddress;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Executors;

import com.ssafy.enjoytrip.algo.route.DistanceProvider;
import com.ssafy.enjoytrip.algo.route.DistanceProviders;
import com.ssafy.enjoytrip.config.AppConfig;
import com.ssafy.enjoytrip.dao.AttractionDao;
import com.ssafy.enjoytrip.dao.PlanDao;
import com.ssafy.enjoytrip.dao.RegionDao;
import com.ssafy.enjoytrip.external.TourApiClient;
import com.ssafy.enjoytrip.service.AttractionService;
import com.ssafy.enjoytrip.service.PlanService;
import com.ssafy.enjoytrip.service.RouteService;
import com.ssafy.enjoytrip.web.AttractionApi;
import com.ssafy.enjoytrip.web.RouteApi;
import com.ssafy.enjoytrip.web.Router;
import com.ssafy.enjoytrip.web.StaticFileHandler;
import com.sun.net.httpserver.HttpServer;

/**
 * 프로그램 진입점. 관광지 데이터를 메모리에 올리고 웹 서버를 띄운다.
 * JDK 에 들어 있는 com.sun.net.httpserver 를 써서 별도 WAS 없이 실행된다.
 */
public class Main {

    public static void main(String[] args) throws Exception {
        AppConfig config = AppConfig.get();
        int port = config.getInt("server.port", 8080);

        long t0 = System.currentTimeMillis();
        TourApiClient tourApi = config.get("datagokr.key", "").isEmpty() ? null : new TourApiClient();
        AttractionService attractionService = new AttractionService(new AttractionDao(), new RegionDao(), tourApi);
        if (attractionService.size() == 0) {
            System.out.println("관광지 데이터가 없습니다. DataLoader 를 먼저 실행해 주세요.");
        }
        System.out.printf("관광지 %d건 로드, 인덱스 구축 %dms%n",
                attractionService.size(), System.currentTimeMillis() - t0);

        DistanceProvider distance = DistanceProviders.of(config.get("route.distance.provider", "straight"));
        RouteService routeService = new RouteService(attractionService, distance);
        PlanService planService = new PlanService(new PlanDao(), attractionService);

        Router api = new Router();
        api.get("/api/config", req -> clientConfig(config));
        new AttractionApi(attractionService).register(api);
        new RouteApi(routeService, planService).register(api);

        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/api/", api);
        server.createContext("/", new StaticFileHandler(Paths.get("web")));
        server.setExecutor(Executors.newFixedThreadPool(8));
        server.start();
        System.out.println("서버 시작: http://localhost:" + port);
    }

    /** 화면에서 필요한 설정만 내려준다. REST 키 같은 비밀값은 넣지 않는다. */
    private static Map<String, Object> clientConfig(AppConfig config) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("kakaoJsKey", config.get("kakao.js.key", ""));
        m.put("distanceProvider", config.get("route.distance.provider", "straight"));
        return m;
    }
}
