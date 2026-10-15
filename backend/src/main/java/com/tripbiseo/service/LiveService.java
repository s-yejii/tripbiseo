package com.tripbiseo.service;

import static com.tripbiseo.common.ApiException.fail;

import java.security.Principal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.tripbiseo.common.ApiException;
import com.tripbiseo.common.Geo;
import com.tripbiseo.common.Json;
import com.tripbiseo.common.Maps;
import com.tripbiseo.dto.Place;
import com.tripbiseo.dto.Stop;
import com.tripbiseo.dto.Trip;
import com.tripbiseo.mapper.TripMapper;

/**
 * 여행 중 실시간 재분석
 *  - track       : GPS 저장 + 150m 안이면 도착, 200m 밖이면 출발 기록
 *  - status      : 지금 위치·시각에서 남은 일정을 다시 계산 → 알림(출발 10분 전부터 1분마다 / 출발 시각 초과 / 고정 일정 지각 / 영업 끝·휴무 / 지난 일정)
 *                  + 지금 갈 수 있는 구역 기준 여유(area) + 비·눈 예보(weather)
 *  - assist      : 채팅 말 이해(제미나이) → 추천 / 대안 / 답
 *  - recommend   : 반경 안 평점 상위 3곳 → insert 로 일정에 넣고 뒤 일정 다시 계산
 *  - alternatives: 대안 A·B·C (규칙으로 만들고, 제미나이가 날씨·요청을 보고 다시 고름)
 *  - apply       : 고른 대안을 일정에 반영
 */
@Service
public class LiveService {

    private static final Logger log = LoggerFactory.getLogger(LiveService.class);
    private static final int ARRIVE_M = 150, LEAVE_M = 200;

    private final TripService trips;
    private final TripMapper mapper;
    private final RouteService routes;
    private final PlaceService places;
    private final GeminiService gemini;
    private final WeatherService weather;

    public LiveService(TripService trips, TripMapper mapper, RouteService routes, PlaceService places, GeminiService gemini, WeatherService weather) {
        this.trips = trips;
        this.mapper = mapper;
        this.routes = routes;
        this.places = places;
        this.gemini = gemini;
        this.weather = weather;
    }


    /** 오늘 상황: 지금 있는 일정, 남은 일정(순서대로), 고정 일정 */
    private record Today(Trip trip, int day, int now, Stop current, List<Stop> rest, Stop fixed) {}

    private Today today(Principal user, long id, double lat, double lng) {
        Trip t = trips.get(user, id);
        if (!"ongoing".equals(t.status)) throw fail(400, "진행 중인 여행이 아니에요. 여행 화면에서 '여행 시작'을 눌러 주세요.");
        int day = (int) ChronoUnit.DAYS.between(LocalDate.parse(t.startDate), Geo.now().toLocalDate()) + 1;
        List<Stop> list = t.stops.stream().filter(s -> s.day == day).sorted(Comparator.comparing((Stop s) -> s.time)).toList();
        Stop cur = list.stream().filter(s -> s.arrivedAt != null && s.departedAt == null).findFirst()
                .orElse(list.stream().filter(s -> "todo".equals(s.state) && Geo.meters(lat, lng, s.lat, s.lng) <= ARRIVE_M).findFirst().orElse(null));
        List<Stop> rest = list.stream().filter(s -> "todo".equals(s.state) && s != cur).toList();
        Stop fixed = rest.stream().filter(s -> Boolean.TRUE.equals(s.fixed)).findFirst().orElse(null);
        return new Today(t, day, Geo.nowMin(), cur, rest, fixed);
    }

    /** 남은 일정 계산을 시작할 시각·위치: 지금 있는 일정이 있으면 (도착 + 머무름)에 그곳에서 출발, 없으면 지금 내 위치에서 지금 출발 */
    private record Start(int clock, double lat, double lng, Integer elapsed, Integer leaveAt) {}

    private static Start start(Today d, double lat, double lng) {
        Stop c = d.current();
        if (c == null) return new Start(d.now(), lat, lng, null, null);
        int arrived = c.arrivedAt != null ? minuteOf(c.arrivedAt) : Geo.min(c.time);
        int leaveAt = arrived + c.stay;
        return new Start(Math.max(d.now(), leaveAt), c.lat, c.lng, d.now() - arrived, leaveAt);
    }

    private Place placeOf(Stop s, Map<String, Place> cache) {
        if (s.placeId == null) return null;
        return cache.computeIfAbsent(s.placeId, k -> {
            try {
                return places.find(k);
            } catch (ApiException e) {
                return null;
            }
        });
    }

    // ---------------------------------------------------------------- GPS
    @Transactional
    public Map<String, Object> track(Principal user, long id, double lat, double lng, Double accuracy) {
        Today d = today(user, id, lat, lng);
        Map<String, Object> prev = mapper.lastTrack(id); // 바로 앞 위치 (튀는 GPS 한 점으로 출발 처리하지 않게)
        mapper.insertTrack(id, lat, lng, accuracy);
        for (Stop s : d.trip().stops) {
            double m = Geo.meters(lat, lng, s.lat, s.lng);
            if (s.day == d.day() && s.arrivedAt == null && "todo".equals(s.state) && m <= ARRIVE_M) mapper.markArrived(s.id);
            if (s.arrivedAt != null && s.departedAt == null && m > LEAVE_M && prev != null
                    && Geo.meters(((Number) prev.get("lat")).doubleValue(), ((Number) prev.get("lng")).doubleValue(), s.lat, s.lng) > LEAVE_M)
                mapper.markDeparted(s.id); // 두 번 연속 200m 밖이면 출발
        }
        return Map.of("ok", true);
    }

