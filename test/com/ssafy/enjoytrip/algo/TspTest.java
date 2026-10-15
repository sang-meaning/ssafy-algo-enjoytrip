package com.ssafy.enjoytrip.algo;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import com.ssafy.enjoytrip.algo.geo.GeoPoint;
import com.ssafy.enjoytrip.algo.geo.GeoUtil;
import com.ssafy.enjoytrip.algo.route.StraightDistanceProvider;
import com.ssafy.enjoytrip.algo.tsp.BitmaskDpTsp;
import com.ssafy.enjoytrip.algo.tsp.BruteForceTsp;
import com.ssafy.enjoytrip.algo.tsp.MemoDpTsp;
import com.ssafy.enjoytrip.algo.tsp.TspProblem;
import com.ssafy.enjoytrip.algo.tsp.TspResult;
import com.ssafy.enjoytrip.algo.tsp.TwoOptTsp;
import com.ssafy.enjoytrip.support.Check;

/**
 * TSP 정확성 테스트.
 * 완전탐색을 정답으로 두고, 무작위 입력에서 DP 두 가지가 같은 비용을 내는지 확인한다.
 */
public class TspTest {

    public static void main(String[] args) {
        haversineKnownDistance();
        smallCases();
        dpMatchesBruteForce();
        twoOptIsValidAndNotBetterThanOptimal();
        invalidInput();
        Check.done("TspTest");
    }

    static void haversineKnownDistance() {
        // 경복궁 - 창덕궁 직선거리는 약 1.2km
        double d = GeoUtil.haversine(37.5760307, 126.9767219, 37.5776507, 126.9902306);
        Check.near(1200, d, 50, "경복궁-창덕궁 거리");
        // 서울역 - 부산역 약 325km
        double far = GeoUtil.haversine(37.5547, 126.9707, 35.1151, 129.0422);
        Check.near(325_000, far, 5_000, "서울역-부산역 거리");
    }

    static void smallCases() {
        double[][] one = {{0}};
        Check.equals(0.0, new BitmaskDpTsp().solve(TspProblem.path(one)).cost(), "지점 1개");

        // 일직선 위 네 점: 0 - 1 - 2 - 3 (간격 1). 출발 자유면 비용 3
        double[][] line = new double[4][4];
        for (int i = 0; i < 4; i++) {
            for (int j = 0; j < 4; j++) {
                line[i][j] = Math.abs(i - j);
            }
        }
        TspResult r = new BitmaskDpTsp().solve(TspProblem.path(line));
        Check.equals(3.0, r.cost(), "일직선 경로형");

        // 출발지를 1 로 고정하면 1-0-2-3 (1+2+1=4) 또는 1-2-3-0 (1+1+3=5) 중 4
        r = new BitmaskDpTsp().solve(new TspProblem(line, 1, -1, false));
        Check.equals(4.0, r.cost(), "일직선 출발 고정");
        Check.equals(1, r.order()[0], "출발지가 맨 앞");

        // 순회형이면 0-1-2-3-0 = 6
        r = new MemoDpTsp().solve(new TspProblem(line, -1, -1, true));
        Check.equals(6.0, r.cost(), "일직선 순회형");
    }

    static void dpMatchesBruteForce() {
        Random rnd = new Random(42);
        BruteForceTsp brute = new BruteForceTsp();
        BitmaskDpTsp bottomUp = new BitmaskDpTsp();
        MemoDpTsp topDown = new MemoDpTsp();

        for (int trial = 0; trial < 200; trial++) {
            int n = 2 + rnd.nextInt(7); // 2 ~ 8
            boolean asymmetric = rnd.nextBoolean();
            double[][] d = asymmetric ? randomAsymmetric(rnd, n) : randomPoints(rnd, n);

            int start = rnd.nextInt(3) == 0 ? -1 : rnd.nextInt(n);
            int end = rnd.nextInt(3) == 0 ? -1 : rnd.nextInt(n);
            boolean ret = rnd.nextInt(4) == 0;
            if (!ret && start != -1 && start == end) {
                end = -1;
            }
            TspProblem p = new TspProblem(d, start, end, ret);

            TspResult expect = brute.solve(p);
            TspResult a = bottomUp.solve(p);
            TspResult b = topDown.solve(p);

            String label = "n=" + n + " start=" + start + " end=" + end + " ret=" + ret + " asym=" + asymmetric;
            Check.near(expect.cost(), a.cost(), 1e-6, "상향식 DP 비용 " + label);
            Check.near(expect.cost(), b.cost(), 1e-6, "하향식 DP 비용 " + label);
            Check.isTrue(p.isValid(a.order()), "상향식 DP 순서 유효 " + label);
            Check.isTrue(p.isValid(b.order()), "하향식 DP 순서 유효 " + label);
            Check.near(a.cost(), p.cost(a.order()), 1e-6, "상향식 순서와 비용 일치 " + label);
            Check.near(b.cost(), p.cost(b.order()), 1e-6, "하향식 순서와 비용 일치 " + label);
        }
    }

    static void twoOptIsValidAndNotBetterThanOptimal() {
        Random rnd = new Random(7);
        for (int trial = 0; trial < 50; trial++) {
            int n = 3 + rnd.nextInt(10); // 3 ~ 12
            double[][] d = randomPoints(rnd, n);
            int start = rnd.nextBoolean() ? -1 : rnd.nextInt(n);
            TspProblem p = new TspProblem(d, start, -1, false);
            TspResult opt = new BitmaskDpTsp().solve(p);
            TspResult approx = new TwoOptTsp().solve(p);
            Check.isTrue(p.isValid(approx.order()), "2-opt 순서 유효 n=" + n);
            Check.isTrue(approx.cost() >= opt.cost() - 1e-6, "2-opt 는 최적해보다 짧을 수 없음 n=" + n);
        }
    }

    static void invalidInput() {
        double[][] d = new double[3][3];
        Check.throwsException(() -> new TspProblem(d, 0, 0, false), "출발지=도착지 경로형은 거부");
        Check.throwsException(() -> new TspProblem(d, 5, -1, false), "범위 밖 인덱스 거부");
        Check.throwsException(() -> new BitmaskDpTsp().solve(TspProblem.path(new double[19][19])), "DP 최대 개수 초과 거부");
    }

    // 서울 근처 무작위 좌표로 직선거리 행렬 생성
    static double[][] randomPoints(Random rnd, int n) {
        List<GeoPoint> pts = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            pts.add(new GeoPoint(37.5 + rnd.nextDouble() * 0.1, 126.9 + rnd.nextDouble() * 0.1));
        }
        return new StraightDistanceProvider().matrix(pts);
    }

    // 대중교통처럼 방향마다 비용이 다른 행렬
    static double[][] randomAsymmetric(Random rnd, int n) {
        double[][] d = new double[n][n];
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                d[i][j] = (i == j) ? 0 : 1 + rnd.nextInt(100);
            }
        }
        return d;
    }
}
