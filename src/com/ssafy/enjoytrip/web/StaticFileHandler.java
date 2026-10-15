package com.ssafy.enjoytrip.web;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

/**
 * web 폴더의 정적 파일(HTML, CSS, JS)을 내려준다.
 * 경로가 web 폴더 밖으로 나가는 요청(../)은 막는다.
 */
public class StaticFileHandler implements HttpHandler {

    private static final Map<String, String> TYPES = Map.of(
            "html", "text/html; charset=UTF-8",
            "css", "text/css; charset=UTF-8",
            "js", "application/javascript; charset=UTF-8",
            "json", "application/json; charset=UTF-8",
            "png", "image/png",
            "jpg", "image/jpeg",
            "svg", "image/svg+xml",
            "ico", "image/x-icon");

    private final Path root;

    public StaticFileHandler(Path root) {
        this.root = root.toAbsolutePath().normalize();
    }

    @Override
    public void handle(HttpExchange ex) throws IOException {
        try {
            String path = ex.getRequestURI().getPath();
            if (path.equals("/")) {
                path = "/index.html";
            }
            Path file = root.resolve(path.substring(1)).normalize();
            if (!file.startsWith(root) || !Files.isRegularFile(file)) {
                byte[] msg = "Not Found".getBytes();
                ex.sendResponseHeaders(404, msg.length);
                try (OutputStream out = ex.getResponseBody()) {
                    out.write(msg);
                }
                return;
            }
            String name = file.getFileName().toString();
            String ext = name.substring(name.lastIndexOf('.') + 1).toLowerCase();
            byte[] bytes = Files.readAllBytes(file);
            ex.getResponseHeaders().set("Content-Type", TYPES.getOrDefault(ext, "application/octet-stream"));
            // 개발 중 수정한 화면이 바로 보이도록 캐시하지 않는다
            ex.getResponseHeaders().set("Cache-Control", "no-cache");
            ex.sendResponseHeaders(200, bytes.length);
            try (OutputStream out = ex.getResponseBody()) {
                out.write(bytes);
            }
        } finally {
            ex.close();
        }
    }
}
