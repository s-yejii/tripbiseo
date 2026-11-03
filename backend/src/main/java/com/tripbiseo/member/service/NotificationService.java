package com.tripbiseo.member.service;

import com.tripbiseo.member.dto.Notification;
import com.tripbiseo.member.dto.NotificationListResponse;

/** ① 알림 */
public interface NotificationService {

    /** 회원에게 알림 하나 보내기 (종류 info, 누를 곳 없음) */
    void send(long memberId, String title, String text);

    /** 알림 하나 보내기 (④ 여행 중 알림·대안 적용이 부름). noticeKey가 같으면 한 번만 쌓임 */
    void send(Notification notification);

    /** 내 알림함 (최근 50개 + 안 읽은 수). 열 때 내 여행을 보고 일정 알림(내일 여행·오늘 여행·돌아보기)을 만들어 둠 */
    NotificationListResponse getNotificationList(long memberId);

    /** 알림 하나 읽음 */
    void readNotification(long memberId, long notificationId);

    /** 내 알림 모두 읽음 */
    void readAllNotifications(long memberId);
}