    // ---------------------------------------------------------------- 지금 상황
    public Map<String, Object> status(Principal user, long id, double lat, double lng) {
        Today d = today(user, id, lat, lng);
        List<String> modes = d.trip().transports;
        LocalDate date = Geo.now().toLocalDate();
        Map<String, Place> cache = new LinkedHashMap<>();
        Map<Long, Integer> eta = new LinkedHashMap<>();
        Map<Long, Map<String, Object>> hours = new LinkedHashMap<>();
        Start st = start(d, lat, lng);
        int clock = st.clock();
        double fromLat = st.lat(), fromLng = st.lng();
        int i = 0;
        Integer firstMove = null;
        for (Stop s : d.rest()) {
            int move = routes.leg(fromLat, fromLng, s.lat, s.lng, s.mode, modes, i++ < 3).minutes(); // 가까운 3구간만 실제 길찾기
            if (firstMove == null) firstMove = move;
            int arrive = Boolean.TRUE.equals(s.fixed) ? Math.max(clock + move, Geo.min(s.time)) : clock + move;
            eta.put(s.id, clock + move);
            hours.put(s.id, PlaceService.openInfo(placeOf(s, cache), date, arrive, s.stay)); // 고정 일정은 일찍 도착해도 계획 시각에 시작하므로 그 시각 기준
            clock = arrive + s.stay;
            fromLat = s.lat;
            fromLng = s.lng;
        }

        Stop f = d.fixed();
        Integer slack = f == null ? null : Geo.min(f.time) - eta.get(f.id);
        Map<String, Object> fixed = f == null ? null : Maps.of("id", f.id, "name", f.name, "time", f.time, "eta", Geo.hhmm(eta.get(f.id)),
                "slackMin", slack, "lat", f.lat, "lng", f.lng);

        // 갈 수 있는지: 지금→장소 + 머무름 + 장소→고정 일정 ≤ 남은 시간
        Map<Long, String> reach = new LinkedHashMap<>();
        List<String> okNames = new ArrayList<>(), tightNames = new ArrayList<>();
        int avail = f == null ? 0 : Geo.min(f.time) - d.now();
        for (Stop s : d.rest()) {
            if (f == null || s == f || Geo.min(s.time) > Geo.min(f.time)) {
                reach.put(s.id, "ok");
                continue;
            }
            int go = routes.leg(lat, lng, s.lat, s.lng, modes, false).minutes(), back = routes.leg(s.lat, s.lng, f.lat, f.lng, modes, false).minutes();
            String r = go + s.stay + back <= avail ? "ok" : go + s.stay / 2 + back <= avail ? "tight" : "no";
            reach.put(s.id, r);
            if (r.equals("ok")) okNames.add(s.name);
            if (r.equals("tight")) tightNames.add(s.name);
        }
        String hint = null;
        if (slack != null && slack < 0 && !tightNames.isEmpty())
            hint = (okNames.isEmpty() ? "" : okNames.get(0) + "은(는) 그대로 갈 수 있어요! ") + tightNames.get(0) + " 시간만 줄이면 " + f.name + "에 맞출 수 있어요.";
        else if (slack != null && slack < 0 && !okNames.isEmpty())
            hint = okNames.get(0) + "은(는) 그대로 갈 수 있어요. 한 곳을 빼면 " + f.name + "에 맞출 수 있어요.";

        // 계획 시각이 지났는데 아직 안 간 곳 (도착 기록 없음, 계획한 머무름까지 끝났어야 하는 곳)
        List<Stop> missed = d.rest().stream().filter(s -> !s.fixed && s.arrivedAt == null && Geo.min(s.time) + s.stay < d.now()).toList();
        // 도착 예상 시각에 문을 닫았거나 오늘 휴무인 곳
        Stop closed = d.rest().stream().filter(s -> Boolean.FALSE.equals(hours.get(s.id).get("open"))).findFirst().orElse(null);

        // 출발 알림: 지금 있는 곳의 출발 시각, 없으면 다음 장소에 계획대로 닿으려면 떠나야 하는 시각
        Map<String, Object> leave = null;
        Stop next = d.rest().isEmpty() ? null : d.rest().get(0);
        if (d.current() != null) {
            int left = st.leaveAt() - d.now();
            if (left <= 10 && left >= -10) // 그 뒤는 '더 머물렀어요' 알림이 이어받음
                leave = Maps.of("stopId", d.current().id, "name", d.current().name, "leaveAt", Geo.hhmm(st.leaveAt()), "minutesLeft", left,
                        "to", next == null ? null : next.name, "atStop", true);
        } else if (next != null && !missed.contains(next)) {
            int leaveAt = Geo.min(next.time) - firstMove;
            int left = leaveAt - d.now();
            if (left <= 10 && left >= -10) // 그 뒤는 '늦게 도착' 알림이 이어받음
                leave = Maps.of("stopId", next.id, "name", next.name, "leaveAt", Geo.hhmm(leaveAt), "minutesLeft", left, "to", next.name, "atStop", false);
        }

        // '지금 갈 수 있는 구역' 기준: 고정 일정(없으면 다음 일정)까지 남는 여유. 화면이 이 여유로 도보 10·20·30분 구역 색을 정함
        Stop target = f != null ? f : next;
        Map<String, Object> area = target == null ? null
                : Maps.of("slackMin", f != null ? slack : Geo.min(next.time) - eta.get(next.id), "basis", target.name, "basisTime", target.time, "lat", lat, "lng", lng);

        List<Map<String, Object>> stops = new ArrayList<>();
        for (Stop s : d.trip().stops) {
            if (s.day != d.day()) continue;
            Map<String, Object> h = hours.get(s.id);
            boolean shut = h != null && Boolean.FALSE.equals(h.get("open"));
            String r = !"todo".equals(s.state) ? "done" : s == d.current() ? "here" : shut ? "closed" : missed.contains(s) ? "missed"
                    : s.fixed ? "fixed" : reach.getOrDefault(s.id, "ok");
            stops.add(Maps.of("id", s.id, "placeId", s.placeId, "name", s.name, "category", s.category, "lat", s.lat, "lng", s.lng, "time", s.time,
                    "stay", s.stay, "fixed", s.fixed, "eta", eta.containsKey(s.id) ? Geo.hhmm(eta.get(s.id)) : s.time, "reach", r,
                    "closeAt", h == null ? null : h.get("closeAt"), "closedReason", h == null ? null : h.get("closedReason"),
                    "closingSoon", h != null && Boolean.TRUE.equals(h.get("closingSoon"))));
        }
        RouteService.Leg toNext = next == null ? null : routes.leg(lat, lng, next.lat, next.lng, next.mode, modes, true);
        Map<String, Object> alert = alert(d, st, slack, eta, hours, missed, closed, leave);
        Integer nextLate = next == null ? null : eta.get(next.id) - Geo.min(next.time);
        boolean replan = !d.rest().isEmpty() && ((slack != null && slack < 0) || closed != null || !missed.isEmpty()
                || (st.elapsed() != null && d.current() != null && st.elapsed() >= d.current().stay)
                || (nextLate != null && nextLate > 10));
        return Maps.of("now", Geo.hhmm(d.now()), "currentStopId", d.current() == null ? null : d.current().id,
                "alert", alert, "leave", leave, "fixed", fixed, "hint", hint, "replan", replan, "area", area, "weather", rain(lat, lng, d),
                "stops", stops, "next", toNext == null ? null : Maps.of("id", next.id, "minutes", toNext.minutes(), "coords", toNext.coords(), "source", toNext.source()));
    }

