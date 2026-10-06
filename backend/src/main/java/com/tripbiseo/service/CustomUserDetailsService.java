package com.tripbiseo.service;

import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import com.tripbiseo.dto.Member;
import com.tripbiseo.mapper.MemberMapper;

@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final MemberMapper memberMapper;

    public CustomUserDetailsService(MemberMapper memberMapper) {
        this.memberMapper = memberMapper;
    }

    // 로그인 버튼을 누르면 Security가 자동으로 호출
    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        Member member = memberMapper.findByUsername(username);
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