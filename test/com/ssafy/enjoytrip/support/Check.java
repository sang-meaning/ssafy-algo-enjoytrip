package com.ssafy.enjoytrip.support;

/**
 * JUnit 없이 테스트를 돌리기 위한 간단한 검사 도구.
 * 각 테스트 클래스의 main 에서 쓰고, 마지막에 Check.done() 을 호출한다.
 */
public final class Check {

    private static int passed;
    private static int failed;

    private Check() {
    }

    public static void isTrue(boolean cond, String message) {
        if (cond) {
            passed++;
        } else {
            failed++;
            System.out.println("[실패] " + message);
        }
    }

    public static void equals(Object expected, Object actual, String message) {
        boolean same = (expected == null) ? actual == null : expected.equals(actual);
        isTrue(same, message + " (기대값 " + expected + ", 실제값 " + actual + ")");
    }

    public static void near(double expected, double actual, double tolerance, String message) {
        isTrue(Math.abs(expected - actual) <= tolerance,
                message + " (기대값 " + expected + ", 실제값 " + actual + ")");
    }

    public static void throwsException(Runnable r, String message) {
        try {
            r.run();
            isTrue(false, message + " (예외가 발생하지 않음)");
        } catch (RuntimeException e) {
            passed++;
        }
    }

    /** 결과를 출력하고 실패가 있으면 종료 코드 1 로 끝낸다. */
    public static void done(String suite) {
        System.out.println(suite + ": 통과 " + passed + ", 실패 " + failed);
        if (failed > 0) {
            System.exit(1);
        }
    }
}
