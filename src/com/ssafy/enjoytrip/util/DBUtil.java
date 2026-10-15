package com.ssafy.enjoytrip.util;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

import com.ssafy.enjoytrip.config.AppConfig;

/**
 * JDBC 커넥션을 얻는 유틸. 커넥션 풀 없이 요청마다 새로 연결한다.
 * 관광지 목록은 서버 시작 시 한 번 메모리에 올리기 때문에 DB 호출이 많지 않다.
 */
public final class DBUtil {

    private DBUtil() {
    }

    public static Connection getConnection() throws SQLException {
        AppConfig config = AppConfig.get();
        return DriverManager.getConnection(
                config.require("db.url"),
                config.get("db.user", "root"),
                config.get("db.password", ""));
    }
}
