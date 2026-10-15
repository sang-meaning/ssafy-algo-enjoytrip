package com.ssafy.enjoytrip.algo.tsp;

import java.util.Arrays;

/**
 * 비트마스크 DP, 상향식(bottom-up). 서비스에서 기본으로 쓰는 정확해 알고리즘.
 *
 * dp[mask][last] = mask 에 들어 있는 지점을 모두 방문하고 last 에서 끝나는 경로의 최소 비용
 *
 * 점화식
 *   dp[mask | (1 << next)][next] = min(dp[mask][last] + dist[last][next])
 *
 * 초기값
 *   출발지가 정해져 있으면 dp[1 << start][start] = 0
 *   아니면 모든 i 에 대해 dp[1 << i][i] = 0  -> 가장 좋은 출발지도 같이 찾는다
 *
 * 시간 O(n^2 * 2^n), 공간 O(n * 2^n)
 * n = 15 에서 약 740만 번 갱신, double + int 테이블 합쳐 약 6MB.
 * n = 20 이면 4억 번, 메모리 약 250MB 라서 웹 요청 한 번에 돌리기엔 무리다.
 */
public class BitmaskDpTsp implements TspSolver {

    /** 이 개수를 넘으면 메모리가 급격히 커지므로 막는다 */
    public static final int MAX_N = 18;

    private static final double INF = Double.POSITIVE_INFINITY;

    @Override
    public TspResult solve(TspProblem p) {
        int n = p.size();
        if (n > MAX_N) {
            throw new IllegalArgumentException("비트마스크 DP 는 " + MAX_N + "개 이하에서만 사용합니다 (입력 " + n + "개)");
        }
        double[][] d = p.dist();
        if (n == 1) {
            return new TspResult(new int[] {0}, 0, name(), true, 1);
        }

        int full = (1 << n) - 1;
        double[][] dp = new double[1 << n][n];
        int[][] parent = new int[1 << n][n];
        for (double[] row : dp) {
            Arrays.fill(row, INF);
        }

        for (int i = 0; i < n; i++) {
            boolean canStart = (p.start() == -1) ? (i != p.end()) : (i == p.start());
            if (canStart) {
                dp[1 << i][i] = 0;
                parent[1 << i][i] = -1;
            }
        }

        long work = 0;
        // mask 를 1 부터 증가시키면 부분집합이 항상 먼저 계산된다 (mask | bit > mask)
        for (int mask = 1; mask <= full; mask++) {
            // 출발지가 고정이면 출발지가 빠진 mask 는 도달할 수 없으니 건너뛴다 (상태 절반 감소)
            if (p.start() != -1 && (mask & (1 << p.start())) == 0) {
                continue;
            }
            for (int last = 0; last < n; last++) {
                if ((mask & (1 << last)) == 0) {
                    continue;
                }
                double cur = dp[mask][last];
                if (cur == INF) {
                    continue;
                }
                for (int next = 0; next < n; next++) {
                    int bit = 1 << next;
                    if ((mask & bit) != 0) {
                        continue;
                    }
                    int nextMask = mask | bit;
                    // 도착지는 마지막에만 방문할 수 있다
                    if (next == p.end() && nextMask != full) {
                        continue;
                    }
                    work++;
                    double cand = cur + d[last][next];
                    if (cand < dp[nextMask][next]) {
                        dp[nextMask][next] = cand;
                        parent[nextMask][next] = last;
                    }
                }
            }
        }

        // 마지막 지점 고르기
        double best = INF;
        int bestLast = -1;
        for (int last = 0; last < n; last++) {
            if (p.end() != -1 && last != p.end()) {
                continue;
            }
            double total = dp[full][last];
            if (p.returnToStart()) {
                total += d[last][p.start()];
            }
            if (total < best) {
                best = total;
                bestLast = last;
            }
        }
        if (bestLast == -1) {
            throw new IllegalStateException("가능한 경로가 없습니다");
        }

        // parent 를 거꾸로 따라가며 순서 복원
        int[] order = new int[n];
        int mask = full;
        int cur = bestLast;
        for (int i = n - 1; i >= 0; i--) {
            order[i] = cur;
            int prev = parent[mask][cur];
            mask ^= (1 << cur);
            cur = prev;
        }
        return new TspResult(order, best, name(), true, work);
    }

    @Override
    public String name() {
        return "bitmask-dp-bottom-up";
    }
}
