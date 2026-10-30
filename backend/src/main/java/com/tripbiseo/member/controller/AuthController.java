package com.tripbiseo.member.controller;

import static com.tripbiseo.common.exception.ApiException.fail;

import java.security.Principal;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.InternalAuthenticationServiceException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.tripbiseo.common.auth.LoginUser;
import com.tripbiseo.common.dto.OkResponse;
import com.tripbiseo.member.dto.LoginRequest;
import com.tripbiseo.member.dto.LoginResponse;
import com.tripbiseo.member.dto.Member;
import com.tripbiseo.member.dto.SignupRequest;
import com.tripbiseo.member.service.MemberService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;

/**
 * ① 로그인·회원가입 (Spring Security). member 테이블 + BCrypt.
 * 폼 로그인 대신 JSON으로 받고, 성공하면 세션 쿠키(JSESSIONID)로 로그인 상태를 유지합니다.
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final MemberService memberService;
    private final LoginUser loginUser;
    // 로그인 정보를 세션에 저장하는 Spring Security 도구
    private final SecurityContextRepository contextRepository = new HttpSessionSecurityContextRepository();

    /** 로그인 → 세션 쿠키 발급 + 세션에 회원 번호 저장 */
    @PostMapping("/login")
    public LoginResponse login(@RequestBody LoginRequest body, HttpServletRequest request, HttpServletResponse response) {
        String username = body.getUsername() == null ? "" : body.getUsername().trim();
        String password = body.getPassword() == null ? "" : body.getPassword();
        if (username.isEmpty() || password.isEmpty()) {
            throw fail(400, "아이디와 비밀번호를 입력해 주세요.");
        }
        Authentication auth;
        try {
            auth = authenticationManager.authenticate(UsernamePasswordAuthenticationToken.unauthenticated(username, password));
        } catch (InternalAuthenticationServiceException e) {
            // DB 연결 실패 같은 서버 문제. "비밀번호가 틀림"으로 숨기지 않고 500으로 (로그에 원인이 남음)
            throw new IllegalStateException("로그인 중 서버 오류", e);
        } catch (AuthenticationException e) {
            throw fail(401, "아이디 또는 비밀번호가 맞지 않아요.");
        }
        Member member = findMember(username);
        if (request.getSession(false) != null) {
            request.changeSessionId(); // 세션 고정 공격 방지
        }
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(auth);
        SecurityContextHolder.setContext(context);
        contextRepository.saveContext(context, request, response);
        loginUser.save(request.getSession(), member.getMemberId());
        return toResponse(member);
    }

    /** 새로고침 후 로그인 상태 확인 (로그인 안 했으면 401) */
    @GetMapping("/me")
    public LoginResponse me(Principal user) {
        if (user == null) {
            throw fail(401, "로그인이 필요해요.");
        }
        return toResponse(findMember(user.getName()));
    }

    /** 로그아웃 (세션 삭제) */
    @PostMapping("/logout")
    public OkResponse logout(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        SecurityContextHolder.clearContext();
        return new OkResponse();
    }

    /** 회원가입 (자동 로그인은 안 함, 화면이 이어서 login을 부름) */
    @PostMapping("/signup")
    public OkResponse signup(@RequestBody SignupRequest body) {
        memberService.createMember(body);
        return new OkResponse();
    }

    private Member findMember(String username) {
        Member member = memberService.getMember(username);
        if (member == null) {
            throw fail(401, "회원 정보를 찾을 수 없어요.");
        }
        return member;
    }

    private static LoginResponse toResponse(Member member) {
        LoginResponse response = new LoginResponse();
        response.setUsername(member.getUsername());
        response.setNickname(member.getNickname());
        response.setAdmin("ROLE_ADMIN".equals(member.getRole()));
        return response;
    }
}
