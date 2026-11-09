package com.tripbiseo.plan.service;

import java.time.LocalDate;
import java.util.List;

import com.tripbiseo.plan.dto.DayWeather;

/** ③ 여행 날짜 날씨 + 기온별 옷차림 (일정 완성 화면) */
public interface TripWeatherService {

    /** 여행 날짜마다 날씨·옷차림 (예보 범위 밖인 날은 available=false) */
    List<DayWeather> getWeatherList(double lat, double lng, LocalDate startDate, LocalDate endDate);
}
