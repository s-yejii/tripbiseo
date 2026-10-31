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
    private String role;     // ROLE_USER / ROLE_ADMIN
    private LocalDateTime createdAt;
}
