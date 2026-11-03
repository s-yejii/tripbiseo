package com.tripbiseo.member.dto;

import lombok.Data;

/** POST /api/auth/reset-password 요청 (계정 찾기에서 이메일 인증을 마친 뒤) */
@Data
public class ResetPasswordRequest {
    private String email;
    private String newPassword; // 8~72자
}
