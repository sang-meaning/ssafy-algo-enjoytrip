package com.ssafy.enjoytrip.algo.route;

import java.util.List;

import com.ssafy.enjoytrip.algo.geo.GeoPoint;

/**
 * 지점 목록으로 TSP 가중치 행렬을 만드는 역할.
 *
 * 지금은 직선거리(StraightDistanceProvider)만 있고, 카카오 길찾기 키를 받으면
 * 같은 인터페이스로 구현체를 하나 더 만들어 설정만 바꾸면 되도록 분리했다.
 * matrix[i][j] 는 i 에서 j 로 가는 비용이다. 대중교통처럼 i->j 와 j->i 가 다를 수 있다.
 */
public interface DistanceProvider {

    double[][] matrix(List<GeoPoint> points);

    /** 화면과 저장 데이터에 남길 식별자. 예: straight, kakao-walk */
    String name();

    /** 비용의 단위. 직선거리는 m, 길찾기 API 는 초(s) */
    String unit();
}
