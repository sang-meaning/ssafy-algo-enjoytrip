package com.ssafy.enjoytrip.algo.tsp;

/**
 * 근사 알고리즘: 최근접 이웃(Nearest Neighbor)으로 초기 경로를 만들고 2-opt 로 다듬는다.
 * DP 를 쓰기 어려운 큰 입력에서 사용한다. 최적해는 보장하지 않는다.
 *
 * 1) 최근접 이웃: 현재 위치에서 가장 가까운 미방문 지점으로 이동. O(n^2)
 *    출발지가 자유면 모든 출발 후보로 한 번씩 만들어 보고 가장 짧은 것을 고른다. O(n^3)
 * 2) 2-opt: 경로의 구간 [i, j] 를 뒤집었을 때 비용이 줄면 반영한다. 더 줄일 수 없을 때까지 반복.
 *    비대칭 행렬에서도 맞게 동작하도록 뒤집은 뒤 전체 비용을 다시 계산한다.
 *    한 번 훑는 데 O(n^3), 보통 몇 번 안에 수렴한다.
 *
 * 출발지/도착지가 고정이면 그 자리는 뒤집기 범위에서 뺀다.
 */
public class TwoOptTsp implements TspSolver {

    private static final double EPS = 1e-9;

    @Override
    public TspResult solve(TspProblem p) {
        int n = p.size();
        if (n == 1) {
            return new TspResult(new int[] {0}, 0, name(), false, 1);
        }

        int[] best = null;
        double bestCost = Double.POSITIVE_INFINITY;
        for (int s = 0; s < n; s++) {
            boolean canStart = (p.start() == -1) ? (s != p.end()) : (s == p.start());
            if (!canStart) {
                continue;
            }
            int[] tour = nearestNeighbor(p, s);
            double c = p.cost(tour);
            if (c < bestCost) {
                bestCost = c;
                best = tour;
            }
        }

        long work = 0;
        int lo = (p.start() != -1 || p.returnToStart()) ? 1 : 0;
        int hi = (p.end() != -1) ? n - 2 : n - 1;

        boolean improved = true;
        while (improved) {
            improved = false;
            for (int i = lo; i < hi; i++) {
                for (int j = i + 1; j <= hi; j++) {
                    work++;
                    reverse(best, i, j);
                    double c = p.cost(best);
                    if (c + EPS < bestCost) {
                        bestCost = c;
                        improved = true;
                    } else {
                        reverse(best, i, j); // 원상 복구
                    }
                }
            }
        }
        return new TspResult(best, bestCost, name(), false, work);
    }

    static int[] nearestNeighbor(TspProblem p, int start) {
        int n = p.size();
        double[][] d = p.dist();
        int[] tour = new int[n];
        boolean[] used = new boolean[n];
        tour[0] = start;
        used[start] = true;
        for (int i = 1; i < n; i++) {
            int last = tour[i - 1];
            int pick = -1;
            for (int next = 0; next < n; next++) {
                if (used[next]) {
                    continue;
                }
                // 도착지는 마지막 칸에만 넣는다
                if (next == p.end() && i != n - 1) {
                    continue;
                }
                if (pick == -1 || d[last][next] < d[last][pick]) {
                    pick = next;
                }
            }
            tour[i] = pick;
            used[pick] = true;
        }
        return tour;
    }

    private static void reverse(int[] a, int i, int j) {
        while (i < j) {
            int t = a[i];
            a[i] = a[j];
            a[j] = t;
            i++;
            j--;
        }
    }

    @Override
    public String name() {
        return "nearest-neighbor-2opt";
    }
}
