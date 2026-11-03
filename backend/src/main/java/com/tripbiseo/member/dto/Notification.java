package com.tripbiseo.member.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;

import lombok.Data;

/** 알림 하나 (DB notification). ④⑤는 이 모양으로 NotificationService.send(notification)를 불러요 */
@Data
public class Notification {
    private Long id;
    @JsonIgnore // 화면에는 안 보냄 (내 알림만 보여 주니까)
    private Long memberId;
    private String kind;       // trip(여행 중) / plan(일정) / info(날씨·안내)
    private String level;      // ok / info / warn / danger (아이콘 색)
    private String title;      // 100자까지
    private String text;       // 300자까지 (없어도 됨)
    private String link;       // 누르면 갈 화면 주소 (예: /live)
    @JsonIgnore
    private String noticeKey;  // 같은 알림 한 번만 쌓기 (null이면 매번 쌓음)
    private String readAt;     // 읽은 시각 yyyy-MM-ddTHH:mm:ss (null이면 안 읽음)
    private String createdAt;  // 만든 시각 yyyy-MM-ddTHH:mm:ss
}
