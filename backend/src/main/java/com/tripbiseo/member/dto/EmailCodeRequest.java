package com.tripbiseo.member.dto;

import lombok.Data;

/** POST /api/auth/email-code 요청 */
@Data
public class EmailCodeRequest {
    private String email;
    private String purpose;  // signup(회원가입) / find(아이디·비밀번호 찾기)
    private String username; // find에서 비밀번호를 찾을 때만: 이 아이디의 이메일이 맞는지 확인
}
