package com.tripbiseo.member.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.tripbiseo.common.auth.LoginUser;
import com.tripbiseo.common.dto.OkResponse;
import com.tripbiseo.member.dto.LoginResponse;
import com.tripbiseo.member.dto.Member;
import com.tripbiseo.member.dto.PasswordRequest;
import com.tripbiseo.member.dto.ProfileRequest;
import com.tripbiseo.member.service.MemberService;

import lombok.RequiredArgsConstructor;

/** ① 내 정보 보기·수정 (정보 수정 화면). 로그인 필요 */
@RestController
@RequestMapping("/api/members")
@RequiredArgsConstructor
public class MemberController {

    private final MemberService memberService;
    private final LoginUser loginUser;

    /** 내 정보 {username, nickname, admin} (/api/auth/me와 같은 모양) */
    @GetMapping("/me")
    public LoginResponse getMe() {
        return toResponse(memberService.getMember(loginUser.id()));
    }

    /** 이름(닉네임) 바꾸기 → 바뀐 내 정보 */
    @PutMapping("/me")
    public LoginResponse updateMe(@RequestBody ProfileRequest body) {
        return toResponse(memberService.updateNickname(loginUser.id(), body.getNickname()));
    }

    /** 비밀번호 바꾸기 (지금 비밀번호 확인) */
    @PutMapping("/me/password")
    public OkResponse changePassword(@RequestBody PasswordRequest body) {
        memberService.changePassword(loginUser.id(), body.getCurrentPassword(), body.getNewPassword());
        return new OkResponse();
    }

    private static LoginResponse toResponse(Member member) {
        LoginResponse response = new LoginResponse();
        response.setUsername(member.getUsername());
        response.setNickname(member.getNickname());
        response.setAdmin("ROLE_ADMIN".equals(member.getRole()));
        return response;
    }
}
