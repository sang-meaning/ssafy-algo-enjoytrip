package com.ssafy.enjoytrip.algo.tsp;

/**
 * 입력 크기를 보고 알고리즘을 고른다.
 *  - n <= EXACT_LIMIT : 상향식 비트마스크 DP (최적해)
 *  - 그 이상          : 최근접 이웃 + 2-opt (근사해)
 *
 * 15개 기준 DP 가 수십 ms 안에 끝나고, 하루 일정에 들어가는 장소 수로도 충분하다.
 */
public class AutoTsp implements TspSolver {

    public static final int EXACT_LIMIT = 15;

    private final TspSolver exact = new BitmaskDpTsp();
    private final TspSolver approx = new TwoOptTsp();

    @Override
    public TspResult solve(TspProblem problem) {
        return problem.size() <= EXACT_LIMIT ? exact.solve(problem) : approx.solve(problem);
    }

    @Override
    public String name() {
        return "auto";
    }
}
