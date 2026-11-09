package com.tripbiseo.live.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

import com.tripbiseo.common.util.Geo;
import com.tripbiseo.live.dto.LiveAlert;
import com.tripbiseo.member.dto.Notification;
import com.tripbiseo.member.service.NotificationService;
import com.tripbiseo.trip.dto.Stop;
import com.tripbiseo.trip.dto.Trip;
import com.tripbiseo.trip.service.TripService;

import lombok.RequiredArgsConstructor;

/**
 * 자동 일정 조정 (③ 11/08 주형, ④ 규칙 확장): 느슨한 곳(식당·카페·공원·쇼핑)에서 계획보다 늦게 떠나면
 *  - 뒤에 예약(회식·KTX)이 없거나 여유가 넉넉하면 뒤 일정이 밀리기만 함 → "○○ 도착이 N분 늦어져요"
 *  - 밀려서 예약까지 여유가 빠듯해지면(20분 미만) 그 사이 느슨한 곳의 머무는 시간을 알아서 줄임 (원래의 70%까지, 5분 단위)
 *    → "○○에서 N분 더 머물러서 △△을 60분 → 50분으로 줄였어요"
 * 숙소·예약·꼭 갈 곳은 안 줄임. 줄여도 늦으면 여행 중 화면의 늦음 카드가 대안을 보여 줌.
 * 안내는 알림함에 한 줄 남기고, 여행 중 화면 위쪽에도 잠깐 보여 주도록 최근 안내를 기억해 둠(상황 응답 adjust).
 */
@Component
@RequiredArgsConstructor
public class AutoAdjuster {

    private static final int LATE_LEAVE_MIN = 10;    // 이만큼 이상 늦게 떠났을 때만 안내·조정
    private static final int TARGET_SLACK_MIN = 20;  // 예약까지 이만큼 여유를 남기려고 줄임
    private static final double KEEP_RATE = 0.7;     // 머무는 시간은 원래의 70%까지만 줄임
    private static final int STEP_MIN = 5;           // 5분 단위
    private static final int SHOW_MIN = 30;          // 여행 중 화면에 안내를 보여 주는 시간(앱 시각 기준 분)

    private final TripService tripService;
    private final LiveStopFinder liveStopFinder;
    private final AlternativeCalculator alternativeCalculator;
    private final NotificationService notificationService;
    private final Map<Long, LiveAlert> recentMap = new ConcurrentHashMap<>(); // 여행 id → 최근 안내 (key에 만든 분)

    /** 칸에서 떠난 직후: 늦게 떠났으면 뒤 일정을 밀거나 줄이고 안내 */
    public void adjustAfterLeave(long memberId, long tripId, Stop left) {
        int lateMin = roundDown(Geo.nowMinutes() - liveStopFinder.getLeaveMinutes(left)); // 5분 단위로 (걸어서 나온 1~2분은 빼고)
        if (lateMin < LATE_LEAVE_MIN || LiveStopFinder.isStay(left)) {
            return;
        }
        Trip trip = tripService.getTrip(memberId, tripId);
        List<Stop> ahead = getAheadStops(liveStopFinder.getTodayStops(trip), left);
        if (ahead.isEmpty()) {
            return;
        }
        int fixedIndex = findFixedIndex(ahead);
        int slack = fixedIndex < 0 ? Integer.MAX_VALUE
                : alternativeCalculator.calculate(trip, null, ahead, left.getLat(), left.getLng()).getSlackMin();
        if (slack >= TARGET_SLACK_MIN) {
            notice(memberId, tripId, ahead.get(0).getName() + " 도착이 " + lateMin + "분 늦어져요",
                    left.getName() + "에서 " + lateMin + "분 더 머물렀어요. 뒤 일정은 그만큼 밀려요");
            return;
        }
        List<String> changes = trimStays(memberId, tripId, ahead.subList(0, fixedIndex), roundUp(TARGET_SLACK_MIN - slack));
        if (changes.isEmpty()) {
            return; // 줄일 곳이 없음 → 늦음 카드가 대안을 보여 줌
        }
        notice(memberId, tripId, left.getName() + "에서 " + lateMin + "분 더 머물러서 " + String.join(", ", changes) + "으로 줄였어요",
                ahead.get(fixedIndex).getName() + " " + ahead.get(fixedIndex).getTime() + "에 늦지 않게 남은 일정을 맞췄어요");
    }

