package com.ssafy.enjoytrip.bench;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import com.ssafy.enjoytrip.algo.geo.GeoPoint;
import com.ssafy.enjoytrip.algo.route.StraightDistanceProvider;
import com.ssafy.enjoytrip.algo.tsp.BitmaskDpTsp;
import com.ssafy.enjoytrip.algo.tsp.BruteForceTsp;
import com.ssafy.enjoytrip.algo.tsp.MemoDpTsp;
import com.ssafy.enjoytrip.algo.tsp.TspProblem;
import com.ssafy.enjoytrip.algo.tsp.TspResult;
import com.ssafy.enjoytrip.algo.tsp.TspSolver;
import com.ssafy.enjoytrip.algo.tsp.TwoOptTsp;

/**
 * TSP 알고리즘 성능 비교. DB 없이 실행된다.
 *
 * 서울 시내(약 10km x 10km) 무작위 좌표 n 개로 직선거리 행렬을 만들고
 * 출발지를 0번으로 고정한 경로형 문제를 푼다.
 *
 * 측정 항목
 *  - 평균 시간: JIT 워밍업을 한 뒤 여러 번 돌린 평균
 *  - DP 갱신 횟수: dp[mask][last] 에서 다음 지점으로 넘어가며 값을 비교한 횟수
 *  - 2-opt 오차: 최적해 대비 몇 % 더 긴지
 *
 * 결과는 마크다운 표로 출력하므로 기획서에 그대로 붙여 넣을 수 있다.
 */
public class TspBenchmark {

    private static final int BRUTE_MAX = 11;
    private static final int CASES = 5;

    public static void main(String[] args) {
        TspSolver brute = new BruteForceTsp();
        TspSolver bottomUp = new BitmaskDpTsp();
        TspSolver topDown = new MemoDpTsp();
        TspSolver twoOpt = new TwoOptTsp();

        warmUp(bottomUp, topDown, twoOpt, brute);

        System.out.println("| n | 완전탐색 (ms) | 상향식 DP (ms) | 하향식 DP (ms) | 2-opt (ms) | DP 갱신 횟수 | DP 테이블 메모리 | 2-opt 평균 오차 |");
        System.out.println("|---|---|---|---|---|---|---|---|");

        Random rnd = new Random(2026);
        for (int n = 4; n <= 18; n++) {
            double tBrute = 0, tUp = 0, tDown = 0, tTwo = 0, gap = 0;
            long work = 0;
            for (int c = 0; c < CASES; c++) {
                TspProblem p = new TspProblem(seoulPoints(rnd, n), 0, -1, false);
                TspResult opt = null;

                if (n <= BRUTE_MAX) {
                    long s = System.nanoTime();
                    opt = brute.solve(p);
                    tBrute += (System.nanoTime() - s) / 1e6;
                }
                long s = System.nanoTime();
                TspResult up = bottomUp.solve(p);
                tUp += (System.nanoTime() - s) / 1e6;

                s = System.nanoTime();
                TspResult down = topDown.solve(p);
                tDown += (System.nanoTime() - s) / 1e6;

                s = System.nanoTime();
                TspResult two = twoOpt.solve(p);
                tTwo += (System.nanoTime() - s) / 1e6;

                if (opt != null && Math.abs(opt.cost() - up.cost()) > 1e-6) {
                    throw new IllegalStateException("DP 결과가 완전탐색과 다릅니다 n=" + n);
                }
                if (Math.abs(up.cost() - down.cost()) > 1e-6) {
                    throw new IllegalStateException("상향식과 하향식 결과가 다릅니다 n=" + n);
                }
                gap += (two.cost() / up.cost() - 1) * 100;
                work = up.work();
            }
            // dp(double 8바이트) + parent(int 4바이트) 테이블 크기
            double tableMb = (double) (1L << n) * n * 12 / (1024 * 1024);
            System.out.printf("| %d | %s | %.3f | %.3f | %.3f | %,d | %.2f MB | %.2f%% |%n",
                    n,
                    n <= BRUTE_MAX ? String.format("%.3f", tBrute / CASES) : "-",
                    tUp / CASES, tDown / CASES, tTwo / CASES,
                    work, tableMb, gap / CASES);
        }
    }

    private static void warmUp(TspSolver... solvers) {
        Random rnd = new Random(1);
        for (int i = 0; i < 30; i++) {
            TspProblem p = new TspProblem(seoulPoints(rnd, 9), 0, -1, false);
            for (TspSolver s : solvers) {
                s.solve(p);
            }
        }
    }

    static double[][] seoulPoints(Random rnd, int n) {
        List<GeoPoint> pts = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            pts.add(new GeoPoint(37.50 + rnd.nextDouble() * 0.09, 126.92 + rnd.nextDouble() * 0.11));
        }
        return new StraightDistanceProvider().matrix(pts);
    }
}
