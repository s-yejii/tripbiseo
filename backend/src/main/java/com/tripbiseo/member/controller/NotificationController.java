package com.tripbiseo.member.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.tripbiseo.common.auth.LoginUser;
import com.tripbiseo.common.dto.OkResponse;
import com.tripbiseo.member.dto.NotificationListResponse;
import com.tripbiseo.member.service.NotificationService;

import lombok.RequiredArgsConstructor;

/** ① 알림함 (알림 센터 화면, 홈 종 아이콘). 로그인 필요 */
@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;
    private final LoginUser loginUser;

    /** 내 알림 최근 50개 + 안 읽은 수 */
    @GetMapping
    public NotificationListResponse getNotificationList() {
        return notificationService.getNotificationList(loginUser.id());
    }

    /** 알림 하나 읽음 */
    @PostMapping("/{id}/read")
    public OkResponse readNotification(@PathVariable long id) {
        notificationService.readNotification(loginUser.id(), id);
        return new OkResponse();
    }

    /** 모두 읽음 */
    @PostMapping("/read-all")
    public OkResponse readAllNotifications() {
        notificationService.readAllNotifications(loginUser.id());
        return new OkResponse();
    }
}
