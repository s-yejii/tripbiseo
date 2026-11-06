package com.tripbiseo.common.dev;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.tripbiseo.common.auth.LoginUser;

import lombok.RequiredArgsConstructor;

/** 개발자 모드 (③, 11/06): 시연 계정만 쓰는 시연 시계 */
@RestController
@RequestMapping("/api/dev")
@RequiredArgsConstructor
public class DevController {

    private final DevService devService;
    private final LoginUser loginUser;

    /** 시연 계정인지 + 시연 시계 상태 */
    @GetMapping("/status")
    public DevStatus getStatus() {
        return devService.getStatus(loginUser.id());
    }

    /** 시연 시계를 그 날짜·시각으로 맞춤 */
    @PostMapping("/clock")
    public DevStatus setClock(@RequestBody ClockRequest request) {
        return devService.setClock(loginUser.id(), request);
    }

    /** 시연 시계 빨리 감기 */
    @PostMapping("/clock/forward")
    public DevStatus forwardClock(@RequestBody ForwardRequest request) {
        return devService.forwardClock(loginUser.id(), request.getMinutes());
    }

    /** 시연 비 예보 넣기 { "from": "13:00" } */
    @PostMapping("/rain")
    public DevStatus setRain(@RequestBody RainRequest request) {
        return devService.setRain(loginUser.id(), request.getFrom());
    }

    /** 시연 비 예보 빼기 */
    @DeleteMapping("/rain")
    public DevStatus clearRain() {
        return devService.clearRain(loginUser.id());
    }

    /** 진짜 시각으로 되돌리기 */
    @DeleteMapping("/clock")
    public DevStatus resetClock() {
        return devService.resetClock(loginUser.id());
    }
}
