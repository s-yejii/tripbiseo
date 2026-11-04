package com.tripbiseo.member.dto;

import lombok.Data;

/** POST /api/auth/email-code 응답: 인증번호가 몇 초 동안 유효한지, 몇 초 뒤에 다시 받을 수 있는지 */
@Data
public class EmailCodeResponse {
    private int expiresInSec;
    private int resendAfterSec;
}
