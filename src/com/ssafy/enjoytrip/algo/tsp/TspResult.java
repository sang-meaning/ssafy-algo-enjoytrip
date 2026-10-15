package com.ssafy.enjoytrip.algo.tsp;

/**
 * TSP 결과.
 *
 * @param order     방문 순서 (입력 인덱스). 순회형이어도 출발지를 끝에 한 번 더 넣지 않는다
 * @param cost      총 비용
 * @param algorithm 사용한 알고리즘 이름
 * @param exact     최적해가 보장되는지 여부
 * @param work      알고리즘이 처리한 상태(또는 탐색 노드) 수. 성능 비교용
 */
public record TspResult(int[] order, double cost, String algorithm, boolean exact, long work) {
}
