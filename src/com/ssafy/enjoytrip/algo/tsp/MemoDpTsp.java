package com.ssafy.enjoytrip.algo.tsp;

import java.util.Arrays;

/**
 * 비트마스크 DP, 하향식(top-down, 메모이제이션 재귀). 상향식과 비교하기 위해 만든 버전.
 *
 * f(mask, last) = mask 를 방문했고 지금 last 에 있을 때, 남은 지점을 모두 돌고 끝내는 최소 비용
 *   f(full, last) = 순회형이면 dist[last][start], 도착지가 있으면 last == end ? 0 : INF, 아니면 0
 *   f(mask, last) = min(dist[last][next] + f(mask | (1 << next), next))
 *
 * 상향식과 상태 수가 같아서 최악 시간 O(n^2 * 2^n), 공간 O(n * 2^n) 도 같다.
 * 차이점
 *  - 출발지에서 실제로 도달 가능한 상태만 자연스럽게 계산한다. 상향식은 같은 효과를 내려면
 *    출발지가 빠진 mask 를 직접 건너뛰도록 조건을 넣어야 한다.
 *  - 재귀 호출 비용이 있어서 n 이 커질수록 상향식보다 느려지는 경향이 있다.
 *    실제 수치는 test 폴더의 TspBenchmark 로 비교한다.
 *  - 재귀 깊이는 n 이라 스택 오버플로 걱정은 없다.
 *  - 결국 하향식으로 바꾼다고 처리 가능한 지점 수가 늘지는 않는다. 상태 수 자체가 같기 때문이다.
 *
 * 경로형이면서 출발지가 자유일 때 f 는 출발지와 무관하므로 메모를 모든 출발 후보가 공유한다.
 */
public class MemoDpTsp implements TspSolver {

    public static final int MAX_N = 18;
    private static final double INF = Double.POSITIVE_INFINITY;
    private static final double UNKNOWN = -1;

    private TspProblem p;
    private double[][] d;
    private int n;
    private int full;
    private double[][] memo;
    private int[][] choice;
    private long work;

    @Override
    public synchronized TspResult solve(TspProblem problem) {
        p = problem;
        d = problem.dist();
        n = problem.size();
        if (n > MAX_N) {
            throw new IllegalArgumentException("비트마스크 DP 는 " + MAX_N + "개 이하에서만 사용합니다 (입력 " + n + "개)");
        }
        if (n == 1) {
            return new TspResult(new int[] {0}, 0, name(), true, 1);
        }
        full = (1 << n) - 1;
        memo = new double[1 << n][n];
        choice = new int[1 << n][n];
        for (double[] row : memo) {
            Arrays.fill(row, UNKNOWN);
        }
        work = 0;

        double best = INF;
        int bestStart = -1;
        for (int s = 0; s < n; s++) {
            boolean canStart = (p.start() == -1) ? (s != p.end()) : (s == p.start());
            if (!canStart) {
                continue;
            }
            double c = f(1 << s, s);
            if (c < best) {
                best = c;
                bestStart = s;
            }
        }
        if (bestStart == -1 || best == INF) {
            throw new IllegalStateException("가능한 경로가 없습니다");
        }

        int[] order = new int[n];
        int mask = 1 << bestStart;
        int cur = bestStart;
        order[0] = cur;
        for (int i = 1; i < n; i++) {
            int next = choice[mask][cur];
            order[i] = next;
            mask |= 1 << next;
            cur = next;
        }
        return new TspResult(order, best, name(), true, work);
    }

    private double f(int mask, int last) {
        if (mask == full) {
            if (p.returnToStart()) {
                return d[last][p.start()];
            }
            return (p.end() == -1 || last == p.end()) ? 0 : INF;
        }
        if (memo[mask][last] != UNKNOWN) {
            return memo[mask][last];
        }

        double best = INF;
        int bestNext = -1;
        for (int next = 0; next < n; next++) {
            int bit = 1 << next;
            if ((mask & bit) != 0) {
                continue;
            }
            if (next == p.end() && (mask | bit) != full) {
                continue;
            }
            work++;
            double c = d[last][next] + f(mask | bit, next);
            if (c < best) {
                best = c;
                bestNext = next;
            }
        }
        memo[mask][last] = best;
        choice[mask][last] = bestNext;
        return best;
    }

    @Override
    public String name() {
        return "bitmask-dp-top-down";
    }
}
