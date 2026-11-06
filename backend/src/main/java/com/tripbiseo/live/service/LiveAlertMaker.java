package com.tripbiseo.live.service;

import org.springframework.stereotype.Component;

import com.tripbiseo.common.util.Geo;
import com.tripbiseo.live.dto.LeaveInfo;
import com.tripbiseo.live.dto.LiveAlert;
import com.tripbiseo.live.dto.StatusContext;
import com.tripbiseo.live.dto.WeatherInfo;
import com.tripbiseo.trip.dto.OpenInfo;
import com.tripbiseo.trip.dto.Stop;

/**
 * ④ 지금 상황(status)의 출발 알림(leave)과 화면 맨 위 알림(alert) 만들기.
 * StatusServiceImpl 안에서만 쓰는 도우미라 인터페이스 없이 @Component로 둠 (RouteClient와 같은 방식).
 * 숫자 계산은 StatusServiceImpl이 해서 StatusContext로 넘겨 주고, 여기서는 "뭐라고 알릴지"만 정해요.
 */
@Component // Spring이 객체를 하나 만들어 두고, 필요한 곳(StatusServiceImpl)에 생성자 주입으로 넣어 줌
public class LiveAlertMaker {

    private static final int LEAVE_WINDOW_MIN = 10;  // 출발 시각 앞뒤 10분 안이면 출발 알림
    private static final int TIGHT_SLACK_MIN = 10;   // 고정 일정 여유가 이 이하면 "여유가 얼마 없어요"
    private static final int NEXT_LATE_MIN = 10;     // 다음 장소에 이보다 더 늦으면 알림

    /**
     * 출발 알림: 출발 시각 앞뒤 10분 안일 때만 (아니면 null)
     *  - 지금 어떤 칸에 있으면: 그 칸에서 떠날 시각 (도착 + 머묾)
     *  - 이동 중이면: 다음 칸에 계획 시각대로 닿으려면 떠나야 하는 시각 (계획 시각 − 이동 시간)
     */
    public LeaveInfo makeLeave(StatusContext context) {
        Stop current = context.getCurrent();
        Stop nextStop = context.getNextStop();
        int now = context.getNow();
        if (current != null) {
            String to = nextStop == null ? null : nextStop.getName();
            return toLeave(current, context.getLeaveAt(), context.getLeaveAt() - now, to, true);
        }
        if (nextStop == null || context.getMissedStops().contains(nextStop)) {
            return null;
        }
        // 이동 중이면 eta = 지금 + 이동 시간이라, 이동 시간 = eta − 지금
        int moveMinutes = context.getEtaMap().get(nextStop.getId()) - now;
        int leaveAt = Geo.toMinutes(nextStop.getTime()) - moveMinutes;
        return toLeave(nextStop, leaveAt, leaveAt - now, nextStop.getName(), false);
    }

    /**
     * 화면 맨 위 알림 하나. 여러 개면 위에서부터 먼저 맞는 것 하나만:
     * 더 머묾 > 영업 끝·휴무·머묾 부족 > 고정 일정 지각 > 출발 > 지난 일정 > 고정 여유 10분 이하 > 다음 장소 지각
     */
    public LiveAlert makeAlert(StatusContext context, LeaveInfo leave) {
        Stop current = context.getCurrent();
        Stop fixedStop = context.getFixedStop();
        Integer slack = context.getSlack();
        if (isOverdue(context)) {
            int overMinutes = context.getNow() - context.getLeaveAt();
            // key 끝의 숫자는 5분마다 바뀜 → 화면이 5분마다 다시 알려 줌
            return newAlert("overdue-" + current.getId() + "-" + overMinutes / 5, "danger",
                    current.getName() + "에서 " + overMinutes + "분 더 머물렀어요", "계획한 출발 시각이 지났어요. 지금 위치에서 다시 계산했어요");
        }
        LiveAlert closedAlert = makeClosedAlert(context);
        if (closedAlert != null) {
            return closedAlert;
        }
        if (isLate(slack)) {
            // key 끝의 숫자는 10분 더 늦어질 때마다 바뀜 → 그때마다 다시 알려 줌
            return newAlert("late-" + fixedStop.getId() + "-" + (-slack) / 10, "danger",
                    "지금대로면 " + fixedStop.getName() + "에 " + (-slack) + "분 늦어요", "'대안 보기'로 늦지 않는 방법을 확인하세요");
        }
        if (leave != null) {
            return makeLeaveAlert(leave);
        }
        if (!context.getMissedStops().isEmpty()) {
            Stop missed = context.getMissedStops().get(0);
            return newAlert("missed-" + missed.getId(), "warn",
                    missed.getName() + " 계획 시간이 지났어요", "아직 안 갔다면 '대안 보기'에서 빼거나 바꿀 수 있어요");
        }
        if (slack != null && slack <= TIGHT_SLACK_MIN) {
            return newAlert("tight-" + fixedStop.getId(), "warn",
                    fixedStop.getName() + "까지 여유가 " + slack + "분뿐이에요", "다음 장소는 짧게 들르는 게 좋아요");
        }
        LiveAlert nextLateAlert = makeNextLateAlert(context);
        return nextLateAlert != null ? nextLateAlert : makeRainAlert(context);
    }

