package com.ssafy.enjoytrip.algo.tsp;

public interface TspSolver {

    TspResult solve(TspProblem problem);

    String name();
}