    /** 비·눈 예보: 오늘 지금 이후 강수 형태가 있거나 강수확률 60% 이상인 첫 시간 + 남은 야외 일정. 기상청 키가 없거나 실패하면 null */
    private Map<String, Object> rain(double lat, double lng, Today d) {
        try {
            String today = Geo.now().toLocalDate().toString().replace("-", "");
            for (Map<String, Object> h : PlanService.maps(weather.forecast(lat, lng).get("hours"))) {
                if (!today.equals(h.get("date")) || Geo.min((String) h.get("time")) + 59 < d.now()) continue;
                int pty = Json.num(h, "pty") == null ? 0 : Json.num(h, "pty").intValue(), pop = Json.num(h, "pop") == null ? 0 : Json.num(h, "pop").intValue();
                if (pty == 0 && pop < 60) continue;
                return Maps.of("time", h.get("time"), "pop", pop, "snow", pty == 2 || pty == 3,
                        "outdoor", d.rest().stream().filter(s -> !PlaceService.isIndoor(s.category)).map(s -> s.name).toList());
            }
        } catch (ApiException e) {
            log.debug("날씨 확인 실패: {}", e.getMessage());
        }
        return null;
    }

    /**
     * 화면 맨 위 알림 한 개. 우선순위: 출발 시각 초과 > 영업 끝/휴무 > 고정 일정 지각 > 출발 10분 전 > 지난 일정 > 고정 여유 10분 이하 > 다음 장소 지각.
     * 출발 10분 전 알림은 key에 남은 분을 넣어 1분마다 새로 울리게 함
     */
    private static Map<String, Object> alert(Today d, Start st, Integer slack, Map<Long, Integer> eta, Map<Long, Map<String, Object>> hours,
            List<Stop> missed, Stop closed, Map<String, Object> leave) {
        Stop c = d.current(), f = d.fixed();
        if (c != null && st.elapsed() != null && st.elapsed() >= c.stay)
            return Maps.of("key", "overdue-" + c.id + "-" + (st.elapsed() - c.stay) / 5, "level", "danger", "title", c.name + "에서 " + (st.elapsed() - c.stay) + "분 더 머물렀어요",
                    "text", "계획한 출발 시각이 지났어요. 지금 위치에서 다시 계산했어요");
        if (closed != null) {
            Map<String, Object> h = hours.get(closed.id);
            return Maps.of("key", "closed-" + closed.id, "level", "danger", "title", closed.name + ": " + h.get("closedReason"),
                    "text", "도착 예상 " + Geo.hhmm(eta.get(closed.id)) + " — '대안 보기'로 다른 곳을 찾아보세요");
        }
        if (slack != null && slack < 0)
            return Maps.of("key", "late-" + f.id + "-" + (slack / 10), "level", "danger", "title", "지금대로면 " + f.name + "에 " + (-slack) + "분 늦어요",
                    "text", "'대안 보기'로 늦지 않는 방법을 확인하세요");
        if (leave != null) {
            int left = ((Number) leave.get("minutesLeft")).intValue();
            boolean atStop = Boolean.TRUE.equals(leave.get("atStop"));
            String title = left > 0 ? left + "분 뒤 출발해야 해요" : left == 0 ? "지금 출발해야 해요" : "출발 시각이 " + (-left) + "분 지났어요";
            String text = atStop ? leave.get("name") + " 출발 " + leave.get("leaveAt") + (leave.get("to") == null ? "" : " → " + leave.get("to"))
                    : leave.get("name") + "에 계획대로 닿으려면 " + leave.get("leaveAt") + "에 출발";
            return Maps.of("key", "leave-" + leave.get("stopId") + "-" + left, "level", left > 0 ? "warn" : "danger", "title", title, "text", text, "repeat", true);
        }
        if (!missed.isEmpty())
            return Maps.of("key", "missed-" + missed.get(0).id, "level", "warn", "title", missed.get(0).name + " 계획 시간이 지났어요",
                    "text", "아직 안 갔다면 '대안 보기'에서 빼거나 바꿀 수 있어요");
        if (slack != null && slack <= 10)
            return Maps.of("key", "tight-" + f.id, "level", "warn", "title", f.name + "까지 여유가 " + slack + "분뿐이에요", "text", "다음 장소는 짧게 들르는 게 좋아요");
        if (!d.rest().isEmpty()) {
            Stop n = d.rest().get(0);
            int late = eta.get(n.id) - Geo.min(n.time);
            if (late > 10) return Maps.of("key", "next-" + n.id + "-" + late / 10, "level", "warn", "title", n.name + "에 " + late + "분 늦게 도착할 것 같아요",
                    "text", "'대안 보기'로 남은 일정을 다시 짤 수 있어요");
        }
        return null;
    }

