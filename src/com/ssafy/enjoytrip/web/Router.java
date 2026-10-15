package com.ssafy.enjoytrip.web;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.ssafy.enjoytrip.service.NotFoundException;
import com.ssafy.enjoytrip.util.Json;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

/**
 * /api 아래 요청을 메서드와 경로로 찾아 핸들러를 실행한다.
 * 경로에 {id} 처럼 쓰면 경로 변수로 받는다.
 * 핸들러가 돌려준 객체는 JSON 으로 응답한다.
 */
public class Router implements HttpHandler {

    @FunctionalInterface
    public interface Handler {
        Object handle(Request req) throws Exception;
    }

    private record Route(String method, String[] parts, Handler handler, int status) {
    }

    private final List<Route> routes = new ArrayList<>();

    public Router get(String path, Handler h) {
        return add("GET", path, h, 200);
    }

    public Router post(String path, Handler h) {
        return add("POST", path, h, 200);
    }

    public Router delete(String path, Handler h) {
        return add("DELETE", path, h, 200);
    }

    private Router add(String method, String path, Handler h, int status) {
        routes.add(new Route(method, split(path), h, status));
        return this;
    }

    @Override
    public void handle(HttpExchange ex) throws IOException {
        try {
            addCorsHeaders(ex);
            if ("OPTIONS".equals(ex.getRequestMethod())) {
                ex.sendResponseHeaders(204, -1);
                return;
            }
            String[] reqParts = split(ex.getRequestURI().getPath());
            boolean pathMatched = false;
            for (Route r : routes) {
                Map<String, String> vars = match(r.parts, reqParts);
                if (vars == null) {
                    continue;
                }
                pathMatched = true;
                if (!r.method.equals(ex.getRequestMethod())) {
                    continue;
                }
                Object result = r.handler.handle(new Request(ex, vars));
                send(ex, r.status, result);
                return;
            }
            if (pathMatched) {
                send(ex, 405, error("허용되지 않은 메서드입니다"));
            } else {
                send(ex, 404, error("없는 API 입니다: " + ex.getRequestURI().getPath()));
            }
        } catch (IllegalArgumentException e) {
            sendQuietly(ex, 400, error(e.getMessage()));
        } catch (NotFoundException e) {
            sendQuietly(ex, 404, error(e.getMessage()));
        } catch (IOException e) {
            // 응답을 보내는 중 브라우저가 연결을 끊은 경우 (페이지 이동, 새로고침). 할 일이 없다.
        } catch (Exception e) {
            e.printStackTrace();
            sendQuietly(ex, 500, error("서버 오류가 발생했습니다: " + e.getMessage()));
        } finally {
            ex.close();
        }
    }

    private static Map<String, String> match(String[] pattern, String[] actual) {
        if (pattern.length != actual.length) {
            return null;
        }
        Map<String, String> vars = new HashMap<>();
        for (int i = 0; i < pattern.length; i++) {
            String p = pattern[i];
            if (p.startsWith("{") && p.endsWith("}")) {
                vars.put(p.substring(1, p.length() - 1), actual[i]);
            } else if (!p.equals(actual[i])) {
                return null;
            }
        }
        return vars;
    }

    private static String[] split(String path) {
        String trimmed = path.replaceAll("^/+|/+$", "");
        return trimmed.isEmpty() ? new String[0] : trimmed.split("/");
    }

    private static Map<String, Object> error(String message) {
        Map<String, Object> m = new HashMap<>();
        m.put("error", message);
        return m;
    }

    // 프론트를 VS Code Live Server 같은 다른 포트에서 띄워도 호출할 수 있게 열어 둔다
    private static void addCorsHeaders(HttpExchange ex) {
        ex.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        ex.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, DELETE, OPTIONS");
        ex.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");
    }

    private static void sendQuietly(HttpExchange ex, int status, Object body) {
        try {
            send(ex, status, body);
        } catch (IOException | IllegalStateException ignore) {
            // 이미 응답 헤더를 보냈거나 연결이 끊긴 경우
        }
    }

    static void send(HttpExchange ex, int status, Object body) throws IOException {
        byte[] bytes = Json.stringify(body).getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        ex.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = ex.getResponseBody()) {
            out.write(bytes);
        }
    }
}
