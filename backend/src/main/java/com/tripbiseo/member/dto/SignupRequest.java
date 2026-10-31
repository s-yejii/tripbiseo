package com.tripbiseo.member.dto;

import lombok.Data;

/** POST /api/auth/signup 요청 */
@Data
public class SignupRequest {
    private String username; // 영문·숫자·_ 4~30자
    private String password; // 8~72자
    private String nickname; // 20자까지, 비우면 아이디
}
