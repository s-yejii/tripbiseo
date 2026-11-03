package com.tripbiseo.member.dto;

import lombok.Data;

/** POST /api/auth/email-code/verify 요청 */
@Data
public class EmailVerifyRequest {
    private String email;
    private String code;    // 메일로 받은 6자리
    private String purpose; // signup / find
}
