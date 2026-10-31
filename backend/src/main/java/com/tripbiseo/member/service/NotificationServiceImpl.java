package com.tripbiseo.member.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class NotificationServiceImpl implements NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationServiceImpl.class);

    /** 회원에게 알림 하나 보내기 */
    // TODO: ① 알림 저장. notification 테이블(DB.md 작성 예시 참고 → 11_member.sql) → Mapper insertNotification. 지금은 로그만 남김
    @Override
    public void send(long memberId, String title, String text) {
        log.info("[알림 가짜] 회원 {}: {} - {}", memberId, title, text);
    }
}