    // ---------------------------------------------------------------- 채팅
    public Map<String, Object> assist(Principal user, long id, String message, double lat, double lng) {
        if (message == null || message.isBlank() || message.length() > 300) throw fail(400, "메시지를 1~300자로 입력해 주세요.");
        Today d = today(user, id, lat, lng);
        Map<String, Object> intent;
        try {
            intent = gemini.askJson("""
                    여행 중인 사용자가 보낸 말을 분류해. JSON만.
                    남은 일정: %s
                    사용자: "%s"
                    {"intent": "recommend"(근처 장소 추천 요청) | "replan"(일정을 바꿔 달라·비·늦음·실내로) | "question"(그 밖의 질문),
                     "category": "추천일 때 장소 종류 한 단어(카페, 식당, 술집, 디저트 …)", "answer": "question일 때 두 문장 이내 답"}
                    """.formatted(summary(d), message));
        } catch (ApiException e) { // AI가 안 되면 낱말로 판단
            log.info("assist AI 실패 → 규칙: {}", e.getMessage());
            String cat = message.contains("카페") ? "카페" : message.matches(".*(식당|맛집|밥).*") ? "식당" : null;
            intent = Maps.of("intent", cat != null || message.contains("추천") ? "recommend" : message.matches(".*(비|실내|바꿔|다시|늦).*") ? "replan" : "question",
                    "category", cat == null ? "카페" : cat, "answer", "AI 연결이 안 돼서 답을 못 했어요. (" + e.getMessage() + ")");
        }
        return switch (String.valueOf(intent.get("intent"))) {
            case "recommend" -> Maps.of("kind", "ask-radius", "category", PlanService.str(intent.get("category"), "카페"));
            case "replan" -> Maps.of("kind", "alternatives", "data", alternatives(user, id, lat, lng, "request", message));
            default -> Maps.of("kind", "text", "text", PlanService.str(intent.get("answer"), "무엇을 도와드릴까요?"));
        };
    }

    public Map<String, Object> recommend(Principal user, long id, double lat, double lng, int radius, String category) {
        today(user, id, lat, lng);
        if (radius < 100 || radius > 3000) throw fail(400, "반경은 100m~3km예요.");
        int stay = category != null && category.matches(".*(카페|디저트|커피|베이커리).*") ? 40 : 60;
        return places.recommend(lat, lng, radius, category, stay);
    }

    /** 추천 장소를 바로 다음 일정으로 넣고, 뒤 일정 시각을 다시 계산. 고정 일정에 늦으면 사이 일정 머무름을 줄임 */
    @Transactional
    public Map<String, Object> insert(Principal user, long id, String placeId, double lat, double lng) {
        Today d = today(user, id, lat, lng);
        Place p = places.find(placeId);
        Start st = start(d, lat, lng);
        Stop s = new Stop();
        s.tripId = id;
        s.day = d.day();
        s.placeId = p.id;
        s.name = p.name;
        s.category = p.category;
        s.address = p.address;
        s.lat = p.lat;
        s.lng = p.lng;
        s.stay = p.category != null && p.category.contains("카페") ? 40 : 60;
        s.time = Geo.hhmm(st.clock() + routes.leg(st.lat(), st.lng(), p.lat, p.lng, d.trip().transports, true).minutes());
        s.fixed = false;
        s.state = "todo";
        mapper.insertStop(s);

        List<Stop> plan = new ArrayList<>(List.of(s));
        plan.addAll(d.rest());
        Plan r = fit(plan, st.lat(), st.lng(), st.clock(), d.trip().transports, true); // 상태 화면과 같은 출발 시각·위치로 계산
        save(r.stops());
        String msg = p.name + "을(를) 다음 일정으로 넣었어요. "
                + (r.shortened().isEmpty() ? "" : String.join("·", r.shortened()) + " 시간을 줄여 ")
                + (d.fixed() == null ? "이후 일정 시각을 다시 계산했어요." : r.slack() >= 0 ? d.fixed().name + "에는 " + r.slack() + "분 여유가 있어요." : "그래도 " + d.fixed().name + "에 " + (-r.slack()) + "분 늦어요. '대안 보기'를 확인하세요.");
        return Maps.of("trip", trips.get(user, id), "message", msg);
    }

