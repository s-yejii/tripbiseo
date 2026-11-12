package com.tripbiseo.config;

import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import com.tripbiseo.common.auth.LoginUser;
import com.tripbiseo.common.dev.DevService;
import com.tripbiseo.common.util.DemoClock;
import com.tripbiseo.common.util.DemoWeather;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;

/**
 * 요청이 Controller에 가기 전에: 로그인한 회원이 시연 시계를 맞춰 뒀으면 이번 요청의 Geo.now()를 그 시각으로.
 * HandlerInterceptor: Controller 앞뒤에 끼어드는 Spring 도구 (WebConfig에 등록)
 */
@Component
@RequiredArgsConstructor
public class DemoClockInterceptor implements HandlerInterceptor {

    private final DevService devService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        HttpSession session = request.getSession(false);
        Object memberId = session == null ? null : session.getAttribute(LoginUser.SESSION_KEY);
        if (memberId instanceof Long) {
            long offset = devService.getOffsetSeconds((Long) memberId); // 시연 계정만 값이 있음 (맞출 때 확인함)
            if (offset != 0) {
                DemoClock.set(offset);
            }
            String rainFrom = devService.getRainFrom((Long) memberId);
            if (rainFrom != null) {
                DemoWeather.set(rainFrom);
            } else if (offset != 0) {
                DemoWeather.set(DemoWeather.DRY); // 시연 시계를 쓰는 중인데 시연 비를 안 넣었으면 진짜 예보 대신 "비 없음" (11/08: 1일차에 진짜 비가 섞이지 않게)
            }
        }
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        DemoClock.clear();
        DemoWeather.clear();
    }
}
