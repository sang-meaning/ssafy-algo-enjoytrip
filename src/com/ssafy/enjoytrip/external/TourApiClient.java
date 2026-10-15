package com.ssafy.enjoytrip.external;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.ssafy.enjoytrip.config.AppConfig;
import com.ssafy.enjoytrip.util.Json;

/**
 * 한국관광공사 TourAPI(KorService2) 호출 클라이언트.
 *
 * 공공데이터포털 응답에서 신경 쓸 부분
 *  - _type=json 을 붙여야 JSON 으로 온다. 기본은 XML.
 *  - 결과가 없으면 items 가 빈 문자열("")로 온다.
 *  - 결과가 한 건이면 item 이 배열이 아니라 객체로 오는 경우가 있다.
 *  - 키 오류 같은 게이트웨이 에러는 OpenAPI_ServiceResponse 라는 다른 형태로 온다.
 *  - 가끔 연결이 끊기므로 몇 번 재시도한다.
 */
public class TourApiClient {

    private static final int MAX_RETRY = 3;

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    private final String baseUrl;
    private final String serviceKey;

    public TourApiClient() {
        AppConfig config = AppConfig.get();
        this.baseUrl = config.get("tour.api.base", "https://apis.data.go.kr/B551011/KorService2");
        this.serviceKey = config.require("datagokr.key");
    }

    public TourApiClient(String baseUrl, String serviceKey) {
        this.baseUrl = baseUrl;
        this.serviceKey = serviceKey;
    }

    /** 한 페이지 조회 결과 */
    public record Page(List<Map<String, Object>> items, int totalCount, int pageNo, int numOfRows) {
    }

    /**
     * 오퍼레이션 하나를 호출해서 item 목록과 전체 개수를 돌려준다.
     *
     * @param operation 예: areaBasedList2, ldongCode2
     * @param params    serviceKey, MobileOS, MobileApp, _type 을 뺀 나머지 파라미터
     */
    public Page call(String operation, Map<String, String> params) throws IOException {
        return call(operation, params, MAX_RETRY, Duration.ofSeconds(60));
    }

    /**
     * 화면에서 사용자가 기다리는 호출용. 오래 붙잡지 않도록 제한 시간을 짧게, 재시도는 한 번만 한다.
     * 실패하면 호출하는 쪽에서 기본 정보만 보여주면 된다.
     */
    public Page callQuick(String operation, Map<String, String> params) throws IOException {
        return call(operation, params, 2, Duration.ofSeconds(5));
    }

    private Page call(String operation, Map<String, String> params, int maxRetry, Duration timeout) throws IOException {
        Map<String, String> all = new LinkedHashMap<>();
        all.put("MobileOS", "ETC");
        all.put("MobileApp", "EnjoyTrip");
        all.put("_type", "json");
        all.putAll(params);

        StringBuilder url = new StringBuilder(baseUrl).append('/').append(operation)
                .append("?serviceKey=").append(encode(serviceKey));
        for (Map.Entry<String, String> e : all.entrySet()) {
            url.append('&').append(e.getKey()).append('=').append(encode(e.getValue()));
        }

        String body = getWithRetry(url.toString(), maxRetry, timeout);
        return parsePage(body);
    }

    private String getWithRetry(String url, int maxRetry, Duration timeout) throws IOException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(timeout)
                .GET()
                .build();
        IOException last = null;
        for (int attempt = 1; attempt <= maxRetry; attempt++) {
            try {
                HttpResponse<String> res = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                if (res.statusCode() == 200) {
                    return res.body();
                }
                last = new IOException("HTTP " + res.statusCode() + ": " + shorten(res.body()));
            } catch (IOException e) {
                last = e;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("요청이 중단되었습니다", e);
            }
            if (attempt < maxRetry) {
                sleep(1000L * attempt);
            }
        }
        throw last;
    }

    @SuppressWarnings("unchecked")
    static Page parsePage(String body) throws IOException {
        String trimmed = body.trim();
        if (!trimmed.startsWith("{")) {
            // 키가 틀리면 XML 에러가 오기도 한다
            throw new IOException("JSON 응답이 아닙니다: " + shorten(trimmed));
        }
        Map<String, Object> root = Json.parseObject(trimmed);

        Map<String, Object> gatewayError = Json.obj(root, "OpenAPI_ServiceResponse");
        if (gatewayError != null) {
            Map<String, Object> header = Json.obj(gatewayError, "cmmMsgHeader");
            throw new IOException("공공데이터포털 오류: " + (header == null ? gatewayError : Json.str(header, "returnAuthMsg")));
        }

        Map<String, Object> response = Json.obj(root, "response");
        if (response == null) {
            throw new IOException("알 수 없는 응답 형식: " + shorten(trimmed));
        }
        Map<String, Object> header = Json.obj(response, "header");
        String resultCode = header == null ? null : Json.str(header, "resultCode");
        if (!"0000".equals(resultCode)) {
            throw new IOException("TourAPI 오류 " + resultCode + ": " + (header == null ? "" : Json.str(header, "resultMsg")));
        }

        Map<String, Object> bodyObj = Json.obj(response, "body");
        int total = toInt(bodyObj.get("totalCount"));
        int pageNo = toInt(bodyObj.get("pageNo"));
        int rows = toInt(bodyObj.get("numOfRows"));

        List<Map<String, Object>> items = Collections.emptyList();
        Object itemsObj = bodyObj.get("items");
        if (itemsObj instanceof Map) {
            Object item = ((Map<String, Object>) itemsObj).get("item");
            if (item instanceof List) {
                items = new ArrayList<>();
                for (Object o : (List<Object>) item) {
                    items.add((Map<String, Object>) o);
                }
            } else if (item instanceof Map) {
                items = List.of((Map<String, Object>) item);
            }
        }
        return new Page(items, total, pageNo, rows);
    }

    private static int toInt(Object o) {
        if (o == null) {
            return 0;
        }
        if (o instanceof Number) {
            return ((Number) o).intValue();
        }
        String s = o.toString().trim();
        return s.isEmpty() ? 0 : Integer.parseInt(s);
    }

    private static String encode(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    private static String shorten(String s) {
        return s.length() > 300 ? s.substring(0, 300) + "..." : s;
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
