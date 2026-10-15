package com.tripbiseo.service;

import static com.tripbiseo.common.ApiException.fail;

import java.time.LocalDate;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.tripbiseo.common.ApiException;
import com.tripbiseo.common.Geo;
import com.tripbiseo.common.Json;
import com.tripbiseo.common.Maps;
import com.tripbiseo.dto.Place;
import com.tripbiseo.dto.Stop;
import com.tripbiseo.dto.Trip;

/**
 * 일정 만들기 (저장 전)
 *  chat     : 사용자 말 → 여행 조건(제미나이)
 *  generate : 조건 + 우리 DB 후보 장소(내 위치 근처·지역·꼭 갈 곳 주변) → 제미나이가 골라 일정 초안
 *             (DB 밖 장소는 TourAPI·카카오로 위치, 없으면 네이버 링크)
 *  check    : 실제 이동시간(TMAP/ODsay)·영업시간·휴무·고정 일정 여유를 서버가 계산
 */
@Service
public class PlanService {

    private static final Logger log = LoggerFactory.getLogger(PlanService.class);

    private final GeminiService gemini;
    private final PlaceService places;
    private final RouteService routes;

    public PlanService(GeminiService gemini, PlaceService places, RouteService routes) {
        this.gemini = gemini;
        this.places = places;
        this.routes = routes;
    }