    /** 다음 경로 비 알림 (가장 낮은 순위). key에 비 시작 시각을 넣어 예보가 바뀌면 다시 울림 */
    private LiveAlert makeRainAlert(StatusContext context) {
        WeatherInfo weather = context.getWeather();
        if (weather == null || weather.getOutdoor().isEmpty()) {
            return null; // 비 소식이 없거나, 비 올 때 야외에 있을 곳이 없으면 알리지 않음
        }
        String what = weather.isSnow() ? "눈" : "비";
        String movable = findMovableOutdoor(context);
        if (movable != null) {
            // 돌발 ② (11/06): 아침에 미리 알려서 야외를 비 오기 전으로 옮기게
            return newAlert("rain-move-" + weather.getTime().replace(":", ""), "warn",
                    weather.getTime() + "부터 " + what + " 소식이 있어요 (강수확률 " + weather.getPop() + "%)",
                    movable + "을(를) " + what + " 오기 전에 먼저 가면 안 젖어요. '대안 보기'에서 순서를 바꿔 보세요");
        }
        return newAlert("rain-" + weather.getTime().replace(":", ""), "warn",
                weather.getTime() + "부터 " + what + " 소식이 있어요 (강수확률 " + weather.getPop() + "%)",
                String.join(", ", weather.getOutdoor()) + "에 있을 때예요. 우산을 챙기세요");
    }

    /** 비 맞을 야외 칸 중 아직 안 간 첫 곳 이름 (지금 있는 곳은 옮길 수 없어서 뺌). 없으면 null */
    private String findMovableOutdoor(StatusContext context) {
        WeatherInfo weather = context.getWeather();
        if (weather == null) {
            return null;
        }
        String currentName = context.getCurrent() == null ? null : context.getCurrent().getName();
        for (String name : weather.getOutdoor()) {
            if (!name.equals(currentName)) {
                return name;
            }
        }
        return null;
    }

    /**
     * 일정을 다시 짜는 게 좋은지 (화면에 지연 배너·'대안 보기'를 띄울지)
     * 더 머묾 · 영업 끝·휴무·머묾 부족 · 고정 일정 지각 · 지난 일정 · 다음 장소 10분 넘게 늦음 중 하나라도 맞으면 true.
     * makeAlert는 하나만 고르므로 결과를 쓰지 않고 조건을 하나씩 다시 검사해요. (출발 알림·여유 10분 이하는 다시 짤 일이 아님)
     */
    public boolean isReplanNeeded(StatusContext context) {
        if (isOverdue(context)) {
            return true;
        }
        if (makeClosedAlert(context) != null) {
            return true;
        }
        if (isLate(context.getSlack())) {
            return true;
        }
        if (!context.getMissedStops().isEmpty()) {
            return true;
        }
        if (makeNextLateAlert(context) != null) {
            return true;
        }
        return findMovableOutdoor(context) != null; // 비 오기 전에 야외를 먼저 (11/06)
    }

    // makeAlert에서

    /** 지금 칸에서 계획한 출발 시각이 지났는지 (더 머묾) */
    private boolean isOverdue(StatusContext context) {
        return context.getCurrent() != null && context.getNow() >= context.getLeaveAt();
    }

    /** 고정 일정에 늦는지 (여유가 음수) */
    private boolean isLate(Integer slack) {
        return slack != null && slack < 0;
    }

