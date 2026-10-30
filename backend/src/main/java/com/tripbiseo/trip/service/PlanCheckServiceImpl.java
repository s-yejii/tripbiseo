package com.tripbiseo.trip.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

import com.tripbiseo.common.external.RouteClient;
import com.tripbiseo.trip.dto.CheckResult;
import com.tripbiseo.trip.dto.CheckedStop;
import com.tripbiseo.trip.dto.FixedCheck;
import com.tripbiseo.trip.dto.Leg;
import com.tripbiseo.trip.dto.PlanCheckResponse;
import com.tripbiseo.trip.dto.Stop;
import com.tripbiseo.trip.dto.Trip;

import lombok.RequiredArgsConstructor;

/** ② 일정 점검. 지금은 보낸 칸을 그대로 돌려주는 가짜 값이에요 */
@Service
@RequiredArgsConstructor
public class PlanCheckServiceImpl implements PlanCheckService {

    private final RouteClient routeClient;   // TODO: ② 구간마다 routeClient.getRoute(…, isPrecise=true)
    private final PlaceService placeService; // TODO: ② placeId가 있으면 getPlace → getOpenInfo로 영업시간·휴무 검사

    /** 하루 일정 점검 */
    // TODO: ② 일정 점검. 그날 칸만 보낸 순서대로, time이 null이면 앞 일정에 이어서, 지난 시각·지각·휴무·영업 끝 issue 한 줄.
    //       참고: 예전 PlanService.check (backend-v0 태그)
    //       주의: 예전처럼 PlaceService의 static 도우미(openInfo·closedOn)를 바로 부르지 말고 placeService 메서드로.
    //             Stop.mode의 taxi는 Leg에서 car. startDate·stay가 없으면 500 나는 문제는 API.md "정해야 할 것"에 있음
    @Override
    public PlanCheckResponse checkPlan(Trip trip, int day) {
        PlanCheckResponse response = new PlanCheckResponse();
        response.setDay(day);
        response.setDate(trip.getStartDate());
        response.setOk(true);
        CheckResult checks = new CheckResult();
        checks.setMove(true);
        checks.setHours(true);
        checks.setClosed(true);
        response.setChecks(checks);

        List<CheckedStop> stops = new ArrayList<>();
        Stop previous = null;
        for (Stop stop : trip.getStops()) {
            if (stop.getDay() != null && stop.getDay() != day) {
                continue;
            }
            CheckedStop checked = toChecked(stop);
            if (previous != null) {
                checked.setLeg(sampleLeg(false));
                response.getLegs().add(sampleLeg(true));
            }
            stops.add(checked);
            previous = stop;
        }
        response.setStops(stops);
        setFixedAndFinal(response, stops);
        return response;
    }

    // 가짜 값: 고정 일정 여유 0분, 마지막 도착 = 마지막 칸 계획 시각
    private static void setFixedAndFinal(PlanCheckResponse response, List<CheckedStop> stops) {
        if (stops.isEmpty()) {
            return;
        }
        response.setFinalArrive(stops.get(stops.size() - 1).getArrive());
        for (CheckedStop stop : stops) {
            if (Boolean.TRUE.equals(stop.getFixed())) {
                FixedCheck fixed = new FixedCheck();
                fixed.setName(stop.getName());
                fixed.setTime(stop.getTime());
                fixed.setArrive(stop.getArrive());
                fixed.setSlack(0);
                response.setFixed(fixed);
                response.setSlackMin(0);
                return;
            }
        }
    }

    private static CheckedStop toChecked(Stop stop) {
        CheckedStop checked = new CheckedStop();
        checked.setId(stop.getId());
        checked.setDay(stop.getDay());
        checked.setPlaceId(stop.getPlaceId());
        checked.setName(stop.getName());
        checked.setCategory(stop.getCategory());
        checked.setLat(stop.getLat());
        checked.setLng(stop.getLng());
        checked.setTime(stop.getTime());
        checked.setStay(stop.getStay());
        checked.setFixed(stop.getFixed());
        checked.setMode(stop.getMode());
        checked.setArrive(stop.getTime());
        return checked;
    }

    // ---------------------------------------------------------------- 가짜 값 (진짜 로직을 만들면 지우기)
    private static Leg sampleLeg(boolean hasCoords) {
        Leg leg = new Leg();
        leg.setMinutes(18);
        leg.setMeters(2100);
        leg.setMode("transit");
        leg.setSource("estimate");
        if (hasCoords) {
            List<double[]> coords = new ArrayList<>();
            coords.add(new double[] {37.57, 126.97});
            coords.add(new double[] {37.56, 126.98});
            leg.setCoords(coords);
        }
        return leg;
    }
}
