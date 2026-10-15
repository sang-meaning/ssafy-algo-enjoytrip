package com.ssafy.enjoytrip.external;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.ssafy.enjoytrip.dao.AttractionDao;
import com.ssafy.enjoytrip.dao.RegionDao;
import com.ssafy.enjoytrip.model.Attraction;
import com.ssafy.enjoytrip.model.Region;
import com.ssafy.enjoytrip.util.Json;

/**
 * TourAPI 데이터를 MySQL 로 옮기는 적재 프로그램. 서버와 별개로 한 번 실행한다.
 *
 * 개발계정은 오퍼레이션당 하루 1,000건 제한이 있어서, 서비스 중에는 API 를 직접 부르지 않고
 * 여기서 DB 에 넣어 둔 데이터만 쓴다. 전체 적재에 필요한 호출은 약 60회.
 *
 * 실행 인자로 관광타입을 주면 그 타입만 받는다. 예) 12 14 39
 * 인자가 없으면 여행코스(25)를 뺀 전체 타입을 받는다.
 */
public class DataLoader {

    private static final int PAGE_SIZE = 1000;
    private static final int[] DEFAULT_TYPES = {12, 14, 15, 28, 32, 38, 39};

    private final TourApiClient client;
    private final AttractionDao attractionDao = new AttractionDao();
    private final RegionDao regionDao = new RegionDao();

    public DataLoader(TourApiClient client) {
        this.client = client;
    }

    public static void main(String[] args) throws Exception {
        int[] types = DEFAULT_TYPES;
        if (args.length > 0) {
            types = new int[args.length];
            for (int i = 0; i < args.length; i++) {
                types[i] = Integer.parseInt(args[i]);
            }
        }

        new DataLoader(new TourApiClient()).loadAll(types);
    }

    /** 서버를 처음 켰는데 데이터가 비어 있을 때 Main 에서 호출한다 */
    public void loadAll() throws Exception {
        loadAll(DEFAULT_TYPES);
    }

    public void loadAll(int[] types) throws Exception {
        long start = System.currentTimeMillis();

        int regions = loadRegions();
        System.out.println("법정동 코드 " + regions + "건 저장");

        int total = 0;
        for (int type : types) {
            int saved = loadAttractions(type);
            System.out.println("관광타입 " + type + ": " + saved + "건 저장");
            total += saved;
        }
        System.out.printf("완료. 관광지 %d건, %.1f초%n", total, (System.currentTimeMillis() - start) / 1000.0);
    }

    public int loadRegions() throws Exception {
        List<Region> list = new ArrayList<>();
        int pageNo = 1;
        while (true) {
            TourApiClient.Page page = client.call("ldongCode2", Map.of(
                    "lDongListYn", "Y",
                    "numOfRows", String.valueOf(PAGE_SIZE),
                    "pageNo", String.valueOf(pageNo)));
            for (Map<String, Object> item : page.items()) {
                list.add(new Region(
                        Json.str(item, "lDongRegnCd"),
                        Json.str(item, "lDongRegnNm"),
                        Json.str(item, "lDongSignguCd"),
                        Json.str(item, "lDongSignguNm")));
            }
            if (page.items().isEmpty() || pageNo * PAGE_SIZE >= page.totalCount()) {
                break;
            }
            pageNo++;
        }
        return regionDao.upsertAll(list);
    }

    public int loadAttractions(int contentTypeId) throws Exception {
        int saved = 0;
        int skipped = 0;
        int pageNo = 1;
        while (true) {
            TourApiClient.Page page = client.call("areaBasedList2", Map.of(
                    "contentTypeId", String.valueOf(contentTypeId),
                    "arrange", "C",
                    "numOfRows", String.valueOf(PAGE_SIZE),
                    "pageNo", String.valueOf(pageNo)));

            List<Attraction> batch = new ArrayList<>();
            for (Map<String, Object> item : page.items()) {
                Attraction a = toAttraction(item);
                if (a == null) {
                    skipped++;
                } else {
                    batch.add(a);
                }
            }
            saved += attractionDao.upsertAll(batch);
            System.out.printf("  타입 %d, %d페이지 (%d/%d)%n", contentTypeId, pageNo,
                    Math.min(pageNo * PAGE_SIZE, page.totalCount()), page.totalCount());

            if (page.items().isEmpty() || pageNo * PAGE_SIZE >= page.totalCount()) {
                break;
            }
            pageNo++;
        }
        if (skipped > 0) {
            System.out.println("  좌표가 없어서 건너뛴 항목 " + skipped + "건");
        }
        return saved;
    }

    /** API 응답 한 건을 Attraction 으로 바꾼다. 좌표가 없거나 이상하면 null. */
    static Attraction toAttraction(Map<String, Object> item) {
        double lng = parseDouble(Json.str(item, "mapx"));
        double lat = parseDouble(Json.str(item, "mapy"));
        // 대한민국 범위를 벗어난 좌표는 버린다 (0 이나 빈 값이 섞여 있음)
        if (lat < 33 || lat > 39 || lng < 124 || lng > 132) {
            return null;
        }
        return new Attraction(
                Integer.parseInt(Json.str(item, "contentid")),
                Integer.parseInt(Json.str(item, "contenttypeid")),
                Json.str(item, "title"),
                emptyToNull(Json.str(item, "addr1")),
                emptyToNull(Json.str(item, "addr2")),
                emptyToNull(Json.str(item, "zipcode")),
                emptyToNull(Json.str(item, "tel")),
                emptyToNull(Json.str(item, "firstimage")),
                emptyToNull(Json.str(item, "firstimage2")),
                lat,
                lng,
                emptyToNull(Json.str(item, "lDongRegnCd")),
                emptyToNull(Json.str(item, "lDongSignguCd")),
                emptyToNull(Json.str(item, "lclsSystm1")),
                emptyToNull(Json.str(item, "lclsSystm2")),
                emptyToNull(Json.str(item, "lclsSystm3")),
                emptyToNull(Json.str(item, "modifiedtime")));
    }

    private static double parseDouble(String s) {
        if (s == null || s.isBlank()) {
            return 0;
        }
        try {
            return Double.parseDouble(s.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static String emptyToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }
}