    // ---------------------------------------------------------------- 대안 A·B·C
    public Map<String, Object> alternatives(Principal user, long id, double lat, double lng, String reason, String request) {
        Today d = today(user, id, lat, lng);
        if (d.rest().isEmpty()) throw fail(400, "오늘 남은 일정이 없어요.");
        List<String> modes = d.trip().transports;
        boolean indoor = request != null && request.matches(".*(비|실내|추워|더워|날씨).*");
        LocalDate date = Geo.now().toLocalDate();
        Start st = start(d, lat, lng);
        Map<String, Place> cache = new LinkedHashMap<>();

        // 뺄 곳 고르기: 도착하면 문 닫은 곳 > 계획 시간이 지난 곳 > (비 오면) 야외 > 오래 머무는 곳
        Plan now = chain(copy(d.rest()), st.lat(), st.lng(), st.clock(), modes);
        Map<Long, Boolean> shut = new LinkedHashMap<>();
        for (Stop s : now.stops()) {
            int arrive = Geo.min(s.time) ;
            shut.put(s.id, Boolean.FALSE.equals(PlaceService.openInfo(placeOf(s, cache), date, s.fixed ? Math.max(arrive, d.now()) : arrive, s.stay).get("open")));
        }
        Stop drop = d.rest().stream().filter(s -> !s.fixed).max(Comparator
                .comparing((Stop s) -> Boolean.TRUE.equals(shut.get(s.id)) ? 1 : 0)
                .thenComparing(s -> s.arrivedAt == null && Geo.min(s.time) + s.stay < d.now() ? 1 : 0)
                .thenComparing(s -> indoor && !PlaceService.isIndoor(s.category) ? 1 : 0)
                .thenComparing(s -> s.stay)).orElse(null);

        // 규칙으로 기본 대안 3가지
        List<Map<String, Object>> rule = new ArrayList<>();
        rule.add(shape("A", "시간 단축", "머무는 시간 줄이기", "가는 곳은 그대로, 머무는 시간만 줄여요.", fit(copy(d.rest()), st.lat(), st.lng(), st.clock(), modes, true), d, date, cache));
        if (drop != null) {
            List<Stop> without = copy(d.rest());
            without.removeIf(s -> Objects.equals(s.id, drop.id));
            String why = Boolean.TRUE.equals(shut.get(drop.id)) ? "도착하면 문을 닫은 곳을 빼요." : drop.arrivedAt == null && Geo.min(drop.time) + drop.stay < d.now()
                    ? "계획 시간이 지난 곳을 빼요." : "한 곳을 빼고 나머지는 계획대로 가요.";
            if (!without.isEmpty())
                rule.add(shape("B", "한 곳 빼기", drop.name + " 빼기", why, fit(without, st.lat(), st.lng(), st.clock(), modes, false), d, date, cache));
            Place sub = substitute(drop, indoor, date, Math.max(d.now(), Geo.min(drop.time)));
            if (sub != null) {
                List<Stop> replaced = copy(d.rest());
                replaced.replaceAll(s -> Objects.equals(s.id, drop.id) ? fromPlace(sub, s) : s);
                rule.add(shape("C", "장소 변경", drop.name + " 대신 " + sub.name, (indoor ? "실내" : "가까운") + " 평점 좋은 곳(그 시간에 영업 중)으로 바꿔요.",
                        fit(replaced, st.lat(), st.lng(), st.clock(), modes, true), d, date, cache));
            }
        }
        String weatherNote = null, source = "rule", title = null, subtitle = null;
        List<Map<String, Object>> ai = new ArrayList<>();

        // 제미나이: 상황·요청·날씨(도구)를 보고 대안을 고르거나 새로 짬
        if (gemini.ready()) {
            try {
                int at = st.clock() + 30;
                List<Place> nearby = places.within(lat, lng, 2000, null).stream().filter(p -> !indoor || PlaceService.isIndoor(p.category))
                        .filter(p -> PlaceService.closedOn(p, date) == null && !Boolean.FALSE.equals(PlaceService.openAt(p, date, at))) // 지금 영업하는 곳만
                        .limit(25).toList();
                StringBuilder cand = new StringBuilder();
                nearby.forEach(p -> cand.append(p.id).append(" | ").append(p.name).append(" | ").append(p.category).append(" | ★").append(p.rating).append('\n'));
                Map<String, Object> r = gemini.askJsonWithTools("""
                        여행 중 일정이 틀어졌어. 지금 %s, 위치 (%.5f, %.5f). 이유: %s / 사용자 요청: %s
                        남은 일정(id | 이름 | 분류 | 계획 시각 | 머무는 분 | 고정 여부): %s
                        도착하면 문을 닫은 곳: %s
                        서버가 만든 기본 대안: %s
                        근처 후보(지금 영업 중, placeId | 이름 | 분류 | 평점):
                        %s
                        규칙: 비·날씨 얘기가 있거나 야외 일정이 남았으면 get_weather 도구로 날씨를 먼저 확인해.
                        고정 일정은 꼭 남기고, 문을 닫은 곳은 넣지 마. 장소는 남은 일정 id 또는 후보 placeId로만. 시간은 서버가 다시 계산하니 순서와 머무는 분만 정해.
                        JSON만: {"title": "대안 화면 제목", "subtitle": "한 줄 설명", "weather": "날씨를 봤다면 한 줄, 아니면 null",
                         "alternatives": [{"short": "탭 이름(6자)", "title": "대안 이름", "reason": "왜 좋은지 한 문장", "recommended": true/false,
                           "stops": [{"id": 남은 일정 id 또는 null, "placeId": "후보 id 또는 null", "stay": 분}]}]} (대안 3개, 서로 다르게)
                        """.formatted(Geo.hhmm(d.now()), lat, lng, reason, request == null ? "" : request, summary(d),
                        d.rest().stream().filter(s -> Boolean.TRUE.equals(shut.get(s.id))).map(s -> s.name).toList(),
                        Json.write(rule.stream().map(a -> Maps.of("key", a.get("key"), "title", a.get("title"), "slackMin", a.get("slackMin"))).toList()), cand),
                        List.of(Maps.of("name", "get_weather", "description", "현재 위치의 오늘 시간대별 강수확률·강수형태·기온(기상청 단기예보)",
                                "parameters", Maps.of("type", "object", "properties", Maps.of("lat", Maps.of("type", "number"), "lng", Maps.of("type", "number")),
                                        "required", List.of("lat", "lng")))),
                        (name, args) -> weatherTool(lat, lng));
                for (Map<String, Object> a : PlanService.maps(r.get("alternatives"))) {
                    List<Stop> stops = new ArrayList<>();
                    for (Map<String, Object> x : PlanService.maps(a.get("stops"))) {
                        Stop s = x.get("id") instanceof Number n ? d.rest().stream().filter(o -> o.id == n.longValue()).findFirst().map(Stop::copy).orElse(null)
                                : nearby.stream().filter(p -> p.id.equals(x.get("placeId"))).findFirst().map(p -> fromPlace(p, null)).orElse(null);
                        if (s == null) continue;
                        if (x.get("stay") instanceof Number n && !s.fixed) s.stay = Math.max(10, Math.min(300, n.intValue()));
                        stops.add(s);
                    }
                    if (d.fixed() != null && stops.stream().noneMatch(s -> Objects.equals(s.id, d.fixed().id))) stops.add(d.fixed().copy());
                    if (stops.isEmpty()) continue;
                    Map<String, Object> alt = shape("?", PlanService.str(a.get("short"), "대안"), PlanService.str(a.get("title"), "대안"),
                            PlanService.str(a.get("reason"), ""), fit(stops, st.lat(), st.lng(), st.clock(), modes, false), d, date, cache);
                    alt.put("recommended", Boolean.TRUE.equals(a.get("recommended")));
                    ai.add(alt);
                    if (ai.size() == 3) break;
                }
                if (!ai.isEmpty()) {
                    source = "gemini";
                    weatherNote = r.get("weather") instanceof String w ? w : null;
                    title = PlanService.str(r.get("title"), null);
                    subtitle = PlanService.str(r.get("subtitle"), null);
                }
            } catch (ApiException e) {
                log.info("대안 AI 실패 → 규칙 대안: {}", e.getMessage());
            }
        }

        // AI 대안이 3개가 안 되면 규칙 대안으로 채움 (같은 장소 순서는 한 번만)
        List<Map<String, Object>> alts = new ArrayList<>(ai);
        for (Map<String, Object> a : rule) {
            if (alts.size() >= 3) break;
            if (alts.stream().noneMatch(x -> sameStops(x, a))) alts.add(a);
        }
        char key = 'A';
        for (Map<String, Object> a : alts) a.put("key", String.valueOf(key++));
        if (!ai.isEmpty() && alts.size() > ai.size()) source = "gemini+rule";
        String target = d.fixed() == null ? "남은 일정" : d.fixed().name;
        return Maps.of("title", title != null ? title : d.fixed() == null ? "남은 일정 대안" : target + "에 늦지 않는 대안",
                "subtitle", subtitle != null ? subtitle : "지금 위치·시각에서 다시 계산했어요", "weather", weatherNote, "source", source,
                "alternatives", recommendBest(alts));
    }

