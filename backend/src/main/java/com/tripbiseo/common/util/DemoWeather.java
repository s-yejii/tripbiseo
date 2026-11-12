package com.tripbiseo.common.util;

/**
 * 시연 비 예보: 시연 계정이 개발자 모드에서 "13:00부터 비"를 넣으면 그 계정의 요청을 처리하는 동안만 기상청 예보 대신 씀 (11/06).
 * DemoClock과 같은 방식으로 DemoClockInterceptor가 요청마다 넣고 지웁니다.
 */
public final class DemoWeather {

    // ThreadLocal: 요청 하나를 처리하는 스레드마다 따로 값을 들고 있는 칸 (다른 사람 요청과 섞이지 않음)
    private static final ThreadLocal<String> RAIN_FROM = new ThreadLocal<>();

    /** 시연 중이지만 비를 안 넣었을 때: 진짜 기상청 예보 대신 "비 없음"으로 봄 (11/08) */
    public static final String DRY = "";

    private DemoWeather() {}

    /** 이번 요청에서 쓸 비 시작 시각 "13:00" */
    public static void set(String rainFrom) {
        RAIN_FROM.set(rainFrom);
    }

    /** 요청이 끝나면 지움 */
    public static void clear() {
        RAIN_FROM.remove();
    }

    /** 이번 요청의 시연 비 시작 시각. 안 넣었으면 null (진짜 예보를 씀) */
    public static String rainFrom() {
        return RAIN_FROM.get();
    }
}
