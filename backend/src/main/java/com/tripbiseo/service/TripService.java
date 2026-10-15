package com.tripbiseo.service;

import static com.tripbiseo.common.ApiException.fail;

import java.security.Principal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.tripbiseo.common.Geo;
import com.tripbiseo.common.Maps;
import com.tripbiseo.dto.Member;
import com.tripbiseo.dto.Stop;
import com.tripbiseo.dto.Trip;
import com.tripbiseo.mapper.MemberMapper;
import com.tripbiseo.mapper.PlaceMapper;
import com.tripbiseo.mapper.TripMapper;

/** 여행·일정 저장/조회/수정/삭제. 모든 조회는 로그인한 회원의 여행만 */
@Service
public class TripService {

    private static final List<String> TRANSPORTS = List.of("bus", "subway", "walk", "taxi", "car");

    private final TripMapper trips;
    private final MemberMapper members;
    private final PlaceMapper places;

    public TripService(TripMapper trips, MemberMapper members, PlaceMapper places) {
        this.trips = trips;
        this.members = members;
        this.places = places;
    }

    public long memberId(Principal user) {
        Member m = user == null ? null : members.findByUsername(user.getName());
        if (m == null) throw fail(401, "로그인이 필요해요.");
        return m.getMemberId();
    }

    // ---------------------------------------------------------------- 여행
    public List<Trip> list(Principal user) {
        return trips.findByMember(memberId(user));
    }

    public Trip get(Principal user, long id) {
        Trip t = trips.findById(id, memberId(user));
        if (t == null) throw fail(404, "여행을 찾을 수 없어요.");
        t.stops = trips.findStops(id);
        return t;
    }

    @Transactional
    public Trip create(Principal user, Trip draft) {
        checkInfo(draft);
        if (draft.stops == null || draft.stops.isEmpty()) throw fail(400, "장소를 하나 이상 담아 주세요.");
        if (draft.endDate.compareTo(Geo.now().toLocalDate().toString()) < 0) throw fail(400, "이미 지난 날짜로는 여행을 만들 수 없어요.");
        draft.memberId = memberId(user);
        trips.insertTrip(draft);
        int days = days(draft);
        for (Stop s : draft.stops) {
            checkStop(s, days);
            s.tripId = draft.id;
            trips.insertStop(s);
        }
        return get(user, draft.id);
    }

    @Transactional
    public Trip update(Principal user, long id, Trip info) {
        Trip t = get(user, id);
        checkInfo(info);
        int days = days(info);
        if (t.stops.stream().anyMatch(s -> s.day > days)) throw fail(400, "줄인 날짜 밖에 일정이 있어요. 그 일정을 먼저 옮기거나 지워 주세요.");
        info.id = id;
        info.memberId = t.memberId;
        trips.updateTrip(info);
        return get(user, id);
    }

    public void delete(Principal user, long id) {
        if (trips.deleteTrip(id, memberId(user)) == 0) throw fail(404, "여행을 찾을 수 없어요.");
    }

    /** 여행 시작: 오늘이 여행 기간 안일 때만 */
    public Trip start(Principal user, long id) {
        Trip t = get(user, id);
        String today = Geo.now().toLocalDate().toString();
        if (today.compareTo(t.startDate) < 0 || today.compareTo(t.endDate) > 0) throw fail(400, "여행 기간(" + t.startDate + " ~ " + t.endDate + ")에만 시작할 수 있어요.");
        Trip other = trips.findByMember(t.memberId).stream().filter(x -> "ongoing".equals(x.status) && x.id != id).findFirst().orElse(null);
        if (other != null) throw fail(409, "이미 진행 중인 여행(" + other.title + ")이 있어요. 그 여행을 먼저 마쳐 주세요.");
        trips.updateStatus(id, "ongoing");
        return get(user, id);
    }

    public Trip finish(Principal user, long id) {
        get(user, id);
        trips.updateStatus(id, "done");
        return get(user, id);
    }

    // ---------------------------------------------------------------- 일정 한 칸
    public Trip addStop(Principal user, long tripId, Stop s) {
        Trip t = get(user, tripId);
        checkStop(s, days(t));
        s.tripId = tripId;
        trips.insertStop(s);
        return get(user, tripId);
    }

    public Trip updateStop(Principal user, long tripId, long stopId, Stop form) {
        Trip t = get(user, tripId);
        Stop s = stop(tripId, stopId);
        s.day = form.day == null ? s.day : form.day;
        s.time = form.time == null ? s.time : form.time;
        s.stay = form.stay == null ? s.stay : form.stay;
        s.fixed = form.fixed == null ? s.fixed : form.fixed;
        s.mode = form.mode;
        s.memo = form.memo;
        s.state = form.state == null ? s.state : form.state;
        checkStop(s, days(t));
        trips.updateStop(s);
        return get(user, tripId);
    }

    public Trip deleteStop(Principal user, long tripId, long stopId) {
        get(user, tripId);
        if (trips.deleteStop(tripId, stopId) == 0) throw fail(404, "일정을 찾을 수 없어요.");
        return get(user, tripId);
    }

