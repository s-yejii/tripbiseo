package com.tripbiseo.member.dto;

import java.time.LocalDateTime;

import lombok.Data;

/** 회원 (member 테이블 한 줄) */
@Data
public class Member {
    private Long memberId;
    private String username;
    private String password; // BCrypt 암호문
    private String nickname;
    private String email;    // 가입할 때 인증한 이메일 (11/03 추가, 예전 회원은 null)
    private String role;     // ROLE_USER / ROLE_ADMIN
    private LocalDateTime createdAt;
}
