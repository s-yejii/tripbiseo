package com.tripbiseo.member.dto;

import lombok.Data;

/** POST /api/auth/login, GET /api/auth/me 응답 {username, nickname, admin} */
@Data
public class LoginResponse {
    private String username;
    private String nickname; // null 가능
    private boolean admin;   // 관리자(ROLE_ADMIN)인지
}