    @SuppressWarnings("unchecked")
    private static boolean sameStops(Map<String, Object> a, Map<String, Object> b) {
        List<Object> x = ((List<Map<String, Object>>) a.get("stops")).stream().map(s -> s.get("name")).map(Object.class::cast).toList();
        List<Object> y = ((List<Map<String, Object>>) b.get("stops")).stream().map(s -> s.get("name")).map(Object.class::cast).toList();
        return x.equals(y) && Objects.equals(a.get("finalArrive"), b.get("finalArrive"));
    }

    /** 날씨 도구: 기상청 → 안 되면 제미나이가 구글 검색으로 직접 찾음 */
    private Object weatherTool(double lat, double lng) {
        try {
            return weather.forecast(lat, lng);
        } catch (ApiException e) {
            try {
                return Maps.of("source", "구글 검색(기상청 연결 실패: " + e.getMessage() + ")",
                        "summary", gemini.askWithSearch(String.format("위도 %.4f, 경도 %.4f 지역의 오늘 남은 시간대별 날씨(비 소식·강수확률·기온)를 3문장으로", lat, lng)));
            } catch (ApiException e2) {
                return Maps.of("error", "날씨를 가져오지 못했어요. 날씨 없이 판단하세요.");
            }
        }
    }

    // ---------------------------------------------------------------- 적용
    @Transactional
    public Map<String, Object> apply(Principal user, long id, Map<String, Object> alt, double lat, double lng) {
        Today d = today(user, id, lat, lng);
        List<Map<String, Object>> chosen = PlanService.maps(alt == null ? null : alt.get("stops"));
        if (chosen.isEmpty()) throw fail(400, "적용할 대안이 없어요.");
        Map<Long, Stop> before = new LinkedHashMap<>();
        d.rest().forEach(s -> before.put(s.id, s));
        List<Long> keep = new ArrayList<>();
        Map<Long, String> tag = new LinkedHashMap<>(), note = new LinkedHashMap<>();
        for (Map<String, Object> c : chosen) {
            String eta = PlanService.str(c.get("eta"), null);
            int stay = c.get("stay") instanceof Number n ? n.intValue() : 60;
            if (c.get("id") instanceof Number n && before.containsKey(n.longValue())) {
                Stop s = before.get(n.longValue());
                tag.put(s.id, stay != s.stay ? "changed" : "kept");
                if (stay != s.stay) note.put(s.id, s.stay + "분 → " + stay + "분");
                if (!s.fixed && eta != null) s.time = eta;
                s.stay = stay;
                mapper.updateStop(s);
                keep.add(s.id);
            } else if (c.get("lat") instanceof Number la && c.get("lng") instanceof Number lo) {
                Stop s = new Stop();
                s.tripId = id;
                s.day = d.day();
                s.placeId = c.get("placeId") instanceof String p ? p : null;
                s.name = PlanService.str(c.get("name"), "새 장소");
                s.category = PlanService.str(c.get("category"), null);
                s.lat = la.doubleValue();
                s.lng = lo.doubleValue();
                s.time = eta == null ? Geo.hhmm(d.now() + 30) : eta;
                s.stay = stay;
                s.fixed = false;
                s.state = "todo";
                mapper.insertStop(s);
                tag.put(s.id, "added");
                note.put(s.id, "새로 추가");
            }
        }
        for (Stop s : d.rest()) {
            if (keep.contains(s.id)) continue;
            s.state = "skipped";
            mapper.updateStop(s);
        }
        Trip t = trips.get(user, id);
        List<Map<String, Object>> view = new ArrayList<>();
        for (Stop s : t.stops) {
            if (s.day != d.day() || "skipped".equals(s.state)) continue;
            boolean here = d.current() != null && s.id.equals(d.current().id);
            String tg = "done".equals(s.state) || here ? "done" : tag.getOrDefault(s.id, "kept");
            view.add(Maps.of("id", s.id, "time", s.time, "name", s.name, "category", s.category, "stay", s.stay, "tag", tg, "note", note.get(s.id)));
        }
        String next = nextAlert(d, t);
        return Maps.of("key", alt.get("key"), "message", d.fixed() == null ? "일정을 바꿨어요!" : d.fixed().name + "에 늦지 않게 일정을 바꿨어요!",
                "nextAlert", next, "stops", view);
    }

