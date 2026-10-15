package com.ssafy.enjoytrip.config;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

/**
 * config/app.properties 를 읽어서 들고 있는 설정 클래스.
 * 실행 위치가 프로젝트 루트라고 가정한다. (STS에서 Run 하면 기본이 프로젝트 루트)
 * -Dapp.config=경로 로 다른 파일을 지정할 수도 있다.
 */
public final class AppConfig {

    private static final String DEFAULT_PATH = "config/app.properties";
    private static AppConfig instance;

    private final Properties props = new Properties();

    private AppConfig(Path path) {
        if (!Files.exists(path)) {
            throw new IllegalStateException(path.toAbsolutePath()
                    + " 파일이 없습니다. config/app.properties.example 을 복사해서 만들어 주세요.");
        }
        try (InputStream in = Files.newInputStream(path);
             Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            props.load(reader);
        } catch (IOException e) {
            throw new IllegalStateException("설정 파일을 읽지 못했습니다: " + path, e);
        }
    }

    public static synchronized AppConfig get() {
        if (instance == null) {
            String path = System.getProperty("app.config", DEFAULT_PATH);
            instance = new AppConfig(Paths.get(path));
        }
        return instance;
    }

    public String get(String key) {
        String value = props.getProperty(key);
        return value == null ? null : value.trim();
    }

    public String get(String key, String defaultValue) {
        String value = get(key);
        return (value == null || value.isEmpty()) ? defaultValue : value;
    }

    public int getInt(String key, int defaultValue) {
        String value = get(key);
        if (value == null || value.isEmpty()) {
            return defaultValue;
        }
        return Integer.parseInt(value);
    }

    /** 값이 비어 있으면 바로 알려주기 위해 사용한다. API 키처럼 꼭 필요한 값에 쓴다. */
    public String require(String key) {
        String value = get(key);
        if (value == null || value.isEmpty()) {
            throw new IllegalStateException("config/app.properties 에 " + key + " 값이 없습니다.");
        }
        return value;
    }
}
