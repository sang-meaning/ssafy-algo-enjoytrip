package com.ssafy.enjoytrip.web;

import java.io.IOException;
import java.io.InputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.ssafy.enjoytrip.util.Json;
import com.sun.net.httpserver.HttpExchange;

/**
 * HttpExchange 를 쓰기 편하게 감싼 요청 객체.
 * 쿼리 파라미터, 경로 변수({id}), JSON 본문을 꺼내는 메서드를 제공한다.
 */
public class Request {

    private final HttpExchange exchange;
    private final Map<String, String> query;
    private final Map<String, String> pathVars;
    private Map<String, Object> body;

    Request(HttpExchange exchange, Map<String, String> pathVars) {
        this.exchange = exchange;
        this.query = parseQuery(exchange.getRequestURI().getRawQuery());
        this.pathVars = pathVars;
    }

    public String param(String name) {
        return query.get(name);
    }

    public String param(String name, String defaultValue) {
        String v = query.get(name);
        return (v == null || v.isBlank()) ? defaultValue : v;
    }

    public int intParam(String name, int defaultValue) {
        String v = query.get(name);
        if (v == null || v.isBlank()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(v.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(name + " 값이 숫자가 아닙니다: " + v);
        }
    }

    public double doubleParam(String name) {
        String v = query.get(name);
        if (v == null || v.isBlank()) {
            throw new IllegalArgumentException(name + " 값이 필요합니다");
        }
        try {
            return Double.parseDouble(v.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(name + " 값이 숫자가 아닙니다: " + v);
        }
    }

    /** "12,14,39" 같은 콤마 구분 정수 목록 */
    public Set<Integer> intSetParam(String name) {
        String v = query.get(name);
        if (v == null || v.isBlank()) {
            return Collections.emptySet();
        }
        Set<Integer> set = new LinkedHashSet<>();
        for (String s : v.split(",")) {
            if (!s.isBlank()) {
                set.add(Integer.parseInt(s.trim()));
            }
        }
        return set;
    }

    public int pathInt(String name) {
        String v = pathVars.get(name);
        try {
            return Integer.parseInt(v);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(name + " 값이 숫자가 아닙니다: " + v);
        }
    }

    /** 요청 본문을 JSON 객체로 읽는다. 여러 번 불러도 한 번만 읽는다. */
    public Map<String, Object> body() throws IOException {
        if (body == null) {
            try (InputStream in = exchange.getRequestBody()) {
                String text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                body = text.isBlank() ? new HashMap<>() : Json.parseObject(text);
            }
        }
        return body;
    }

    // ---------------------------------------------------------------- JSON 본문 값 꺼내기

    public static int toInt(Object o, String name) {
        if (o instanceof Number) {
            return ((Number) o).intValue();
        }
        if (o instanceof String && !((String) o).isBlank()) {
            return Integer.parseInt(((String) o).trim());
        }
        throw new IllegalArgumentException(name + " 값이 필요합니다");
    }

    public static Integer toIntOrNull(Object o) {
        if (o == null || (o instanceof String && ((String) o).isBlank())) {
            return null;
        }
        return toInt(o, "");
    }

    public static List<Integer> toIntList(Object o, String name) {
        if (!(o instanceof List)) {
            throw new IllegalArgumentException(name + " 는 배열이어야 합니다");
        }
        List<Integer> list = new ArrayList<>();
        for (Object v : (List<?>) o) {
            list.add(toInt(v, name));
        }
        return list;
    }

    private static Map<String, String> parseQuery(String raw) {
        Map<String, String> map = new HashMap<>();
        if (raw == null || raw.isEmpty()) {
            return map;
        }
        for (String pair : raw.split("&")) {
            int eq = pair.indexOf('=');
            String k = eq < 0 ? pair : pair.substring(0, eq);
            String v = eq < 0 ? "" : pair.substring(eq + 1);
            map.put(URLDecoder.decode(k, StandardCharsets.UTF_8), URLDecoder.decode(v, StandardCharsets.UTF_8));
        }
        return map;
    }
}
