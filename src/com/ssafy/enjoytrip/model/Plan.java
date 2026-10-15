package com.ssafy.enjoytrip.model;

import java.util.List;

/**
 * 저장한 여행 경로. contentIds 는 방문 순서대로.
 */
public record Plan(
        int planId,
        String title,
        String routeMode,
        boolean returnToStart,
        double totalDistanceM,
        String createdAt,
        List<Integer> contentIds) {
}
