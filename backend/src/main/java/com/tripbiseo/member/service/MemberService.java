package com.tripbiseo.member.service;

import com.tripbiseo.member.dto.Member;
import com.tripbiseo.member.dto.MemberStyle;
import com.tripbiseo.member.dto.SignupRequest;

/** ① 회원 */
public interface MemberService {

    /** 아이디로 회원 한 명 (없으면 null) */
    Member getMember(String username);

    /** 회원가입: 입력 검사 + BCrypt로 저장. 잘못되면 400, 아이디·이메일이 있으면 409 (이메일 인증은 Controller가 먼저 확인) */
    void createMember(SignupRequest request);

    /** 아이디를 쓸 수 있는지 (규칙에 안 맞으면 400) */
    boolean isUsernameAvailable(String username);

    /** 이메일로 회원 한 명 (없으면 null) */
    Member getMemberByEmail(String email);

    /** 계정 찾기: 이메일 인증을 마친 회원의 비밀번호를 새로 정함 (8~72자) */
    void resetPassword(String email, String newPassword);

    /** 회원 번호로 한 명. 없으면 404 */
    Member getMember(long memberId);

    /** 닉네임 바꾸기 (1~20자). 바뀐 회원을 돌려줌 */
    Member updateNickname(long memberId, String nickname);

    /** 비밀번호 바꾸기: 지금 비밀번호가 맞아야 하고, 새 비밀번호는 8~72자 */
    void changePassword(long memberId, String currentPassword, String newPassword);

    /** 여행 스타일 (③ AI 일정이 부름) */
    MemberStyle getStyle(long memberId);
}
