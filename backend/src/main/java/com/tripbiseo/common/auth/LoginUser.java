package com.tripbiseo.common.auth;

import static com.tripbiseo.common.exception.ApiException.fail;

import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpSession;

/**
 * 지금 로그인한 회원 번호를 꺼내는 곳.
 * 로그인할 때 세션에 member_id를 넣어 두고, 요청마다 DB를 다시 조회하지 않고 세션에서 바로 꺼냅니다.
 */
@Component
public class LoginUser {

    /** 세션에 회원 번호를 넣을 때 쓰는 이름 */
    public static final String SESSION_KEY = "memberId";

    /** 지금 로그인한 회원 번호. 로그인 안 했으면 401 "로그인이 필요해요." */
    public long id() {
        HttpSession session = currentSession();
        Object memberId = session == null ? null : session.getAttribute(SESSION_KEY);
        if (!(memberId instanceof Long)) {
            throw fail(401, "로그인이 필요해요.");
        }
        return (Long) memberId;
    }

    /** 로그인 성공 때 세션에 회원 번호를 넣음 (AuthController가 부름) */
    public void save(HttpSession session, long memberId) {
        session.setAttribute(SESSION_KEY, memberId);
    }

    // RequestContextHolder: 지금 처리 중인 요청(request)을 어디서든 꺼내 주는 Spring 도구
    private HttpSession currentSession() {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            return null;
        }
        return attributes.getRequest().getSession(false);
    }
}
