package com.tripbiseo.member.service;

/** ① 알림 */
public interface NotificationService {

    /** 회원에게 알림 하나 보내기 (④ 지연·출발 알림, ⑤ 결제 알림이 부름) */
    void send(long memberId, String title, String text);
}
