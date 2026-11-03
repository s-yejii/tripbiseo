package com.tripbiseo.member.service;

import com.tripbiseo.member.dto.EmailCodeResponse;

/** ① 이메일 인증번호 (회원가입·계정 찾기). clientId는 브라우저 세션 id: 인증한 사람만 그 결과를 쓰게 함 */
public interface EmailCodeService {

    /** 인증번호를 만들어 메일로 보냄. purpose: signup(가입 안 된 이메일만) / find(가입된 이메일만, username이 있으면 짝 확인) */
    EmailCodeResponse sendCode(String email, String purpose, String username, String clientId);

    /** 인증번호 확인. 맞으면 30분 동안 "인증됨" (틀리면 400, 5번 틀리면 번호를 다시 받아야 함) */
    void verifyCode(String email, String code, String purpose, String clientId);

    /** 이 브라우저에서 이 이메일을 이 목적으로 인증했는지 */
    boolean isVerified(String email, String purpose, String clientId);

    /** 다 쓴 인증 지우기 (가입·비밀번호 바꾸기 뒤) */
    void clearVerified(String email, String purpose);
}
