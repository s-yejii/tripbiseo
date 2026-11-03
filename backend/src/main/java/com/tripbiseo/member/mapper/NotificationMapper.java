package com.tripbiseo.member.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.tripbiseo.member.dto.Notification;

/** ① 알림함 (notification 테이블) */
@Mapper
public interface NotificationMapper {

    /** 알림 저장. 같은 회원·같은 noticeKey가 이미 있으면 저장 안 함 → 저장한 줄 수(0 또는 1) */
    int insertNotification(Notification notification);

    /** 내 알림 최근 것부터 limit개 */
    List<Notification> selectNotificationList(@Param("memberId") long memberId, @Param("limit") int limit);

    /** 안 읽은 알림 수 */
    int selectUnreadCount(long memberId);

    /** 알림 하나 읽음 (내 알림만) → 바뀐 줄 수 */
    int updateNotificationRead(@Param("memberId") long memberId, @Param("notificationId") long notificationId);

    /** 내 알림 모두 읽음 → 바뀐 줄 수 */
    int updateNotificationReadAll(long memberId);
}
