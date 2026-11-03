package com.tripbiseo.member.service;

import static com.tripbiseo.common.exception.ApiException.fail;

import java.time.LocalDate;

import org.springframework.stereotype.Service;

import com.tripbiseo.common.util.Geo;
import com.tripbiseo.member.dto.Notification;
import com.tripbiseo.member.dto.NotificationListResponse;
import com.tripbiseo.member.mapper.NotificationMapper;
import com.tripbiseo.trip.dto.Trip;
import com.tripbiseo.trip.service.TripService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {

    private static final int LIST_LIMIT = 50;      // 알림함에 보여 줄 최근 알림 수
    private static final int TITLE_MAX = 100;      // notification.title 길이
    private static final int TEXT_MAX = 300;       // notification.text 길이
    private static final int RETRO_DAYS = 3;       // 끝난 지 3일 안 여행만 "돌아보기" 알림

    private final NotificationMapper notificationMapper;
    private final TripService tripService; // ② 내 여행 목록 (읽기만)

    /** 회원에게 알림 하나 보내기 (종류 info) */
    @Override
    public void send(long memberId, String title, String text) {
        Notification notification = new Notification();
        notification.setMemberId(memberId);
        notification.setTitle(title);
        notification.setText(text);
        send(notification);
    }

    /** 알림 하나 보내기. 빈 값은 기본값으로, 긴 글은 칸 길이에 맞게 자름 */
    @Override
    public void send(Notification notification) {
        if (notification.getMemberId() == null || notification.getTitle() == null || notification.getTitle().isBlank()) {
            throw fail(400, "알림에 받는 사람과 제목이 있어야 해요.");
        }
        if (notification.getKind() == null) {
            notification.setKind("info");
        }
        if (notification.getLevel() == null) {
            notification.setLevel("info");
        }
        notification.setTitle(cut(notification.getTitle(), TITLE_MAX));
        notification.setText(cut(notification.getText(), TEXT_MAX));
        notificationMapper.insertNotification(notification);
    }

    /** 내 알림함: 일정 알림을 먼저 만들어 두고 최근 50개 + 안 읽은 수 */
    @Override
    public NotificationListResponse getNotificationList(long memberId) {
        addTripNotices(memberId);
        NotificationListResponse response = new NotificationListResponse();
        response.setNotifications(notificationMapper.selectNotificationList(memberId, LIST_LIMIT));
        response.setUnreadCount(notificationMapper.selectUnreadCount(memberId));
        return response;
    }

    /** 알림 하나 읽음 (내 알림이 아니면 404) */
    @Override
    public void readNotification(long memberId, long notificationId) {
        if (notificationMapper.updateNotificationRead(memberId, notificationId) == 0) {
            throw fail(404, "알림을 찾을 수 없어요.");
        }
    }

    /** 내 알림 모두 읽음 */
    @Override
    public void readAllNotifications(long memberId) {
        notificationMapper.updateNotificationReadAll(memberId);
    }

    // ---------------------------------------------------------------- 일정 알림 (알림함을 열 때 만듦)

    /** 내 여행마다: 내일 떠나는 여행 / 오늘 시작할 여행 / 최근 마친 여행. noticeKey로 한 번만 쌓임 */
    private void addTripNotices(long memberId) {
        LocalDate today = Geo.now().toLocalDate();
        String todayText = today.toString();
        String tomorrowText = today.plusDays(1).toString();
        String retroFromText = today.minusDays(RETRO_DAYS).toString();
        for (Trip trip : tripService.getTripList(memberId)) {
            boolean isPlanned = "planned".equals(trip.getStatus());
            boolean isInPeriod = todayText.compareTo(trip.getStartDate()) >= 0 && todayText.compareTo(trip.getEndDate()) <= 0;
            if (isPlanned && tomorrowText.equals(trip.getStartDate())) {
                sendTripNotice(memberId, trip, "dday1", "내일 " + trip.getTitle() + " 여행이에요",
                        "출발 전에 일정과 영업 시간을 한 번 더 확인해 보세요", "/trips/detail?id=" + trip.getId());
            } else if (isPlanned && isInPeriod) {
                sendTripNotice(memberId, trip, "start-" + todayText, "오늘 " + trip.getTitle() + " 여행 날이에요",
                        "출발할 때 여행 시작을 누르면 늦을 때 알려 드려요", "/trips/today?id=" + trip.getId());
            } else if ("done".equals(trip.getStatus()) && trip.getEndDate().compareTo(retroFromText) >= 0) {
                sendTripNotice(memberId, trip, "retro", trip.getTitle() + " 여행은 어땠나요?",
                        "다녀온 길과 기록을 돌아봐요", "/retro?id=" + trip.getId());
            }
        }
    }

    private void sendTripNotice(long memberId, Trip trip, String keyEnd, String title, String text, String link) {
        Notification notification = new Notification();
        notification.setMemberId(memberId);
        notification.setKind("plan");
        notification.setLevel("info");
        notification.setTitle(title);
        notification.setText(text);
        notification.setLink(link);
        notification.setNoticeKey("trip" + trip.getId() + "-" + keyEnd);
        send(notification);
    }

    /** 글이 max자보다 길면 잘라요 (null은 그대로) */
    private static String cut(String value, int max) {
        if (value == null || value.length() <= max) {
            return value;
        }
        return value.substring(0, max);
    }
}