    // ---------------------------------------------------------------- 대화 → 조건
    public Map<String, Object> chat(String message, Map<String, Object> current) {
        if (message == null || message.isBlank() || message.length() > 300) throw fail(400, "메시지를 1~300자로 입력해 주세요.");
        Map<String, Object> cond = new LinkedHashMap<>(current == null ? Map.of() : current);
        LocalDate today = Geo.now().toLocalDate();
        Map<String, Object> r = gemini.askJson("""
                너는 여행 일정 비서야. 오늘은 %s(%s)이야.
                사용자 말에서 여행 조건을 뽑아 JSON만 출력해. 말하지 않은 값은 null로 둬(이전 조건을 유지할게).
                이동 수단(transports)은 사용자가 직접 말했을 때만 채우고 짐작하지 마. "내 위치·여기·이 근처"처럼 말하면 region은 "내 위치".
                이전 조건: %s
                사용자: "%s"
                {"reply": "조건을 정리했다는 한두 문장 + 확인 부탁", "title": "여행 이름(15자 이내)", "region": "지역(예: 서울 종로 성수)",
                 "startDate": "YYYY-MM-DD", "endDate": "YYYY-MM-DD", "people": 정수, "transports": ["bus"|"subway"|"walk"|"taxi"|"car"],
                 "mustVisit": ["꼭 갈 장소"], "fixed": [{"name": "시간이 정해진 일정 장소", "time": "HH:mm"}], "style": "여행 스타일 한 줄"}
                """.formatted(today, today.getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.KOREAN), Json.write(cond), message));
        for (String k : List.of("title", "region", "startDate", "endDate", "people", "transports", "mustVisit", "fixed", "style"))
            if (r.get(k) != null && !(r.get(k) instanceof List<?> l && l.isEmpty() && cond.get(k) != null)) cond.put(k, r.get(k));
        cond.putIfAbsent("startDate", today.toString());
        cond.putIfAbsent("endDate", cond.get("startDate"));
        cond.putIfAbsent("people", 1);
        cond.putIfAbsent("transports", List.of("subway", "bus", "walk"));
        return Maps.of("reply", r.get("reply") instanceof String s ? s : "조건을 정리했어요. 확인해 주세요.", "conditions", cond);
    }

    // ---------------------------------------------------------------- 조건 → 일정 초안
    /** lat/lng: 화면이 보낸 지금 GPS 위치 (없으면 null) — "내 위치 근처"일 때 후보를 그 주변에서 고름 */
    public Map<String, Object> generate(Map<String, Object> cond, List<String> messages, Double lat, Double lng) {
        if (cond == null) throw fail(400, "여행 조건이 없어요.");
        Trip t = new Trip();
        t.title = str(cond.get("title"), "새 여행");
        t.startDate = str(cond.get("startDate"), Geo.now().toLocalDate().toString());
        t.endDate = str(cond.get("endDate"), t.startDate);
        t.people = cond.get("people") instanceof Number n ? n.intValue() : 1;
        t.transports = strings(cond.get("transports"));
        int days = TripService.days(t);

        // 1) 후보: 내 위치 근처 + 꼭 갈 곳과 그 근처 + 지역 이름으로 찾은 곳 (우리 DB)
        Map<String, Place> candidates = new LinkedHashMap<>();
        List<Map<String, Object>> notes = new ArrayList<>();
        String region = str(cond.get("region"), "");
        boolean nearMe = region.matches(".*(내 ?위치|현재 ?위치|여기|근처).*");
        // 지역을 따로 말했으면 내 위치는 쓰지 않음 (집에서 다른 지역 여행을 짤 때)
        Double oLat = nearMe || region.isBlank() ? lat : null, oLng = nearMe || region.isBlank() ? lng : null;
        if (oLat != null) places.within(oLat, oLng, 2000, null).stream().limit(60).forEach(x -> candidates.putIfAbsent(x.id, x));
        for (String name : strings(cond.get("mustVisit"))) {
            Place p = places.resolve(name, oLat, oLng);
            if (p == null) {
                notes.add(Maps.of("text", name + "의 위치를 찾지 못했어요", "url", PlaceService.naverMapLink(name, null)));
                continue;
            }
            if (p.id != null) candidates.put(p.id, p);
            places.within(p.lat, p.lng, 1000, null).stream().limit(12).forEach(x -> candidates.putIfAbsent(x.id, x));
        }
        if (!nearMe) for (String word : region.split("[\\s,·/]+"))
            if (word.length() >= 2 && !word.equals("서울")) places.inArea(word).stream().limit(25).forEach(x -> candidates.putIfAbsent(x.id, x));

        // 2) 제미나이가 후보에서 고르게 함 (후보 밖 장소는 이름만 → 3단계에서 위치 찾기)
        List<Map<String, Object>> picked;
        try {
            StringBuilder list = new StringBuilder();
            candidates.values().stream().limit(90).forEach(p -> list.append(p.id).append(" | ").append(p.name).append(" | ").append(p.category)
                    .append(p.rating == null ? "" : " | ★" + p.rating + "(" + p.reviewCount + ")").append(" | ").append(p.address).append('\n'));
            Map<String, Object> r = gemini.askJson("""
                    여행 일정을 짜 줘. 시간 계산은 대략이면 돼(서버가 실제 이동시간으로 다시 검사해).
                    조건: %s
                    사용자가 한 말: %s
                    규칙: 아래 후보 목록에서 고르고 id를 그대로 적어. 꼭 갈 곳과 시간 고정 일정은 반드시 넣고, 고정 일정은 그 시각으로.
                    꼭 갈 곳이 '분식집·카페'처럼 종류면 후보 중 그 종류에서 골라. 목록에 없는 장소가 꼭 필요하면 id는 null, name은 실제 상호명으로.
                    하루 4~6곳, 여러 날이면 날마다 나눠서, 점심(12시 전후)·저녁 시간엔 식당, 가까운 곳끼리 이어서.
                    기간: %d일 (day는 1부터)
                    %s
                    후보(id | 이름 | 분류 | 평점 | 주소):
                    %s
                    JSON만: {"title": "여행 이름", "stops": [{"day": 1, "id": "후보 id 또는 null", "name": "장소 이름", "time": "HH:mm", "stay": 머무는 분, "fixed": false}]}
                    """.formatted(Json.write(cond), Json.write(messages == null ? List.of() : messages), days,
                    t.startDate.equals(Geo.now().toLocalDate().toString())
                            ? "오늘 출발하는 여행이야. 지금은 " + Geo.hhmm(Geo.nowMin()) + "이니 1일차 일정은 " + Geo.hhmm(Geo.nowMin() + 30) + " 이후로만 짜."
                            : "", list));
            picked = maps(r.get("stops"));
            if (r.get("title") instanceof String s && !s.isBlank()) t.title = s.length() > 60 ? s.substring(0, 60) : s;
        } catch (ApiException e) {
            log.info("일정 생성 AI 실패 → 꼭 갈 곳만: {}", e.getMessage());
            notes.add(Maps.of("text", "AI 연결이 안 돼서 꼭 갈 곳만 순서대로 담았어요 (" + e.getMessage() + ")", "url", null));
            picked = fallback(cond);
        }

        // 3) 실제 장소로 바꾸기: 후보 id → DB 이름·분류 → TourAPI → 카카오 → 못 찾으면 네이버 링크 안내
        Place first = candidates.values().stream().findFirst().orElse(null);
        Double nearLat = oLat != null ? oLat : first == null ? null : first.lat, nearLng = oLng != null ? oLng : first == null ? null : first.lng;
        for (Map<String, Object> s : picked) {
            Place p = candidates.get(String.valueOf(s.get("id")));
            if (p == null) p = places.resolve(str(s.get("name"), null), nearLat, nearLng);
            if (p == null) {
                notes.add(Maps.of("text", s.get("name") + "의 위치를 찾지 못해 뺐어요", "url", PlaceService.naverMapLink(str(s.get("name"), ""), null)));
                continue;
            }
            Stop stop = new Stop();
            stop.day = Math.min(days, Math.max(1, s.get("day") instanceof Number n ? n.intValue() : 1));
            stop.placeId = p.id;
            stop.name = p.name;
            stop.category = p.category;
            stop.address = p.address;
            stop.lat = p.lat;
            stop.lng = p.lng;
            stop.time = str(s.get("time"), "10:00").matches("([01]\\d|2[0-3]):[0-5]\\d") ? str(s.get("time"), "10:00") : "10:00";
            stop.stay = s.get("stay") instanceof Number n ? Math.max(10, Math.min(600, n.intValue())) : 60;
            stop.fixed = Boolean.TRUE.equals(s.get("fixed"));
            stop.state = "todo";
            t.stops.add(stop);
        }
        if (t.stops.isEmpty()) throw fail(422, nearMe && lat == null ? "내 위치를 받지 못했어요. 브라우저 위치 권한을 허용하거나 지역 이름을 말해 주세요."
                : "일정에 넣을 장소를 찾지 못했어요. 가고 싶은 곳을 조금 더 자세히 말해 주세요.");
        t.stops.sort(Comparator.comparing((Stop s) -> s.day).thenComparing(s -> s.time));
        return Maps.of("title", t.title, "startDate", t.startDate, "endDate", t.endDate, "people", t.people, "transports", t.transports,
                "stops", t.stops, "notes", notes);
    }

    /** AI 없이: 꼭 갈 곳을 10시부터 2시간 간격, 고정 일정은 그 시각 */
    private static List<Map<String, Object>> fallback(Map<String, Object> cond) {
        List<Map<String, Object>> out = new ArrayList<>();
        int t = 600;
        for (String name : strings(cond.get("mustVisit"))) {
            out.add(Maps.of("day", 1, "name", name, "time", Geo.hhmm(t), "stay", 90, "fixed", false));
            t += 120;
        }
        for (Map<String, Object> f : maps(cond.get("fixed"))) out.add(Maps.of("day", 1, "name", f.get("name"), "time", f.get("time"), "stay", 120, "fixed", true));
        return out;
    }

    // ---------------------------------------------------------------- 검증
    /**
     * 하루 일정을 화면에 놓인 순서대로 실제 이동시간으로 이어 계산해 늦는 곳·영업시간·휴무를 찾음.
     * 시각이 비어 있는 곳(끌어서 순서를 바꾸거나 새로 넣은 곳)은 앞 일정에 이어서 시각을 정해 돌려줌.
     * 구간 수단은 장소마다 고른 값(mode), 없으면 거리로 자동.
     */
    public Map<String, Object> check(Trip draft, int day) {
        if (draft == null || draft.stops == null || draft.stops.isEmpty()) throw fail(400, "검증할 일정이 없어요.");
        if (draft.transports == null || draft.transports.isEmpty()) draft.transports = List.of("subway", "bus", "walk");
        LocalDate date = LocalDate.parse(draft.startDate).plusDays(day - 1);
        boolean today = date.equals(Geo.now().toLocalDate());
        List<Stop> list = draft.stops.stream().filter(s -> s.day != null && s.day == day).toList();
        List<Map<String, Object>> stops = new ArrayList<>(), legs = new ArrayList<>();
        boolean late = false, hoursOk = true, closedOk = true;
        Map<String, Object> fixed = null;
        Stop prev = null;
        int clock = 0;
        for (Stop s : list) {
            boolean fixedStop = Boolean.TRUE.equals(s.fixed);
            int arrive = s.time == null ? 600 : Geo.min(s.time);
            RouteService.Leg leg = null;
            if (prev != null) {
                leg = routes.leg(prev.lat, prev.lng, s.lat, s.lng, s.mode, draft.transports, true);
                legs.add(leg.toMap());
                arrive = Math.max(clock + leg.minutes(), fixedStop || s.time == null ? 0 : Geo.min(s.time)); // 일찍 도착하면 계획 시각에 시작
            }
            if (s.time == null) s.time = Geo.hhmm(Math.min(arrive, 23 * 60 + 30));
            String issue = null;
            if (today && !fixedStop && Geo.min(s.time) + s.stay < Geo.nowMin()) {
                issue = "이미 지난 시각이에요 (지금 " + Geo.hhmm(Geo.nowMin()) + ")";
                late = true;
            } else if (arrive > Geo.min(s.time) + 10) {
                issue = "계획보다 " + (arrive - Geo.min(s.time)) + "분 늦게 도착해요";
                late = true;
            }
            Place place = s.placeId == null ? null : safeFind(s.placeId);
            if (place != null) { // 여행 날짜에 걸릴 때만 알림
                Map<String, Object> h = PlaceService.openInfo(place, date, arrive, s.stay);
                String closed = PlaceService.closedOn(place, date);
                if (closed != null) {
                    issue = "이날 휴무예요 (" + closed + ")";
                    closedOk = false;
                } else if (Boolean.FALSE.equals(h.get("open"))) {
                    issue = Geo.hhmm(arrive) + "에는 영업 시간이 아니에요";
                    hoursOk = false;
                } else if (h.get("minutesLeft") instanceof Integer left && left < s.stay) {
                    issue = h.get("closeAt") + "에 문을 닫아 " + left + "분밖에 못 머물러요";
                    hoursOk = false;
                }
            }
            if (fixedStop && fixed == null)
                fixed = Maps.of("name", s.name, "time", s.time, "arrive", Geo.hhmm(arrive), "slack", Geo.min(s.time) - arrive);
            stops.add(Maps.of("id", s.id, "day", s.day, "placeId", s.placeId, "name", s.name, "category", s.category, "lat", s.lat, "lng", s.lng,
                    "time", s.time, "stay", s.stay, "fixed", s.fixed, "mode", s.mode, "arrive", Geo.hhmm(arrive), "issue", issue,
                    "leg", leg == null ? null : Maps.of("minutes", leg.minutes(), "meters", leg.meters(), "mode", leg.mode(), "source", leg.source(), "detail", leg.detail())));
            clock = (fixedStop ? Math.max(arrive, Geo.min(s.time)) : arrive) + s.stay;
            prev = s;
        }
        Integer slack = fixed == null ? null : (Integer) fixed.get("slack");
        boolean moveOk = !late && (slack == null || slack >= 0);
        return Maps.of("day", day, "date", date.toString(), "ok", moveOk && hoursOk && closedOk,
                "fixed", fixed, "slackMin", slack, "finalArrive", stops.isEmpty() ? null : stops.get(stops.size() - 1).get("arrive"),
                "checks", Maps.of("move", moveOk, "hours", hoursOk, "closed", closedOk), "stops", stops, "legs", legs);
    }

    private Place safeFind(String id) {
        try {
            return places.find(id);
        } catch (ApiException e) {
            return null;
        }
    }

    static String str(Object v, String fallback) {
        return v instanceof String s && !s.isBlank() ? s.trim() : fallback;
    }

    static List<String> strings(Object v) {
        return v instanceof List<?> l ? l.stream().filter(x -> x instanceof String).map(x -> ((String) x).trim()).filter(x -> !x.isEmpty()).toList() : List.of();
    }

    @SuppressWarnings("unchecked")
    static List<Map<String, Object>> maps(Object v) {
        return v instanceof List<?> l ? l.stream().filter(x -> x instanceof Map).map(x -> (Map<String, Object>) x).toList() : List.of();
    }
}
