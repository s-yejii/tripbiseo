package com.tripbiseo.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

import jakarta.servlet.DispatcherType;

@Configuration
public class SecurityConfig {

    // 로그인 없이 접근 가능한 URL (새 공개 페이지가 생기면 여기에 추가)
    private static final String[] PUBLIC_URLS = {
            "/", "/index.html",                       // 메인
            "/login", "/signup", "/signup.html",      // 로그인·회원가입
            "/css/**", "/js/**", "/images/**", "/favicon.ico",  // 정적 리소스
            "/error",                                 // 에러 페이지
            "/db-test"                                // DB 연결 확인용 (개발 단계)
    };

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            // TODO: 배포 전 CSRF 적용 검토 (현재는 정적 HTML 폼·fetch API 개발 편의를 위해 비활성화)
            .csrf(AbstractHttpConfigurer::disable)
            .httpBasic(AbstractHttpConfigurer::disable)   // 브라우저 기본 인증 팝업 사용 안 함

            .authorizeHttpRequests(auth -> auth
                // 내부 forward(index.html 등)·에러 페이지 이동은 항상 허용
                .dispatcherTypeMatchers(DispatcherType.FORWARD, DispatcherType.ERROR).permitAll()
                .requestMatchers(PUBLIC_URLS).permitAll()
                .anyRequest().authenticated()             // 나머지는 로그인 필요
            )

            .formLogin(form -> form
                .defaultSuccessUrl("/", true)             // 로그인 성공 → 메인
                .permitAll()
            )

            .logout(logout -> logout
                .logoutSuccessUrl("/")                    // 로그아웃 → 메인
                .permitAll()
            );

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}