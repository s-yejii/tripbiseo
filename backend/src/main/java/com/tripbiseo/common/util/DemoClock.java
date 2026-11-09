package com.tripbiseo.common.util;

/**
 * 시연 시계: 시연 계정의 요청을 처리하는 동안만 Geo.now()를 앞(뒤)으로 옮겨 줍니다.
 * 요청이 들어올 때 DemoClockInterceptor가 그 계정의 차이(초)를 넣고, 요청이 끝나면 지웁니다.
 * 다른 계정·서버 안 예약 작업은 값이 없어서 진짜 시각 그대로입니다.
 */
public final class DemoClock {

    // ThreadLocal: 요청 하나를 처리하는 스레드마다 따로 값을 들고 있는 칸 (다른 사람 요청과 섞이지 않음)
    private static final ThreadLocal<Long> OFFSET_SECONDS = new ThreadLocal<>();

    private DemoClock() {}

    /** 이번 요청에서 쓸 차이(초)를 넣음 */
    public static void set(long offsetSeconds) {
        OFFSET_SECONDS.set(offsetSeconds);
    }

    /** 요청이 끝나면 지움 (스레드는 다음 요청에 다시 쓰여서 꼭 지워야 함) */
    public static void clear() {
        OFFSET_SECONDS.remove();
    }

    /** 이번 요청의 차이(초). 시연 시계를 안 쓰면 0 */
    public static long offsetSeconds() {
        Long offset = OFFSET_SECONDS.get();
        return offset == null ? 0 : offset;
    }
}
