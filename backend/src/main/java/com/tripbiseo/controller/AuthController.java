package com.tripbiseo.controller;

import static com.tripbiseo.common.ApiException.fail;

import java.security.Principal;
import java.util.HashMap;
import java.util.Map;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.tripbiseo.dto.Member;
import com.tripbiseo.mapper.MemberMapper;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

/**
 * 로그인·회원가입 (Spring Security). 팀 member 테이블 + BCrypt 그대로.
 * 폼 로그인 대신 JSON으로 받고, 성공하면 세션 쿠키(JSESSIONID)로 로그인 상태를 유지합니다.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthenticationManager authManager;
    private final MemberMapper members;
    private final PasswordEncoder encoder;
    private final SecurityContextRepository contextRepo = new HttpSessionSecurityContextRepository();

    public AuthController(AuthenticationManager authManager, MemberMapper members, PasswordEncoder encoder) {
        this.authManager = authManager;
        this.members = members;
        this.encoder = encoder;
    }

    @PostMapping("/login")
    public Map<String, Object> login(@RequestBody Map<String, String> body, HttpServletRequest req, HttpServletResponse res) {
        String username = body.getOrDefault("username", "").trim(), password = body.getOrDefault("password", "");
        if (username.isEmpty() || password.isEmpty()) throw fail(400, "아이디와 비밀번호를 입력해 주세요.");
        Authentication auth;
        try {
            auth = authManager.authenticate(UsernamePasswordAuthenticationToken.unauthenticated(username, password));
        } catch (AuthenticationException e) {
            throw fail(401, "아이디 또는 비밀번호가 맞지 않아요.");
        }
        HttpSession old = req.getSession(false);
        if (old != null) req.changeSessionId(); // 세션 고정 공격 방지
        SecurityContext ctx = SecurityContextHolder.createEmptyContext();
        ctx.setAuthentication(auth);
        SecurityContextHolder.setContext(ctx);
        contextRepo.saveContext(ctx, req, res);
        return userOf(username);
    }

    /** 새로고침 후 로그인 상태 확인 (로그인 안 했으면 401) */
    @GetMapping("/me")
    public Map<String, Object> me(Principal user) {
        if (user == null) throw fail(401, "로그인이 필요해요.");
        return userOf(user.getName());
    }

    @PostMapping("/logout")
    public Map<String, Object> logout(HttpServletRequest req) {
        HttpSession s = req.getSession(false);
        if (s != null) s.invalidate();
        SecurityContextHolder.clearContext();
        return Map.of("ok", true);
    }

    @PostMapping("/signup")
    public Map<String, Object> signup(@RequestBody Map<String, String> body) {
        String username = body.getOrDefault("username", "").trim(), password = body.getOrDefault("password", "");
        String nickname = body.getOrDefault("nickname", "").trim();
        if (!username.matches("[A-Za-z0-9_]{4,30}")) throw fail(400, "아이디는 영문·숫자·_ 4~30자예요.");
        if (password.length() < 8 || password.length() > 72) throw fail(400, "비밀번호는 8~72자예요.");
        if (nickname.length() > 20) throw fail(400, "닉네임은 20자까지예요.");
        if (members.findByUsername(username) != null) throw fail(409, "이미 쓰는 아이디예요.");
        Member m = new Member();
        m.setUsername(username);
        m.setPassword(encoder.encode(password));
        m.setNickname(nickname.isEmpty() ? username : nickname);
        try {
            members.insert(m);
        } catch (DuplicateKeyException e) {
            throw fail(409, "이미 쓰는 아이디예요.");
        }
        return Map.of("ok", true);
    }

    private Map<String, Object> userOf(String username) {
        Member m = members.findByUsername(username);
        if (m == null) throw fail(401, "회원 정보를 찾을 수 없어요.");
        Map<String, Object> user = new HashMap<>(); // nickname이 null일 수 있어서 Map.of 대신 HashMap
        user.put("username", m.getUsername());
        user.put("nickname", m.getNickname());
        user.put("admin", "ROLE_ADMIN".equals(m.getRole()));
        return user;
    }
}