    public Stop stop(long tripId, long stopId) {
        Stop s = trips.findStop(tripId, stopId);
        if (s == null) throw fail(404, "일정을 찾을 수 없어요.");
        return s;
    }

    // ---------------------------------------------------------------- 대화 기록 (계획 대화 + 여행 중 대화를 여행 하나에 시간순으로)
    public List<Map<String, Object>> chats(Principal user, long id) {
        get(user, id);
        return trips.findChats(id);
    }

    /** messages: [{role: me|ai, text, at: "yyyy-MM-ddTHH:mm:ss"(없으면 지금)}] */
    @Transactional
    public Map<String, Object> addChats(Principal user, long id, List<Map<String, Object>> messages) {
        get(user, id);
        for (Map<String, Object> m : messages == null ? List.<Map<String, Object>>of() : messages) {
            String text = PlanService.str(m.get("text"), null), at = PlanService.str(m.get("at"), null);
            if (text == null || !List.of("me", "ai").contains(m.get("role"))) continue;
            trips.insertChat(id, (String) m.get("role"), text.length() > 2000 ? text.substring(0, 2000) : text,
                    at != null && at.matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}(:\\d{2})?") ? at : null);
        }
        return Map.of("ok", true);
    }

    // ---------------------------------------------------------------- 회고
    /** 계획 vs 실제: 일정마다 실제 도착 시각·머문 시간 + GPS 선 */
    public Map<String, Object> retro(Principal user, long id) {
        Trip t = get(user, id);
        List<Map<String, Object>> stops = new ArrayList<>();
        int visited = 0;
        for (Stop s : t.stops) {
            Map<String, Object> m = Maps.of("id", s.id, "day", s.day, "name", s.name, "category", s.category, "lat", s.lat, "lng", s.lng,
                    "time", s.time, "stay", s.stay, "state", s.state, "actualArrive", null, "actualStay", null);
            if (s.arrivedAt != null) {
                visited++;
                LocalDateTime a = LocalDateTime.parse(s.arrivedAt);
                m.put("actualArrive", a.toLocalTime().toString().substring(0, 5));
                if (s.departedAt != null) m.put("actualStay", ChronoUnit.MINUTES.between(a, LocalDateTime.parse(s.departedAt)));
            }
            stops.add(m);
        }
        return Maps.of("id", t.id, "title", t.title, "startDate", t.startDate, "endDate", t.endDate, "visited", visited,
                "stops", stops, "track", trips.findTracks(id));
    }

    // ---------------------------------------------------------------- 입력 확인
    public static int days(Trip t) {
        return (int) ChronoUnit.DAYS.between(LocalDate.parse(t.startDate), LocalDate.parse(t.endDate)) + 1;
    }

    private static void checkInfo(Trip t) {
        if (t == null) throw fail(400, "여행 정보가 없어요.");
        if (t.title == null || t.title.isBlank() || t.title.length() > 60) throw fail(400, "여행 이름을 1~60자로 적어 주세요.");
        t.title = t.title.trim();
        try {
            LocalDate s = LocalDate.parse(t.startDate), e = LocalDate.parse(t.endDate);
            if (e.isBefore(s) || ChronoUnit.DAYS.between(s, e) >= 30) throw fail(400, "여행 기간은 1~30일이에요.");
        } catch (DateTimeParseException | NullPointerException e) {
            throw fail(400, "여행 날짜를 확인해 주세요.");
        }
        if (t.people == null || t.people < 1 || t.people > 20) throw fail(400, "동행 인원은 1~20명이에요.");
        if (t.transports == null || t.transports.isEmpty() || !TRANSPORTS.containsAll(t.transports)) throw fail(400, "이동 수단을 하나 이상 골라 주세요.");
    }

    private void checkStop(Stop s, int days) {
        if (s.name == null || s.name.isBlank() || s.name.length() > 120) throw fail(400, "장소 이름을 확인해 주세요.");
        if (s.lat == null || s.lng == null || Math.abs(s.lat) > 90 || Math.abs(s.lng) > 180) throw fail(400, s.name + "의 위치를 확인해 주세요.");
        if (s.day == null || s.day < 1 || s.day > days) throw fail(400, s.name + "의 날짜가 여행 기간 밖이에요.");
        if (s.time == null || !s.time.matches("([01]\\d|2[0-3]):[0-5]\\d")) throw fail(400, s.name + "의 시각을 HH:mm으로 적어 주세요.");
        if (s.stay == null || s.stay < 0 || s.stay > 720) throw fail(400, s.name + "의 머무는 시간은 0~720분이에요.");
        if (s.memo != null && s.memo.length() > 200) throw fail(400, "메모는 200자까지예요.");
        if (s.mode != null && !List.of("walk", "transit", "taxi").contains(s.mode)) s.mode = null;
        if (s.fixed == null) s.fixed = false;
        if (s.state == null) s.state = "todo";
        if (s.placeId != null && places.findById(s.placeId) == null) s.placeId = null; // DB에 없는 id면 연결만 끊음
    }
}