    /** 여행 중 화면 위쪽에 보여 줄 최근 안내 (없거나 오래됐으면 null) */
    public LiveAlert getRecent(long tripId) {
        LiveAlert alert = recentMap.get(tripId);
        if (alert == null) {
            return null;
        }
        int madeAt = Integer.parseInt(alert.getKey().substring(alert.getKey().lastIndexOf('-') + 1));
        int passed = Geo.nowMinutes() - madeAt;
        return passed >= 0 && passed <= SHOW_MIN ? alert : null;
    }

    // ---------------------------------------------------------------- 도우미

    /** 떠난 칸 뒤의 아직 안 간 칸 (순서대로) */
    private List<Stop> getAheadStops(List<Stop> todayStops, Stop left) {
        List<Stop> ahead = new ArrayList<>();
        boolean isAfter = false;
        for (Stop stop : todayStops) {
            if (stop.getId().equals(left.getId())) {
                isAfter = true;
            } else if (isAfter && stop.getArrivedAt() == null && !"skipped".equals(stop.getState())) {
                ahead.add(stop);
            }
        }
        return ahead;
    }

    /** 첫 예약(고정) 칸 위치, 없으면 -1 */
    private int findFixedIndex(List<Stop> stops) {
        for (int i = 0; i < stops.size(); i++) {
            if (Boolean.TRUE.equals(stops.get(i).getFixed())) {
                return i;
            }
        }
        return -1;
    }

    /** 앞에서부터 느슨한 곳의 머무는 시간을 need분만큼 줄여 저장 (②의 updateStop). 바뀐 곳 "이름 60분 → 50분" 목록 */
    private List<String> trimStays(long memberId, long tripId, List<Stop> before, int need) {
        List<String> changes = new ArrayList<>();
        int left = need;
        for (Stop stop : before) {
            int stay = liveStopFinder.getStay(stop);
            boolean isMust = "must".equals(stop.getImportance()); // 꼭 갈 곳(예: 선물 사러 타임스퀘어)은 안 줄임
            if (left <= 0 || LiveStopFinder.isStay(stop) || isMust || stay <= 0) {
                continue;
            }
            int cut = Math.min(left, roundDown(stay - (int) Math.ceil(stay * KEEP_RATE)));
            if (cut <= 0) {
                continue;
            }
            saveStay(memberId, tripId, stop, stay - cut);
            changes.add(stop.getName() + objectParticle(stop.getName()) + " " + stay + "분 → " + (stay - cut) + "분");
            left -= cut;
        }
        return changes;
    }

    /** 머무는 시간만 바꿔 저장 (시각·이동 수단·메모는 그대로, ②의 updateStop은 보낸 값으로 덮어씀) */
    private void saveStay(long memberId, long tripId, Stop stop, int stay) {
        Stop update = new Stop();
        update.setTime(stop.getTime());
        update.setStay(stay);
        update.setMode(stop.getMode());
        update.setMemo(stop.getMemo());
        tripService.updateStop(memberId, tripId, stop.getId(), update);
    }

    /** 알림함에 한 줄 + 여행 중 화면 위쪽에 잠깐 */
    private void notice(long memberId, long tripId, String title, String text) {
        Notification notification = new Notification();
        notification.setMemberId(memberId);
        notification.setKind("trip");
        notification.setLevel("info");
        notification.setTitle(title);
        notification.setText(text);
        notification.setLink("/live");
        notificationService.send(notification);

        LiveAlert alert = new LiveAlert();
        alert.setKey("adjust-" + tripId + "-" + Geo.nowMinutes());
        alert.setLevel("info");
        alert.setTitle(title);
        alert.setText(text);
        recentMap.put(tripId, alert);
    }

    /** 받침이 있으면 "을", 없으면 "를" (한글이 아니면 "을(를)") */
    private String objectParticle(String name) {
        char last = name.charAt(name.length() - 1);
        if (last < '가' || last > '힣') {
            return "을(를)";
        }
        return (last - '가') % 28 == 0 ? "를" : "을";
    }

    private int roundUp(int minutes) {
        return (minutes + STEP_MIN - 1) / STEP_MIN * STEP_MIN;
    }

    private int roundDown(int minutes) {
        return minutes / STEP_MIN * STEP_MIN;
    }
}
