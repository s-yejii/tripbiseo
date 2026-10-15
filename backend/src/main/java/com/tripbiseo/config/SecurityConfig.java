package com.tripbiseo.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletResponse;

/**
 * 스프링 시큐리티 설정 (Vue 화면용 JSON API + 세션 쿠키)
 *  로그인 없이 : 로그인·회원가입, 지도 키, /api/chat·/db-test(팀 테스트용)
 *  관리자      : /api/admin/** (member.role = 'ROLE_ADMIN')
 *  그 밖       : 로그인 필요 → 401 JSON (로그인 페이지로 튕기지 않음, 화면이 처리)
 */
@Configuration
public class SecurityConfig {

    private static final String[] PUBLIC_URLS = {
            "/api/auth/**", "/api/config/public",
            "/error",
            "/db-test", // DB 연결 확인용 (개발 단계)
            "/api/chat" // 제미나이 테스트용
    };

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            // TODO: 배포 전 CSRF 적용 검토 (지금은 Vite 프록시로 같은 주소에서만 호출)
            .csrf(AbstractHttpConfigurer::disable)
            .httpBasic(AbstractHttpConfigurer::disable)
            .formLogin(AbstractHttpConfigurer::disable)   // 로그인은 /api/auth/login (JSON)
            .logout(AbstractHttpConfigurer::disable)      // 로그아웃은 /api/auth/logout
            .authorizeHttpRequests(auth -> auth
                .dispatcherTypeMatchers(DispatcherType.FORWARD, DispatcherType.ERROR).permitAll()
                .requestMatchers(PUBLIC_URLS).permitAll()
                .requestMatchers("/api/admin/**").hasRole("ADMIN")
                .anyRequest().authenticated()
            )
            .exceptionHandling(e -> e
                .authenticationEntryPoint((req, res, ex) -> json(res, 401, "로그인이 필요해요."))
                .accessDeniedHandler((req, res, ex) -> json(res, 403, "관리자만 쓸 수 있어요."))
            );
        return http.build();
    }

    /** AuthController가 아이디·비밀번호 확인에 씀 (CustomUserDetailsService + BCrypt) */
    @Bean
    public AuthenticationManager authenticationManager(UserDetailsService users, PasswordEncoder encoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(users);
        provider.setPasswordEncoder(encoder);
        return new ProviderManager(provider);
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    private static void json(HttpServletResponse res, int status, String message) throws java.io.IOException {
        res.setStatus(status);
        res.setContentType("application/json;charset=UTF-8");
        res.getWriter().write("{\"message\":\"" + message + "\"}");
    }
}
