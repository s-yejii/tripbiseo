package com.tripbiseo.trip.service;

import static com.tripbiseo.common.exception.ApiException.fail;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

import com.tripbiseo.common.exception.ApiException;
import com.tripbiseo.common.external.Route;
import com.tripbiseo.common.external.RouteClient;
import com.tripbiseo.common.util.Geo;
import com.tripbiseo.trip.dto.CheckResult;
import com.tripbiseo.trip.dto.CheckedStop;
import com.tripbiseo.trip.dto.FixedCheck;
import com.tripbiseo.trip.dto.Leg;
import com.tripbiseo.trip.dto.OpenInfo;
import com.tripbiseo.trip.dto.Place;
import com.tripbiseo.trip.dto.PlanCheckResponse;
import com.tripbiseo.trip.dto.Stop;
import com.tripbiseo.trip.dto.Trip;

import lombok.RequiredArgsConstructor;

/**
 * ② 하루 일정 점검: 실제 이동 시간(RouteClient) + 영업시간·휴무(PlaceService) + 고정 일정 여유.
 * 그날 칸만 보낸 순서대로 계산하고, time이 비어 있으면 앞 일정에 이어서 정해요.
 * 예전 코드: git show backend-v0:backend/src/main/java/com/tripbiseo/service/PlanService.java (check)
 */
@Service
@RequiredArgsConstructor
public class PlanCheckServiceImpl implements PlanCheckService {

    private static final List<String> DEFAULT_TRANSPORTS = List.of("subway", "bus", "walk");
    private static final int DEFAULT_START_MIN = 600;     // 첫 칸 시각이 없으면 10:00
    private static final int LATEST_START_MIN = 23 * 60 + 30;
    private static final int LATE_ALLOWANCE_MIN = 10;     // 이보다 늦게 도착하면 "늦게 도착해요"

    private final RouteClient routeClient;
    private final PlaceService placeService;

    /** 하루 일정 점검 */
    @Override
    public PlanCheckResponse checkPlan(Trip trip, int day) {
        if (trip == null || trip.getStops() == null || trip.getStops().isEmpty() || trip.getStartDate() == null) {
            throw fail(400, "검증할 일정이 없어요.");
        }
        List<String> transports = trip.getTransports() == null || trip.getTransports().isEmpty() ? DEFAULT_TRANSPORTS : trip.getTransports();
        LocalDate date = LocalDate.parse(trip.getStartDate()).plusDays(day - 1);
        PlanCheckResponse response = new PlanCheckResponse();
        response.setDay(day);
        response.setDate(date.toString());
        CheckResult checks = new CheckResult();
        checks.setMove(true);
        checks.setHours(true);
        checks.setClosed(true);

        Stop previous = null;
        int clock = 0; // 앞 칸을 떠나는 시각(분)
        for (Stop stop : trip.getStops()) {
            boolean isSkipped = "skipped".equals(stop.getState()); // 여행 중 뺀 칸은 점검에서 빼요 (11/04)
            if (stop.getDay() == null || stop.getDay() != day || isSkipped) {
                continue;
            }
            Leg leg = previous == null ? null : findLeg(previous, stop, transports);
            int arrive = getArrive(stop, leg, clock);
            if (stop.getTime() == null) {
                stop.setTime(Geo.toHhmm(Math.min(arrive, LATEST_START_MIN))); // 비어 있으면 앞 일정에 이어서
            }
            CheckedStop checked = toCheckedStop(stop, arrive, leg);
            checked.setIssue(findIssue(stop, date, arrive, checks));
            response.getStops().add(checked);
            if (leg != null) {
                response.getLegs().add(leg);
            }
            setFixedIfFirst(response, stop, arrive);
            clock = getStartMinutes(stop, arrive) + (stop.getStay() == null ? 0 : stop.getStay());
            previous = stop;
        }
        finishResponse(response, checks);
        return response;
    }

    // ---------------------------------------------------------------- 도우미

    /** 앞 칸 → 이 칸 실제 길찾기 (taxi는 Leg에서 car) */
    private Leg findLeg(Stop from, Stop to, List<String> transports) {
        Route route = routeClient.getRoute(from.getLat(), from.getLng(), to.getLat(), to.getLng(), to.getMode(), transports, true);
        Leg leg = new Leg();
        leg.setMinutes(route.getMinutes());
        leg.setMeters(route.getMeters());
        leg.setWalkMinutes(route.getWalkMinutes());
        leg.setMode(route.getMode());
        leg.setSource(route.getSource());
        leg.setDetail(route.getDetail());
        leg.setCoords(route.getCoords());
        return leg;
    }

    /** 예상 도착: 첫 칸은 계획 시각, 그다음은 앞 칸 떠난 시각 + 이동. 고정 일정이 아니면 일찍 와도 계획 시각에 시작 */
    private int getArrive(Stop stop, Leg leg, int clock) {
        int planned = stop.getTime() == null ? DEFAULT_START_MIN : Geo.toMinutes(stop.getTime());
        if (leg == null) {
            return planned;
        }
        int byMove = clock + leg.getMinutes();
        boolean isFixed = Boolean.TRUE.equals(stop.getFixed());
        return isFixed || stop.getTime() == null ? byMove : Math.max(byMove, planned);
    }

