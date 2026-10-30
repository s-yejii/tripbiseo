package com.tripbiseo.common.external;

import java.util.ArrayList;
import java.util.List;

import lombok.Data;

/** 기상청 단기예보 결과 (WeatherClient가 만듦). Gemini 날씨 도구도 이 모양 그대로 받음 */
@Data
public class Forecast {
    private String source = "기상청 단기예보";
    private List<Hour> hours = new ArrayList<>();
    private String note = "pop=강수확률(%), pty=강수형태(0 없음 1 비 2 비/눈 3 눈 4 소나기), tmp=기온(℃)";

    /** 한 시간 예보 */
    @Data
    public static class Hour {
        private String date; // yyyyMMdd
        private String time; // HH:00
        private String pop;  // 강수확률(%)
        private String pty;  // 강수형태
        private String tmp;  // 기온(℃)
    }
}
