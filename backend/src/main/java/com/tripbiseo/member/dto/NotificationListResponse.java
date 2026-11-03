package com.tripbiseo.member.dto;

import java.util.ArrayList;
import java.util.List;

import lombok.Data;

/** 알림함 (GET /api/notifications) */
@Data
public class NotificationListResponse {
    private int unreadCount;   // 안 읽은 알림 수 (홈 종 아이콘 점)
    private List<Notification> notifications = new ArrayList<>(); // 최근 것부터
}
