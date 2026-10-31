package com.tripbiseo.live.dto;

import java.util.ArrayList;
import java.util.List;

import lombok.Data;

/** 오늘 지금 이후 비·눈(또는 강수확률 60% 이상) 첫 시간 */
@Data
public class WeatherInfo {
    private String time;   // HH:00
    private int pop;       // 강수확률(%)
    private boolean snow;
    private List<String> outdoor = new ArrayList<>(); // 남은 야외 일정 이름
}
