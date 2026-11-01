package com.tripbiseo.live.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import com.tripbiseo.common.exception.ApiException;
import com.tripbiseo.common.external.Forecast;
import com.tripbiseo.common.external.WeatherClient;
import com.tripbiseo.common.util.Geo;
import com.tripbiseo.live.dto.StatusContext;
import com.tripbiseo.live.dto.WeatherInfo;
import com.tripbiseo.trip.dto.Stop;
import com.tripbiseo.trip.service.PlaceService;

import lombok.RequiredArgsConstructor;

/**
 * ④ 지금 상황(status)의 날씨: 오늘 지금 이후 비·눈이 오는 첫 시간 + 그때 야외에 있을 일정.
 * StatusServiceImpl 안에서 쓰는 도우미라 인터페이스 없이 @Component (LiveAlertMaker와 같은 방식).
 * 나중에 대안("비 올 때 실내로 바꾸기")에서도 씀.
 */
@Component
@RequiredArgsConstructor
public class LiveWeatherChecker {

    private static final int RAIN_POP_PERCENT = 60; // 강수확률이 이 이상이면 비 온다고 봄

    private final WeatherClient weatherClient; // 기상청 단기예보 (공통)
    private final PlaceService placeService;   // 실내 판단 (②)

    /** 비 소식 + 비 맞을 야외 일정. 비 소식이 없거나 예보를 못 받으면 null */
    public WeatherInfo check(double lat, double lng, StatusContext context) {
        Forecast forecast;
        try {
            forecast = weatherClient.getForecast(lat, lng);
        } catch (ApiException e) {
            return null; // 기상청 키가 없거나(503) 기상청 오류(502)면 날씨 없이 계속
        }
        Forecast.Hour rainHour = findRainHour(forecast, context.getNow());
        if (rainHour == null) {
            return null;
        }
        int pty = toNumber(rainHour.getPty());
        WeatherInfo weather = new WeatherInfo();
        weather.setTime(rainHour.getTime());
        weather.setPop(toNumber(rainHour.getPop()));
        weather.setSnow(pty == 2 || pty == 3); // 2 비/눈, 3 눈
        weather.setOutdoor(findOutdoorStops(context, Geo.toMinutes(rainHour.getTime())));
        return weather;
    }

    /** 오늘, 아직 안 지난 시간 중 비·눈이 오거나(pty ≠ 0) 강수확률 60% 이상인 첫 시간 (없으면 null) */
    private Forecast.Hour findRainHour(Forecast forecast, int now) {
        String today = Geo.now().toLocalDate().toString().replace("-", ""); // "2026-11-01" → "20261101"
        for (Forecast.Hour hour : forecast.getHours()) {
            // 14:00 예보는 14:59까지라서, 끝(시작 + 59분)이 지났으면 지난 시간
            boolean isPast = Geo.toMinutes(hour.getTime()) + 59 < now;
            if (!today.equals(hour.getDate()) || isPast) {
                continue;
            }
            if (toNumber(hour.getPty()) != 0 || toNumber(hour.getPop()) >= RAIN_POP_PERCENT) {
                return hour;
            }
        }
        return null;
    }

    /**
     * 비 시작 뒤에도 야외에 있을 칸 이름: 지금 칸·남은 칸 중 실내가 아니고 떠날 시각이 비 시작보다 늦은 칸.
     * 남은 칸 = eta 표에 있는 칸 (StatusServiceImpl이 남은 칸만 eta를 계산함)
     */
    private List<String> findOutdoorStops(StatusContext context, int rainStart) {
        List<String> names = new ArrayList<>();
        for (Stop stop : context.getTodayStops()) {
            Integer leaveMinutes = getLeaveMinutes(stop, context);
            if (leaveMinutes == null) {
                continue; // 다녀온 칸·뺀 칸
            }
            if (!placeService.isIndoor(stop.getCategory()) && leaveMinutes > rainStart) {
                names.add(stop.getName());
            }
        }
        return names;
    }

    /** 칸을 떠날 시각(분): 지금 칸이면 context의 떠날 시각, 남은 칸이면 eta + 머묾, 그 밖은 null */
    private Integer getLeaveMinutes(Stop stop, StatusContext context) {
        if (stop == context.getCurrent()) {
            return context.getLeaveAt();
        }
        Integer eta = context.getEtaMap().get(stop.getId());
        if (eta == null) {
            return null;
        }
        int stay = stop.getStay() == null ? 0 : stop.getStay();
        return eta + stay;
    }

    /** 예보 값 글자("70") → 숫자. 비어 있거나 숫자가 아니면 0 */
    private int toNumber(String text) {
        if (text == null || !text.matches("\\d+")) {
            return 0;
        }
        return Integer.parseInt(text);
    }
}
