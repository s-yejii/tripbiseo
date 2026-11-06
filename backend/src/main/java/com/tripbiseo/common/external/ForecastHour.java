package com.tripbiseo.common.external;

import lombok.Data;

/** 기상청 단기예보 한 시간 (DayForecastClient가 만듦). 값이 안 온 칸은 null */
@Data
public class ForecastHour {
    private String date;  // yyyyMMdd
    private int hour;     // 0~23
    private Double tmp;   // 기온(℃)
    private Double tmn;   // 그날 최저기온(℃), 06시 칸에만 옴
    private Double tmx;   // 그날 최고기온(℃), 15시 칸에만 옴
    private Double reh;   // 습도(%)
    private Integer sky;  // 하늘 1 맑음 · 3 구름많음 · 4 흐림
    private Integer pop;  // 강수확률(%)
    private Integer pty;  // 강수형태 0 없음 · 1 비 · 2 비/눈 · 3 눈 · 4 소나기
}
