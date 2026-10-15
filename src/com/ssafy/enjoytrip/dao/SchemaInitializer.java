package com.ssafy.enjoytrip.dao;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import com.ssafy.enjoytrip.util.DBUtil;

/**
 * 서버 시작 시 sql/schema.sql 을 실행해서 테이블을 만든다.
 * 모든 문장이 CREATE TABLE IF NOT EXISTS, INSERT IGNORE 라서 매번 실행해도 기존 데이터는 그대로다.
 *
 * 데이터베이스(enjoytrip) 자체는 접속 URL 의 createDatabaseIfNotExist=true 옵션으로 만들어지므로
 * 스크립트의 CREATE DATABASE, USE 문은 건너뛴다.
 */
public final class SchemaInitializer {

    private static final Path SCHEMA = Paths.get("sql", "schema.sql");

    private SchemaInitializer() {
    }

    public static void run() throws SQLException, IOException {
        if (!Files.exists(SCHEMA)) {
            System.out.println(SCHEMA + " 파일이 없어 테이블 생성을 건너뜁니다.");
            return;
        }
        List<String> statements = split(Files.readString(SCHEMA, StandardCharsets.UTF_8));
        try (Connection conn = DBUtil.getConnection();
             Statement st = conn.createStatement()) {
            for (String sql : statements) {
                st.execute(sql);
            }
        }
    }

    /** 주석을 지우고 세미콜론으로 나눈다. CREATE DATABASE, USE 는 뺀다. */
    static List<String> split(String script) {
        StringBuilder sb = new StringBuilder();
        for (String line : script.split("\\R")) {
            String trimmed = line.trim();
            if (trimmed.startsWith("--")) {
                continue;
            }
            sb.append(line).append('\n');
        }
        List<String> result = new ArrayList<>();
        for (String part : sb.toString().split(";")) {
            String sql = part.trim();
            if (sql.isEmpty()) {
                continue;
            }
            String upper = sql.toUpperCase();
            if (upper.startsWith("CREATE DATABASE") || upper.startsWith("USE ")) {
                continue;
            }
            result.add(sql);
        }
        return result;
    }
}
