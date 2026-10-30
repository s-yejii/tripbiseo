package com.tripbiseo.member.dto;

import lombok.Data;

/** POST /api/auth/login 요청 */
@Data
public class LoginRequest {
    private String username;
    private String password;
}
