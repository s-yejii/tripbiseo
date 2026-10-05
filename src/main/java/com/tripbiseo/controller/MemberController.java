package com.tripbiseo.controller;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;

import com.tripbiseo.dto.Member;
import com.tripbiseo.mapper.MemberMapper;

@Controller
public class MemberController {

    private final MemberMapper memberMapper;
    private final PasswordEncoder passwordEncoder;

    public MemberController(MemberMapper memberMapper, PasswordEncoder passwordEncoder) {
        this.memberMapper = memberMapper;
        this.passwordEncoder = passwordEncoder;
    }

    @PostMapping("/signup")
    public String signup(Member member) {
        // 아이디 중복 체크
        if (memberMapper.findByUsername(member.getUsername()) != null) {
            return "redirect:/signup.html?error=duplicate";
        }
        member.setPassword(passwordEncoder.encode(member.getPassword()));  // 비밀번호 암호화
        memberMapper.insert(member);
        return "redirect:/login";
    }
}