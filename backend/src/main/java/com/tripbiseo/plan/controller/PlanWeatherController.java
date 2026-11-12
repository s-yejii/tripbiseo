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

import com.tripbiseo.common.auth.LoginUser;
import com.tripbiseo.common.dev.DevService;
import com.tripbiseo.plan.service.DemoScenario;

import lombok.RequiredArgsConstructor;

/** ③ 여행 날짜 날씨 + 옷차림 (11/06). PlanController와 겹치지 않게 따로 둔 컨트롤러 */
@RestController
@RequestMapping("/api/plans")
@RequiredArgsConstructor
public class PlanWeatherController {

    private final TripWeatherService tripWeatherService;
    private final DevService devService;     // 시연 계정인지 (11/08)
    private final DemoScenario demoScenario; // 시연 계정은 시나리오 날씨 (1일차 맑음, 2일차 13:00부터 비)
    private final LoginUser loginUser;

    /** 여행 날짜마다 기온·습도·하늘·비 시작 시각 + 옷차림. 예: /api/plans/weather?lat=37.52&lng=126.92&startDate=2026-11-07&endDate=2026-11-08 */
    @GetMapping("/weather")
    public List<DayWeather> getWeatherList(@RequestParam double lat, @RequestParam double lng,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        List<DayWeather> list = tripWeatherService.getWeatherList(lat, lng, startDate, endDate);
        if (devService.isDemoAccount(loginUser.id())) {
            demoScenario.applyWeather(list); // 일정 짤 때 "2일차 비 예보" 안내와 날씨 카드가 같은 말을 하게
        }
        return list;
    }
}
