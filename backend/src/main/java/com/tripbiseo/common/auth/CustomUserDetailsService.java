package com.tripbiseo.common.auth;

import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import com.tripbiseo.member.dto.Member;
import com.tripbiseo.member.service.MemberService;

import lombok.RequiredArgsConstructor;

/**
 * 로그인할 때 Spring Security가 부르는 곳: 아이디로 회원을 찾아 비밀번호(BCrypt)를 비교하게 넘김.
 * UserDetailsService는 Spring Security가 정한 인터페이스라 우리 Service 규칙(인터페이스 + Impl)과 따로 둠.
 */
@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final MemberService memberService;

    /** 아이디로 회원 찾기 (없으면 로그인 실패) */
    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        Member member = memberService.getMember(username);
        if (member == null) {
            throw new UsernameNotFoundException("회원 없음: " + username);
        }
        return User.builder()
                .username(member.getUsername())
                .password(member.getPassword())   // DB 암호문과 BCrypt로 비교
                .authorities(member.getRole())    // ROLE_USER
                .build();
    }
}
