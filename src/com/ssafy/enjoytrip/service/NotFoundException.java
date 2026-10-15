package com.ssafy.enjoytrip.service;

/**
 * 요청한 데이터가 없을 때. HTTP 404 로 응답한다.
 */
public class NotFoundException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public NotFoundException(String message) {
        super(message);
    }
}
