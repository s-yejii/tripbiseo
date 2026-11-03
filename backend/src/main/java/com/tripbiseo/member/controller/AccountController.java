package com.tripbiseo.member.controller;

import static com.tripbiseo.common.exception.ApiException.fail;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.tripbiseo.common.dto.OkResponse;
import com.tripbiseo.member.dto.EmailCodeRequest;
import com.tripbiseo.member.dto.EmailCodeResponse;
import com.tripbiseo.member.dto.EmailVerifyRequest;
import com.tripbiseo.member.dto.EmailVerifyResponse;
import com.tripbiseo.member.dto.Member;
import com.tripbiseo.member.dto.ResetPasswordRequest;
import com.tripbiseo.member.service.EmailCodeService;
import com.tripbiseo.member.service.EmailCodeServiceImpl;
import com.tripbiseo.member.service.MemberService;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;

/**
 * ① 이메일 인증번호 · 계정 찾기 (로그인 전에 쓰는 기능이라 /api/auth 아래, 로그인 불필요).
 * 인증한 브라우저를 알아보려고 세션 id를 같이 넘겨요 (다른 사람이 같은 이메일로 끼어들지 못하게).
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AccountController {

    private final EmailCodeService emailCodeService;
    private final MemberService memberService;

    /** 인증번호 메일 보내기 (purpose: signup / find) */
    @PostMapping("/email-code")
    public EmailCodeResponse sendCode(@RequestBody EmailCodeRequest body, HttpServletRequest request) {
        return emailCodeService.sendCode(body.getEmail(), body.getPurpose(), body.getUsername(), clientId(request));
    }

    /** 인증번호 확인. 계정 찾기(find)면 그 이메일의 아이디도 알려 줌 */
    @PostMapping("/email-code/verify")
    public EmailVerifyResponse verifyCode(@RequestBody EmailVerifyRequest body, HttpServletRequest request) {
        emailCodeService.verifyCode(body.getEmail(), body.getCode(), body.getPurpose(), clientId(request));
        EmailVerifyResponse response = new EmailVerifyResponse();
        response.setVerified(true);
        if (EmailCodeServiceImpl.PURPOSE_FIND.equals(body.getPurpose())) {
            Member member = memberService.getMemberByEmail(body.getEmail());
            response.setUsername(member == null ? null : member.getUsername());
        }
        return response;
    }

    /** 계정 찾기: 이메일 인증을 마친 브라우저에서만 새 비밀번호로 바꿈 */
    @PostMapping("/reset-password")
    public OkResponse resetPassword(@RequestBody ResetPasswordRequest body, HttpServletRequest request) {
        if (!emailCodeService.isVerified(body.getEmail(), EmailCodeServiceImpl.PURPOSE_FIND, clientId(request))) {
            throw fail(400, "이메일 인증을 먼저 해 주세요.");
        }
        memberService.resetPassword(body.getEmail(), body.getNewPassword());
        emailCodeService.clearVerified(body.getEmail(), EmailCodeServiceImpl.PURPOSE_FIND);
        return new OkResponse();
    }

    /** 이 브라우저를 알아보는 값: 세션 id (없으면 새로 만듦) */
    private String clientId(HttpServletRequest request) {
        return request.getSession(true).getId();
    }
}
