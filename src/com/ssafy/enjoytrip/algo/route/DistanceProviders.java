package com.ssafy.enjoytrip.algo.route;

/**
 * 설정값으로 가중치 계산 방식을 고른다.
 * 카카오 길찾기 구현체는 키를 받은 뒤 추가할 예정이라, 지금은 다른 값이 오면 직선거리로 대체한다.
 */
public final class DistanceProviders {

    private DistanceProviders() {
    }

    public static DistanceProvider of(String name) {
        if (name == null || name.isBlank() || "straight".equals(name)) {
            return new StraightDistanceProvider();
        }
        System.out.println("아직 지원하지 않는 경로 가중치 방식입니다: " + name + " -> 직선거리로 계산합니다");
        return new StraightDistanceProvider();
    }
}
