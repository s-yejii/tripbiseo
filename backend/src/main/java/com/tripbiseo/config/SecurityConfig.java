package com.tripbiseo.config;

import java.io.IOException;

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
 *  로그인 없이 : 로그인·회원가입(/api/auth/**), 지도 키(/api/config/public), Swagger 화면
 *  그 밖       : 로그인 필요 → 401 {"message": "로그인이 필요해요."} (로그인 페이지로 튕기지 않음, 화면이 처리)
 */
@Configuration
public class SecurityConfig {

    private static final String[] PUBLIC_URLS = {
            "/api/auth/**", "/api/config/public",
            "/error",
            "/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**" // Swagger (API 문서 화면)
    };

    /** 어떤 주소를 로그인 없이 열지, 로그인 안 했을 때 어떻게 답할지 */
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
                .anyRequest().authenticated()
            )
            .exceptionHandling(e -> e
                .authenticationEntryPoint((request, response, ex) -> writeJson(response, 401, "로그인이 필요해요."))
            );
        return http.build();
    }

    /** AuthController가 아이디·비밀번호 확인에 씀 (CustomUserDetailsService + BCrypt) */
    @Bean
    public AuthenticationManager authenticationManager(UserDetailsService userDetailsService, PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        return new ProviderManager(provider);
    }

    /** 비밀번호를 BCrypt 암호문으로 바꾸고 비교하는 도구 */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    private static void writeJson(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"message\":\"" + message + "\"}");
    }
}
