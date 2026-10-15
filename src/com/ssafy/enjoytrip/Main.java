package com.ssafy.enjoytrip;

import com.ssafy.enjoytrip.config.AppConfig;

/**
 * 프로그램 진입점. 설정을 읽고 웹 서버를 띄운다.
 */
public class Main {

    public static void main(String[] args) {
        AppConfig config = AppConfig.get();
        int port = config.getInt("server.port", 8080);
        System.out.println("EnjoyTrip 설정 로드 완료, port=" + port);
    }
}
