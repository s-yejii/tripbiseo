package com.tripbiseo.live.service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import com.tripbiseo.common.util.Geo;
import com.tripbiseo.trip.dto.Stop;
import com.tripbiseo.trip.dto.Trip;

/**
 * ④ 오늘 일정 칸 고르기·시각 계산 도우미.
 * 지금 상황(StatusServiceImpl)과 대안(LiveServiceImpl)이 같은 기준으로 칸을 고르도록 한 곳에 모아 둠.
 * 계산만 하고 DB·외부 API는 부르지 않아요.
 */
@Component 
public class LiveStopFinder {

    private static final int ARRIVE_RADIUS_M = 150; // GPS 도착 기록이 없어도 이 안이면 "지금 있는 곳"

    /** 오늘 칸 전부 (다녀온 칸·뺀 칸 포함) */
    public List<Stop> getTodayStops(Trip trip) {
        // ChronoUnit.DAYS.between: 두 날짜 사이가 며칠인지 (시작일이면 0 → 1일째)
        long today = ChronoUnit.DAYS.between(LocalDate.parse(trip.getStartDate()), Geo.now().toLocalDate()) + 1;
        List<Stop> stops = new ArrayList<>();
        for (Stop stop : trip.getStops()) {
            if (stop.getDay() != null && stop.getDay() == today) {
                stops.add(stop);
            }
        }
        return stops;
    }

    /** 지금 있는 칸: 도착했고 아직 출발 안 한 칸, 없으면 150m 안에서 제일 가까운 todo 칸 (없으면 null) */
    public Stop findCurrentStop(List<Stop> stops, double lat, double lng) {
        Stop nearest = null;
        double nearestMeters = ARRIVE_RADIUS_M;
        for (Stop stop : stops) {
            if (!isTodo(stop) || stop.getLat() == null || stop.getLng() == null) {
                continue;
            }
            if (stop.getArrivedAt() != null && stop.getDepartedAt() == null) {
                return stop; // GPS로 도착 기록된 칸이 제일 확실함
            }
            double meters = Geo.distanceMeters(lat, lng, stop.getLat(), stop.getLng());
            if (meters <= nearestMeters) {
                nearest = stop;
                nearestMeters = meters;
            }
        }
        return nearest;
    }

    /** 다음 남은 칸 (없으면 null) */
    public Stop findNextStop(List<Stop> todayStops, Stop current) {
        for (Stop stop : todayStops) {
            if (isRemaining(stop, current)) {
                return stop;
            }
        }
        return null;
    }

    /** 남은 칸 중 첫 고정 일정 (없으면 null) */
    public Stop findFixedStop(List<Stop> todayStops, Stop current) {
        for (Stop stop : todayStops) {
            if (isRemaining(stop, current) && Boolean.TRUE.equals(stop.getFixed())) {
                return stop;
            }
        }
        return null;
    }

    /** 남은 일정인지: 아직 갈 칸 + 지금 있는 칸이 아님 + 좌표 있음 (API.md "남은 일정") */
    public boolean isRemaining(Stop stop, Stop current) {
        return isTodo(stop) && stop != current && stop.getLat() != null && stop.getLng() != null;
    }

    /** 아직 갈 칸인지 (done·skipped가 아님) */
    public boolean isTodo(Stop stop) {
        return "todo".equals(stop.getState());
    }

    /** 지난 일정: 고정 일정이 아니고, 도착 기록이 없는데 계획한 머무름까지 이미 끝났어야 하는 칸 */
    public boolean isMissed(Stop stop) {
        if (Boolean.TRUE.equals(stop.getFixed()) || stop.getArrivedAt() != null) {
            return false;
        }
        int plannedEnd = Geo.toMinutes(stop.getTime()) + getStay(stop);
        return plannedEnd < Geo.nowMinutes();
    }

    /** 지금 칸에서 떠날 시각(분) = 도착 시각 + 머무는 시간. 도착 기록이 없으면 계획 시각으로 */
    public int getLeaveMinutes(Stop current) {
        int arrived = Geo.toMinutes(current.getTime());
        if (current.getArrivedAt() != null && current.getArrivedAt().length() >= 16) {
            // "2026-11-01T13:30:00"에서 "13:30" 부분만
            arrived = Geo.toMinutes(current.getArrivedAt().substring(11, 16));
        }
        return arrived + getStay(current);
    }

    /** 칸에서 시작하는 시각: 고정 일정은 일찍 도착해도 계획 시각에 시작 */
    public int getStartMinutes(Stop stop, int arrive) {
        if (Boolean.TRUE.equals(stop.getFixed())) {
            return Math.max(arrive, Geo.toMinutes(stop.getTime()));
        }
        return arrive;
    }

    /** 머무는 시간(분). 없으면 0 */
    public int getStay(Stop stop) {
        return stop.getStay() == null ? 0 : stop.getStay();
    }
}
