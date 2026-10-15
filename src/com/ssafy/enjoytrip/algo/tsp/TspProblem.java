package com.ssafy.enjoytrip.algo.tsp;

/**
 * TSP 입력.
 *
 * 여행 경로는 보통 출발지로 돌아오지 않으므로 기본은 "경로형"(path) TSP 이다.
 *  - start  : 출발지 인덱스. -1 이면 어디서 출발해도 된다 (가장 좋은 출발지를 같이 찾는다)
 *  - end    : 도착지 인덱스. -1 이면 자유
 *  - returnToStart : true 면 마지막 지점에서 출발지로 돌아오는 비용까지 더한다 (순회형)
 *
 * 순회형일 때 출발지를 지정하지 않으면 0번에서 출발한다. 순회는 어디서 시작해도 비용이 같기 때문.
 */
public final class TspProblem {

    private final double[][] dist;
    private final int n;
    private final int start;
    private final int end;
    private final boolean returnToStart;

    public TspProblem(double[][] dist, int start, int end, boolean returnToStart) {
        this.n = dist.length;
        if (n == 0) {
            throw new IllegalArgumentException("지점이 하나 이상 있어야 합니다");
        }
        for (double[] row : dist) {
            if (row.length != n) {
                throw new IllegalArgumentException("거리 행렬은 n x n 이어야 합니다");
            }
        }
        if (start < -1 || start >= n || end < -1 || end >= n) {
            throw new IllegalArgumentException("출발지/도착지 인덱스가 범위를 벗어났습니다");
        }
        if (returnToStart) {
            // 순회형에서는 도착지가 곧 출발지이므로 따로 받지 않는다
            end = -1;
            if (start == -1) {
                start = 0;
            }
        } else if (n > 1 && start != -1 && start == end) {
            throw new IllegalArgumentException("출발지와 도착지가 같다면 returnToStart 를 사용하세요");
        }
        this.dist = dist;
        this.start = start;
        this.end = end;
        this.returnToStart = returnToStart;
    }

    public static TspProblem path(double[][] dist) {
        return new TspProblem(dist, -1, -1, false);
    }

    public double[][] dist() {
        return dist;
    }

    public int size() {
        return n;
    }

    public int start() {
        return start;
    }

    public int end() {
        return end;
    }

    public boolean returnToStart() {
        return returnToStart;
    }

    /** 방문 순서의 총 비용. 순회형이면 마지막에서 처음으로 돌아오는 비용도 더한다. */
    public double cost(int[] order) {
        double sum = 0;
        for (int i = 0; i + 1 < order.length; i++) {
            sum += dist[order[i]][order[i + 1]];
        }
        if (returnToStart && order.length > 1) {
            sum += dist[order[order.length - 1]][order[0]];
        }
        return sum;
    }

    /** 순서가 모든 지점을 한 번씩 방문하고 출발/도착 조건을 지키는지 확인한다. */
    public boolean isValid(int[] order) {
        if (order.length != n) {
            return false;
        }
        boolean[] seen = new boolean[n];
        for (int v : order) {
            if (v < 0 || v >= n || seen[v]) {
                return false;
            }
            seen[v] = true;
        }
        if (start != -1 && order[0] != start) {
            return false;
        }
        return end == -1 || order[n - 1] == end;
    }
}