    /** 적용 화면의 다음 알림 안내 (이미 지났으면 그다음 장소 기준) */
    private static String nextAlert(Today d, Trip t) {
        for (Stop s : t.stops) {
            if (s.day != d.day() || !"todo".equals(s.state) || s.fixed) continue;
            int start = s.arrivedAt != null ? minuteOf(s.arrivedAt) : Geo.min(s.time);
            int at = start + s.stay - 10;
            if (at > d.now())
                return s.name + "에서 출발할 시각(" + Geo.hhmm(start + s.stay) + ") 10분 전부터 1분마다 알려드릴게요. 늦게 도착하면 도착한 시각 기준으로 다시 맞춰요.";
        }
        return null;
    }

    // ---------------------------------------------------------------- 계산 도우미
    /** 계산 결과: 시각을 고친 일정, 마지막 도착, 고정 일정 여유, 줄인 장소 이름 */
    private record Plan(List<Stop> stops, Map<Long, Integer> moves, List<double[]> coords, int end, int slack, List<String> shortened) {}

    /**
     * 지금 위치·시각에서 차례로 이어 계산. shrink=true면 고정 일정에 늦을 때 고정 전 일정의 머무름을 최대 절반까지 줄여 맞춤.
     */
    private Plan fit(List<Stop> stops, double lat, double lng, int now, List<String> modes, boolean shrink) {
        Plan p = chain(stops, lat, lng, now, modes);
        if (!shrink || p.slack() >= 0) return p;
        List<String> names = new ArrayList<>();
        int need = -p.slack() + 5;
        for (Stop s : stops) {
            if (s.fixed || need <= 0) break;
            int cut = Math.min(s.stay / 2, need);
            if (cut <= 0) continue;
            s.stay -= cut;
            need -= cut;
            names.add(s.name);
        }
        Plan q = chain(stops, lat, lng, now, modes);
        return new Plan(q.stops(), q.moves(), q.coords(), q.end(), q.slack(), names);
    }

    private Plan chain(List<Stop> stops, double lat, double lng, int now, List<String> modes) {
        int clock = now, slack = Integer.MAX_VALUE;
        double fl = lat, fg = lng;
        Map<Long, Integer> moves = new LinkedHashMap<>();
        List<double[]> coords = new ArrayList<>();
        for (Stop s : stops) {
            RouteService.Leg leg = routes.leg(fl, fg, s.lat, s.lng, s.mode, modes, true);
            coords.addAll(leg.coords());
            int eta = clock + leg.minutes();
            if (s.fixed) slack = Math.min(slack, Geo.min(s.time) - eta);
            else s.time = Geo.hhmm(eta);
            moves.put(s.id == null ? -System.identityHashCode(s) : s.id, leg.minutes());
            clock = (s.fixed ? Math.max(eta, Geo.min(s.time)) : eta) + s.stay;
            fl = s.lat;
            fg = s.lng;
        }
        return new Plan(stops, moves, coords, clock, slack == Integer.MAX_VALUE ? 0 : slack, List.of());
    }

