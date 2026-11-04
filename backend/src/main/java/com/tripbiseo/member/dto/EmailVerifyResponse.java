package com.tripbiseo.member.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.Data;

/** POST /api/auth/email-code/verify 응답. find(계정 찾기)면 그 이메일의 아이디도 알려 줌 */
@Data
public class EmailVerifyResponse {
    private boolean verified;
    @JsonInclude(JsonInclude.Include.NON_NULL) // signup이면 키가 없음
    private String username;
}