    /** 칸에서 시작하는 시각: 고정 일정은 일찍 와도 계획 시각에 시작 */
    private int getStartMinutes(Stop stop, int arrive) {
        return Boolean.TRUE.equals(stop.getFixed()) ? Math.max(arrive, Geo.toMinutes(stop.getTime())) : arrive;
    }

    /** 문제 한 줄 (뒤에 맞는 것이 앞 것을 덮어씀: 시간 문제 → 휴무·영업시간). 검사 결과도 같이 바꿈 */
    private String findIssue(Stop stop, LocalDate date, int arrive, CheckResult checks) {
        String issue = findTimeIssue(stop, date, arrive);
        if (issue != null) {
            checks.setMove(false);
        }
        Place place = findPlace(stop.getPlaceId());
        if (place == null) {
            return issue;
        }
        int stay = stop.getStay() == null ? 0 : stop.getStay();
        OpenInfo open = placeService.getOpenInfo(place, date, arrive, stay);
        if (open.getClosedReason() != null && open.getClosedReason().startsWith("오늘 휴무")) {
            checks.setClosed(false);
            return "이날 휴무예요 (" + open.getClosedReason().replace("오늘 휴무 (", "").replace(")", "") + ")";
        }
        if (Boolean.FALSE.equals(open.getOpen())) {
            checks.setHours(false);
            return Geo.toHhmm(arrive) + "에는 영업 시간이 아니에요";
        }
        if (open.getMinutesLeft() != null && open.getMinutesLeft() < stay) {
            checks.setHours(false);
            return open.getCloseAt() + "에 문을 닫아 " + open.getMinutesLeft() + "분밖에 못 머물러요";
        }
        return issue;
    }

    /** 시간 문제: 오늘 일정인데 이미 지난 시각 / 계획보다 10분 넘게 늦게 도착 */
    private String findTimeIssue(Stop stop, LocalDate date, int arrive) {
        int planned = Geo.toMinutes(stop.getTime());
        int stay = stop.getStay() == null ? 0 : stop.getStay();
        boolean isToday = date.equals(Geo.now().toLocalDate());
        boolean isFixed = Boolean.TRUE.equals(stop.getFixed());
        if (isToday && !isFixed && planned + stay < Geo.nowMinutes()) {
            return "이미 지난 시각이에요 (지금 " + Geo.toHhmm(Geo.nowMinutes()) + ")";
        }
        if (arrive > planned + LATE_ALLOWANCE_MIN) {
            return "계획보다 " + (arrive - planned) + "분 늦게 도착해요";
        }
        return null;
    }

    /** 그날 첫 고정 일정이면 fixed·slackMin 채움 */
    private void setFixedIfFirst(PlanCheckResponse response, Stop stop, int arrive) {
        if (!Boolean.TRUE.equals(stop.getFixed()) || response.getFixed() != null) {
            return;
        }
        FixedCheck fixed = new FixedCheck();
        fixed.setName(stop.getName());
        fixed.setTime(stop.getTime());
        fixed.setArrive(Geo.toHhmm(arrive));
        fixed.setSlack(Geo.toMinutes(stop.getTime()) - arrive);
        response.setFixed(fixed);
        response.setSlackMin(fixed.getSlack());
    }

    /** 마지막 도착, 지각 여부, 전체 ok */
    private void finishResponse(PlanCheckResponse response, CheckResult checks) {
        List<CheckedStop> stops = response.getStops();
        response.setFinalArrive(stops.isEmpty() ? null : stops.get(stops.size() - 1).getArrive());
        if (response.getSlackMin() != null && response.getSlackMin() < 0) {
            checks.setMove(false);
        }
        response.setChecks(checks);
        response.setOk(checks.isMove() && checks.isHours() && checks.isClosed());
    }

    /** DB 장소 (없거나 지워졌으면 null → 영업 검사 건너뜀) */
    private Place findPlace(String placeId) {
        if (placeId == null) {
            return null;
        }
        try {
            return placeService.getPlace(placeId);
        } catch (ApiException e) {
            return null;
        }
    }

    /** 보낸 칸 + 예상 도착 + 구간(coords 뺀 것) */
    private CheckedStop toCheckedStop(Stop stop, int arrive, Leg leg) {
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
        checked.setArrive(Geo.toHhmm(arrive));
        checked.setLeg(leg == null ? null : withoutCoords(leg));
        return checked;
    }

    /** stops[].leg는 coords를 빼서 보냄 (legs[]에만 경로선) */
    private Leg withoutCoords(Leg leg) {
        Leg copy = new Leg();
        copy.setMinutes(leg.getMinutes());
        copy.setMeters(leg.getMeters());
        copy.setWalkMinutes(leg.getWalkMinutes());
        copy.setMode(leg.getMode());
        copy.setSource(leg.getSource());
        copy.setDetail(leg.getDetail());
        return copy;
    }
}
