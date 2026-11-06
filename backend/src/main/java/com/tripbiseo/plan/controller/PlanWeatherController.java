package com.tripbiseo.plan.controller;

import java.time.LocalDate;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.tripbiseo.plan.dto.DayWeather;
import com.tripbiseo.plan.service.TripWeatherService;

import lombok.RequiredArgsConstructor;

/** ③ 여행 날짜 날씨 + 옷차림 (11/06). PlanController와 겹치지 않게 따로 둔 컨트롤러 */
@RestController
@RequestMapping("/api/plans")
@RequiredArgsConstructor
public class PlanWeatherController {

    private final TripWeatherService tripWeatherService;

    /** 여행 날짜마다 기온·습도·하늘·비 시작 시각 + 옷차림. 예: /api/plans/weather?lat=37.52&lng=126.92&startDate=2026-11-07&endDate=2026-11-08 */
    @GetMapping("/weather")
    public List<DayWeather> getWeatherList(@RequestParam double lat, @RequestParam double lng,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return tripWeatherService.getWeatherList(lat, lng, startDate, endDate);
    }
}