    /** 남은 분이 −10~10 안이면 LeaveInfo, 아니면 null */
    private LeaveInfo toLeave(Stop stop, int leaveAt, int minutesLeft, String to, boolean isAtStop) {
        if (Math.abs(minutesLeft) > LEAVE_WINDOW_MIN) {
            return null;
        }
        LeaveInfo leave = new LeaveInfo();
        leave.setStopId(stop.getId());
        leave.setName(stop.getName());
        leave.setLeaveAt(Geo.toHhmm(leaveAt));
        leave.setMinutesLeft(minutesLeft);
        leave.setTo(to);
        leave.setAtStop(isAtStop);
        return leave;
    }

    /**
     * 영업 알림 (남은 칸 중 첫 번째 하나, 없으면 null)
     *  - 도착할 때 닫힘·휴무: danger / 열려 있지만 머무는 시간을 다 못 채움: warn
     */
    private LiveAlert makeClosedAlert(StatusContext context) {
        for (Stop stop : context.getTodayStops()) {
            OpenInfo open = context.getOpenMap().get(stop.getId());
            if (open == null) {
                continue; // 남은 칸이 아니거나 영업 정보를 모름
            }
            if (Boolean.FALSE.equals(open.getOpen())) {
                String reason = open.getClosedReason() == null ? "도착할 때 문을 닫아요" : open.getClosedReason();
                return newAlert("closed-" + stop.getId(), "danger", stop.getName() + ": " + reason,
                        "도착 예상 " + Geo.toHhmm(context.getEtaMap().get(stop.getId())) + " — '대안 보기'로 다른 곳을 찾아보세요");
            }
            if (isTooShort(open, stop)) {
                return newAlert("closed-" + stop.getId() + "-short", "warn", stop.getName() + ": " + open.getCloseAt() + "에 닫아요",
                        "도착 후 " + open.getMinutesLeft() + "분밖에 못 있어요. '대안 보기'로 다른 곳을 찾아보세요");
            }
        }
        return null;
    }

    /** 열려 있지만 닫을 때까지 남은 시간이 머무는 시간보다 짧은가 */
    private boolean isTooShort(OpenInfo open, Stop stop) {
        int stay = stop.getStay() == null ? 0 : stop.getStay();
        return Boolean.TRUE.equals(open.getOpen()) && open.getMinutesLeft() != null && open.getMinutesLeft() < stay;
    }

    /** 출발 알림: 남은 분을 key에 넣어 1분마다 새로 울리게 함 (repeat = true) */
    private LiveAlert makeLeaveAlert(LeaveInfo leave) {
        int left = leave.getMinutesLeft();
        String title = left + "분 뒤 출발해야 해요";
        if (left == 0) {
            title = "지금 출발해야 해요";
        } else if (left < 0) {
            title = "출발 시각이 " + (-left) + "분 지났어요";
        }
        String text = leave.getName() + "에 계획대로 닿으려면 " + leave.getLeaveAt() + "에 출발";
        if (leave.isAtStop()) {
            text = leave.getName() + " 출발 " + leave.getLeaveAt() + (leave.getTo() == null ? "" : " → " + leave.getTo());
        }
        LiveAlert alert = newAlert("leave-" + leave.getStopId() + "-" + left, left > 0 ? "warn" : "danger", title, text);
        alert.setRepeat(true);
        return alert;
    }

    /** 다음 장소에 10분 넘게 늦으면 알림 (아니면 null) */
    private LiveAlert makeNextLateAlert(StatusContext context) {
        Stop nextStop = context.getNextStop();
        if (nextStop == null) {
            return null;
        }
        int lateMinutes = context.getEtaMap().get(nextStop.getId()) - Geo.toMinutes(nextStop.getTime());
        if (lateMinutes <= NEXT_LATE_MIN) {
            return null;
        }
        return newAlert("next-" + nextStop.getId() + "-" + lateMinutes / 10, "warn",
                nextStop.getName() + "에 " + lateMinutes + "분 늦게 도착할 것 같아요", "'대안 보기'로 남은 일정을 다시 짤 수 있어요");
    }

    /** 알림 하나 만들기 */
    private LiveAlert newAlert(String key, String level, String title, String text) {
        LiveAlert alert = new LiveAlert();
        alert.setKey(key);
        alert.setLevel(level);
        alert.setTitle(title);
        alert.setText(text);
        return alert;
    }
}
