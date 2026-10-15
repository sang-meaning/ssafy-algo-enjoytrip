package com.ssafy.enjoytrip.algo.route;

import java.util.List;

import com.ssafy.enjoytrip.algo.geo.GeoPoint;
import com.ssafy.enjoytrip.algo.geo.GeoUtil;

/**
 * 좌표만으로 하버사인 직선거리를 구해서 가중치로 쓴다.
 * 대칭 행렬이므로 절반만 계산한다. n 개 지점이면 n(n-1)/2 번 계산, O(n^2).
 */
public class StraightDistanceProvider implements DistanceProvider {

    @Override
    public double[][] matrix(List<GeoPoint> points) {
        int n = points.size();
        double[][] d = new double[n][n];
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                double dist = GeoUtil.haversine(points.get(i), points.get(j));
                d[i][j] = dist;
                d[j][i] = dist;
            }
        }
        return d;
    }

    @Override
    public String name() {
        return "straight";
    }

    @Override
    public String unit() {
        return "m";
    }
}
