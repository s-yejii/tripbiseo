package com.tripbiseo.member.service;

import com.tripbiseo.member.dto.Member;
import com.tripbiseo.member.dto.MemberStyle;
import com.tripbiseo.member.dto.SignupRequest;

/** ① 회원 */
public interface MemberService {

    /** 아이디로 회원 한 명 (없으면 null) */
    Member getMember(String username);

    /** 회원가입: 입력 검사 + BCrypt로 저장. 잘못되면 400, 아이디가 있으면 409 */
    void createMember(SignupRequest request);

    /** 여행 스타일 (③ AI 일정이 부름) */
    MemberStyle getStyle(long memberId);
}
