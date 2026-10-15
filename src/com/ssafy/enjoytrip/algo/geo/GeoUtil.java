package com.ssafy.enjoytrip.algo.geo;

/**
 * 좌표 계산 유틸.
 */
public final class GeoUtil {

    /** 지구 평균 반지름 (m) */
    public static final double EARTH_RADIUS_M = 6_371_000.0;

    /** 위도 1도의 길이 (m). 위도와 상관없이 거의 일정하다. */
    public static final double METERS_PER_LAT_DEGREE = Math.PI * EARTH_RADIUS_M / 180.0;

    private GeoUtil() {
    }

    /**
     * 하버사인 공식으로 두 좌표 사이의 대원 거리(m)를 구한다.
     * 지구를 구로 보고 계산하므로 수 km 범위에서 오차는 0.5% 이내다.
     */
    public static double haversine(double lat1, double lng1, double lat2, double lng2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return EARTH_RADIUS_M * c;
    }

    public static double haversine(GeoPoint a, GeoPoint b) {
        return haversine(a.lat(), a.lng(), b.lat(), b.lng());
    }

    /** 해당 위도에서 경도 1도의 길이 (m). 위도가 높을수록 짧아진다. */
    public static double metersPerLngDegree(double lat) {
        return METERS_PER_LAT_DEGREE * Math.cos(Math.toRadians(lat));
    }
}
