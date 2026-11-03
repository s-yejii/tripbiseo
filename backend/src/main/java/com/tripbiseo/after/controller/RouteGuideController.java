package com.tripbiseo.after.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.tripbiseo.after.service.RouteGuideService;
import com.tripbiseo.common.external.RouteGuide;

import lombok.RequiredArgsConstructor;

/** ⑤ 길 안내 (여행 중 화면의 "길 안내") */
@RestController
@RequiredArgsConstructor
public class RouteGuideController {

    private final RouteGuideService routeGuideService;

    /** 지금 위치 → 도착지 길 안내 (경로선 + 단계별 안내) */
    @GetMapping("/api/route")
    public RouteGuide getGuide(@RequestParam(required = false) Double fromLat, @RequestParam(required = false) Double fromLng,
                               @RequestParam(required = false) Double toLat, @RequestParam(required = false) Double toLng,
                               @RequestParam(defaultValue = "walk") String mode) {
        return routeGuideService.getGuide(fromLat, fromLng, toLat, toLng, mode);
    }
}
