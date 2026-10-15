package com.ssafy.enjoytrip;

import java.net.InetSocketAddress;
import java.nio.file.Paths;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Executors;

import com.ssafy.enjoytrip.algo.route.DistanceProvider;
import com.ssafy.enjoytrip.algo.route.DistanceProviders;
import com.ssafy.enjoytrip.config.AppConfig;
import com.ssafy.enjoytrip.dao.AttractionDao;
import com.ssafy.enjoytrip.dao.PlanDao;
import com.ssafy.enjoytrip.dao.RegionDao;
import com.ssafy.enjoytrip.dao.SchemaInitializer;
import com.ssafy.enjoytrip.external.DataLoader;
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
 * 프로그램 진입점. DB 를 준비하고 관광지 데이터를 메모리에 올린 뒤 웹 서버를 띄운다.
 * JDK 에 들어 있는 com.sun.net.httpserver 를 써서 별도 WAS 없이 실행된다.
 */
public class Main {

    public static void main(String[] args) throws Exception {
        AppConfig config = AppConfig.get();
        int port = config.getInt("server.port", 8000);

        TourApiClient tourApi = config.get("datagokr.key", "").isEmpty() ? null : new TourApiClient();
        prepareDatabase(tourApi);

        long t0 = System.currentTimeMillis();
        AttractionService attractionService = new AttractionService(new AttractionDao(), new RegionDao(), tourApi);
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

    /**
     * 테이블을 만들고, 관광지 데이터가 비어 있으면 TourAPI 에서 받아 온다 (처음 한 번, 2~3분).
     * MySQL 에 접속할 수 없으면 원인을 알려주고 종료한다.
     */
    private static void prepareDatabase(TourApiClient tourApi) throws Exception {
        AttractionDao attractionDao = new AttractionDao();
        try {
            SchemaInitializer.run();
            int count = attractionDao.count();
            if (count > 0) {
                return;
            }
        } catch (SQLException e) {
            System.out.println("MySQL 에 접속하지 못했습니다: " + e.getMessage());
            System.out.println("MySQL 이 켜져 있는지, config/app.properties 의 db.user / db.password 가 맞는지 확인해 주세요.");
            System.exit(1);
        }
        if (tourApi == null) {
            System.out.println("관광지 데이터가 없고 datagokr.key 도 비어 있어서 빈 상태로 시작합니다.");
            return;
        }
        System.out.println("관광지 데이터가 비어 있어서 TourAPI 에서 받아 옵니다. 처음 한 번만 2~3분 걸립니다.");
        new DataLoader(tourApi).loadAll();
    }

    /** 화면에서 필요한 설정만 내려준다. REST 키 같은 비밀값은 넣지 않는다. */
    private static Map<String, Object> clientConfig(AppConfig config) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("kakaoJsKey", config.get("kakao.js.key", ""));
        m.put("distanceProvider", config.get("route.distance.provider", "straight"));
        return m;
    }
}
