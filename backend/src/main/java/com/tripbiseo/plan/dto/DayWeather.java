package com.tripbiseo.plan.dto;

import lombok.Data;

/**
 * 여행 하루의 날씨 + 옷차림 (GET /api/plans/weather 한 줄).
 * available=false면 예보 범위(3일 뒤까지) 밖이라 숫자 없이 message만 있어요.
 */
@Data
public class DayWeather {
    private String date;          // yyyy-MM-dd
    private boolean available;    // 예보가 있는 날인지
    private String message;       // 예보가 없을 때 안내 (예: "11/20 날씨는 3일 전부터 보여요")
    private Integer minTemp;      // 최저기온(℃, 반올림)
    private Integer maxTemp;      // 최고기온(℃, 반올림)
    private Integer dayTemp;      // 낮(9~21시) 평균 기온 → 옷차림 기준
    private Integer humidity;     // 낮 평균 습도(%)
    private String sky;           // 맑음 · 구름많음 · 흐림 · 비 · 눈
    private Integer maxPop;       // 낮 최고 강수확률(%)
    private String rainFrom;      // 비·눈이 시작하는 낮 시각 "13:00" (없으면 null)
    private String outfit;        // 옷차림 한 줄 (예: "자켓 · 가디건 · 청바지")
    private String outfitNote;    // 덧붙일 말 (일교차·우산)
    private String source;        // "기상청 단기예보"
}