    private Map<String, Object> shape(String key, String shortName, String title, String reason, Plan p, Today d, LocalDate date, Map<String, Place> cache) {
        List<Map<String, Object>> stops = new ArrayList<>();
        int closedCount = 0, lastEta = p.end();
        for (Stop s : p.stops()) {
            Stop orig = s.id == null ? null : d.rest().stream().filter(o -> o.id.equals(s.id)).findFirst().orElse(null);
            String change = orig == null ? "새로 추가" : !Objects.equals(orig.stay, s.stay) ? orig.stay + "분 → " + s.stay + "분" : null;
            Integer move = p.moves().get(s.id == null ? -System.identityHashCode(s) : s.id);
            Place place = s.placeId == null ? null : cache.computeIfAbsent(s.placeId, k -> safeFind(k));
            Map<String, Object> h = PlaceService.openInfo(place, date, Geo.min(s.time), s.stay);
            boolean shut = Boolean.FALSE.equals(h.get("open"));
            if (shut) closedCount++;
            lastEta = Geo.min(s.time);
            stops.add(Maps.of("id", s.id, "placeId", s.placeId, "name", s.name, "category", s.category, "lat", s.lat, "lng", s.lng, "stay", s.stay,
                    "fixed", s.fixed, "eta", s.time, "moveMin", move, "change", change,
                    "closedReason", shut ? h.get("closedReason") : null, "closeAt", h.get("closeAt"), "closingSoon", Boolean.TRUE.equals(h.get("closingSoon")),
                    "naverUrl", s.placeId == null && orig == null ? PlaceService.naverMapLink(s.name, s.address) : null));
        }
        // 여유: 고정 일정이 있으면 그 시각 기준, 없으면 원래 계획한 마지막 일정 끝 시각 기준
        int slack;
        String fin;
        if (d.fixed() != null) {
            slack = p.slack();
            fin = Geo.hhmm(Geo.min(d.fixed().time) - slack);
        } else {
            Stop last = d.rest().get(d.rest().size() - 1);
            slack = Geo.min(last.time) + last.stay - p.end();
            fin = Geo.hhmm(lastEta);
        }
        return Maps.of("key", key, "short", shortName, "title", title, "reason", reason, "recommended", false,
                "finalArrive", fin, "slackMin", slack, "closedCount", closedCount, "coords", p.coords(), "stops", stops,
                "changes", (int) stops.stream().filter(s -> s.get("change") != null).count());
    }

    private Place safeFind(String id) {
        try {
            return places.find(id);
        } catch (ApiException e) {
            return null;
        }
    }

    /** 늦지 않고 문 닫은 곳도 없으면서 바뀌는 곳이 가장 적은 대안을 추천 (제미나이가 이미 골랐고 그 대안에 문 닫은 곳이 없으면 그대로) */
    private static List<Map<String, Object>> recommendBest(List<Map<String, Object>> alts) {
        alts.stream().filter(a -> Boolean.TRUE.equals(a.get("recommended")) && (int) a.get("closedCount") > 0).forEach(a -> a.put("recommended", false));
        long picked = alts.stream().filter(a -> Boolean.TRUE.equals(a.get("recommended"))).count();
        if (picked > 1) alts.stream().filter(a -> Boolean.TRUE.equals(a.get("recommended"))).skip(1).forEach(a -> a.put("recommended", false));
        if (picked == 0)
            alts.stream().filter(a -> (int) a.get("slackMin") >= 0 && (int) a.get("closedCount") == 0)
                    .min(Comparator.comparing((Map<String, Object> a) -> (int) a.get("changes")))
                    .or(() -> alts.stream().max(Comparator.comparing((Map<String, Object> a) -> (int) a.get("slackMin") - 100 * (int) a.get("closedCount"))))
                    .ifPresent(a -> a.put("recommended", true));
        return alts;
    }

    /** 뺀 장소 대신 갈 곳: 그 근처 1.5km, 그 시간에 영업 중, (비면 실내) 보정 평점 1위 */
    private Place substitute(Stop drop, boolean indoor, LocalDate date, int at) {
        return places.within(drop.lat, drop.lng, 1500, null).stream()
                .filter(p -> !p.name.equals(drop.name) && p.rating != null && (!indoor || PlaceService.isIndoor(p.category)))
                .filter(p -> PlaceService.closedOn(p, date) == null && !Boolean.FALSE.equals(PlaceService.openAt(p, date, at)))
                .max(Comparator.comparing(p -> PlaceService.adjusted(p.rating, p.reviewCount))).orElse(null);
    }

    private static Stop fromPlace(Place p, Stop like) {
        Stop s = new Stop();
        s.placeId = p.id;
        s.name = p.name;
        s.category = p.category;
        s.address = p.address;
        s.lat = p.lat;
        s.lng = p.lng;
        s.stay = like == null ? 60 : Math.min(like.stay, 90);
        s.time = like == null ? "12:00" : like.time;
        s.fixed = false;
        s.state = "todo";
        return s;
    }

    private void save(List<Stop> stops) {
        for (Stop s : stops) if (s.id != null) mapper.updateStop(s);
    }

    private static List<Stop> copy(List<Stop> list) {
        return new ArrayList<>(list.stream().map(Stop::copy).toList());
    }

    private static String summary(Today d) {
        StringBuilder sb = new StringBuilder();
        for (Stop s : d.rest())
            sb.append(s.id).append(" | ").append(s.name).append(" | ").append(s.category).append(" | ").append(s.time).append(" | ").append(s.stay).append(" | ").append(s.fixed ? "고정" : "").append("; ");
        return sb.length() == 0 ? "(없음)" : sb.toString();
    }

    private static int minuteOf(String isoDateTime) {
        LocalDateTime t = LocalDateTime.parse(isoDateTime);
        return t.getHour() * 60 + t.getMinute();
    }
}
