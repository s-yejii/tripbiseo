package com.tripbiseo.trip.service;

import static com.tripbiseo.common.exception.ApiException.fail;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

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
    private static final String STAY_CATEGORY = "숙소"; // 일정 만들기가 넣는 숙소 칸의 분류
    private static final int DEFAULT_START_MIN = 600;     // 첫 칸 시각이 없으면 10:00
    private static final int LATEST_START_MIN = 23 * 60 + 30;
    private static final int LATE_ALLOWANCE_MIN = 10;     // 이보다 늦게 도착하면 "늦게 도착해요"
    private static final String STAY_KIND = "stay";       // DB 장소 종류 코드: 숙박 (PlaceMapper kindCase)
    // Pattern: 늘 열려 있어 영업시간이 없는 곳의 이름 (공원·거리·광장… 또는 "청계천"처럼 천·강으로 끝남) → "영업시간 확인 전" 안내를 안 띄움
    private static final Pattern ALWAYS_OPEN_NAME = Pattern.compile("공원|거리|골목|마을|광장|한강|산책|둘레길|숲|나들길|[천강]$");
    private static final String TOUR_KIND = "tour";        // DB 장소 종류 코드: 관광지·축제·레포츠
    private static final String MOUNTAIN_END = "산";       // 관광지 이름이 이걸로 끝나면 산 (관악산) → 늘 열린 곳

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
        Stop start = findStartPoint(trip, day); // 첫 장소 앞 길안내: 1일차는 출발지, 그다음 날은 전날 숙소 (없으면 null)
        for (Stop stop : trip.getStops()) {
            boolean isSkipped = "skipped".equals(stop.getState()); // 여행 중 뺀 칸은 점검에서 빼요 (11/04)
            if (stop.getDay() == null || stop.getDay() != day || isSkipped) {
                continue;
            }
            Leg leg = previous == null ? null : findLeg(previous, stop, transports);
            int arrive = getArrive(stop, leg, clock);
            if (previous == null && start != null) {
                leg = findLeg(start, stop, transports); // 첫 장소는 계획 시각 그대로, 길안내만 붙임 (지도 경로선 legs[]에는 안 넣음)
                leg.setFromName(start.getName());
            }
            if (stop.getTime() == null) {
                stop.setTime(Geo.toHhmm(Math.min(arrive, LATEST_START_MIN))); // 비어 있으면 앞 일정에 이어서
            }
            CheckedStop checked = toCheckedStop(stop, arrive, leg);
            checked.setIssue(findIssue(stop, checked, date, arrive, checks));
            response.getStops().add(checked);
            if (leg != null && previous != null) {
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

    /** 그날 첫 장소 앞에서 출발하는 곳: 1일차는 고른 출발지, 2일차부터는 전날 마지막 칸이 숙소면 그 숙소 (11/09 ② 찬기 OK) */
    private Stop findStartPoint(Trip trip, int day) {
        if (day == 1) {
            if (trip.getOriginLat() == null || trip.getOriginLng() == null) {
                return null;
            }
            Stop origin = new Stop();
            origin.setName(trip.getOriginName() == null ? "출발지" : trip.getOriginName());
            origin.setLat(trip.getOriginLat());
            origin.setLng(trip.getOriginLng());
            return origin;
        }
        Stop last = null;
        for (Stop stop : trip.getStops()) {
            boolean isSkipped = "skipped".equals(stop.getState());
            if (stop.getDay() != null && stop.getDay() == day - 1 && !isSkipped) {
                last = stop;
            }
        }
        return last != null && STAY_CATEGORY.equals(last.getCategory()) ? last : null;
    }

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

    /**
     * 문제 한 줄 (뒤에 맞는 것이 앞 것을 덮어씀: 시간 문제 → 휴무·영업시간). 검사 결과도 같이 바꿈.
     * checked에는 네이버 주소와 "영업시간 확인 전"(hoursUnknown)을 채움. hoursUnknown은 문제 줄·검사 결과를 바꾸지 않음
     */
    private String findIssue(Stop stop, CheckedStop checked, LocalDate date, int arrive, CheckResult checks) {
        String issue = findTimeIssue(stop, date, arrive);
        if (issue != null) {
            checks.setMove(false);
        }
        Place place = findPlace(stop.getPlaceId());
        if (place == null) {
            // DB에 없는 장소(카카오 검색 등)는 영업시간을 알 길이 없어서 "확인 전" 대상, 네이버는 이름·주소 검색
            checked.setNaverUrl(placeService.getNaverUrl(stop.getName(), stop.getAddress()));
            checked.setHoursUnknown(needsHours(stop, null));
            return issue;
        }
        checked.setNaverUrl(findNaverUrl(place));
        int stay = stop.getStay() == null ? 0 : stop.getStay();
        OpenInfo open = placeService.getOpenInfo(place, date, arrive, stay);
        if (open.getClosedReason() != null && open.getClosedReason().startsWith("오늘 휴무")) {
            checks.setClosed(false);
            return "이날 휴무예요 (" + open.getClosedReason().replace("오늘 휴무 (", "").replace(")", "") + ")";
        }
        checked.setHoursUnknown(open.getOpen() == null && needsHours(stop, place)); // 영업시간을 모름 (휴무는 위에서 이미 걸러짐)
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

    /**
     * 영업시간을 알아야 하는 칸인지: 늘 열린 곳(공원·거리 등, 관광지면 "…산")·숙소·고정 일정(예약)은 몰라도 안내하지 않음.
     * place가 null(DB에 없는 장소)이면 칸 이름으로만 보고, 종류를 몰라서 "…산" 규칙은 안 씀
     */
    private boolean needsHours(Stop stop, Place place) {
        String kind = place == null ? null : place.getKind();
        String name = place != null && place.getName() != null ? place.getName().trim() : (stop.getName() == null ? "" : stop.getName().trim());
        boolean isStay = STAY_CATEGORY.equals(stop.getCategory()) || STAY_KIND.equals(kind);
        boolean isFixed = Boolean.TRUE.equals(stop.getFixed());
        boolean isMountain = TOUR_KIND.equals(kind) && name.endsWith(MOUNTAIN_END); // 관광지만: 식당 "부산"·"마산" 같은 이름은 안 걸리게
        boolean isAlwaysOpen = ALWAYS_OPEN_NAME.matcher(name).find() || isMountain;
        return !isStay && !isFixed && !isAlwaysOpen;
    }

    /** 네이버 지도 주소: DB map_url, 없으면 이름·주소로 검색 링크 (AlternativeServiceImpl과 같은 방식) */
    private String findNaverUrl(Place place) {
        boolean hasMapUrl = place.getMapUrl() != null && !place.getMapUrl().isBlank();
        return hasMapUrl ? place.getMapUrl() : placeService.getNaverUrl(place.getName(), place.getAddress());
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
        checked.setHoursUnknown(false); // findIssue가 영업시간을 모르는 칸만 true로 바꿈
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
        copy.setFromName(leg.getFromName());
        return copy;
    }
}
