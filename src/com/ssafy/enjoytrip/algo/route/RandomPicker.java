package com.ssafy.enjoytrip.algo.route;

import java.util.Arrays;
import java.util.Random;

/**
 * 후보 중에서 k 개를 중복 없이 무작위로 뽑는다.
 *
 * 부분 Fisher-Yates 셔플: 배열 앞에서부터 k 칸만 남은 구간의 임의 원소와 바꾼다.
 * 전체를 섞지 않으므로 O(k) 번 교환으로 끝나고, 모든 조합이 같은 확률로 나온다.
 * (후보 배열 복사 때문에 공간은 O(M))
 *
 * 후보가 k 개보다 적으면 있는 만큼만 돌려준다.
 */
public final class RandomPicker {

    private RandomPicker() {
    }

    public static int[] pick(int[] candidates, int k, Random rnd) {
        int[] a = candidates.clone();
        int m = a.length;
        int take = Math.min(k, m);
        for (int i = 0; i < take; i++) {
            int j = i + rnd.nextInt(m - i);
            int t = a[i];
            a[i] = a[j];
            a[j] = t;
        }
        return Arrays.copyOf(a, take);
    }
}
