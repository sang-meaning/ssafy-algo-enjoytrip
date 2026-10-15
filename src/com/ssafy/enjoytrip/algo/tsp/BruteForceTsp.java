package com.ssafy.enjoytrip.algo.tsp;

/**
 * 완전탐색. 가능한 모든 방문 순서를 백트래킹으로 만들어 본다.
 *
 * 시간 O(n!), 공간 O(n). n = 10 이면 약 360만 가지, 12 를 넘으면 실사용이 어렵다.
 * DP 결과가 맞는지 검증하고 성능을 비교하는 기준(baseline)으로만 쓴다.
 */
public class BruteForceTsp implements TspSolver {

    private TspProblem p;
    private double[][] d;
    private int n;
    private int[] path;
    private boolean[] used;
    private int[] bestPath;
    private double best;
    private long work;

    @Override
    public synchronized TspResult solve(TspProblem problem) {
        p = problem;
        d = problem.dist();
        n = problem.size();
        path = new int[n];
        used = new boolean[n];
        bestPath = null;
        best = Double.POSITIVE_INFINITY;
        work = 0;

        for (int s = 0; s < n; s++) {
            if (p.start() != -1 && s != p.start()) {
                continue;
            }
            if (n > 1 && s == p.end()) {
                continue;
            }
            path[0] = s;
            used[s] = true;
            dfs(1, 0);
            used[s] = false;
        }
        return new TspResult(bestPath, best, name(), true, work);
    }

    private void dfs(int depth, double cost) {
        work++;
        if (depth == n) {
            double total = cost;
            if (p.returnToStart() && n > 1) {
                total += d[path[n - 1]][path[0]];
            }
            if (total < best) {
                best = total;
                bestPath = path.clone();
            }
            return;
        }
        int last = path[depth - 1];
        for (int next = 0; next < n; next++) {
            if (used[next]) {
                continue;
            }
            // 도착지가 정해져 있으면 마지막 자리에만 올 수 있다
            if (next == p.end() && depth != n - 1) {
                continue;
            }
            used[next] = true;
            path[depth] = next;
            dfs(depth + 1, cost + d[last][next]);
            used[next] = false;
        }
    }

    @Override
    public String name() {
        return "brute-force";
    }
}
