package com.tripbiseo.member.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.tripbiseo.member.dto.Member;

/** 회원 (member 테이블). SQL은 resources/mapper/member/MemberMapper.xml */
@Mapper
public interface MemberMapper {

    /** 아이디로 회원 한 명 (없으면 null) */
    Member selectMemberByUsername(String username);

    /** 회원가입 */
    int insertMember(Member member);

    /** 이메일로 한 명 (없으면 null) */
    Member selectMemberByEmail(String email);

    /** 회원 번호로 한 명 (없으면 null) */
    Member selectMember(long memberId);

    /** 닉네임 바꾸기 */
    int updateNickname(@Param("memberId") long memberId, @Param("nickname") String nickname);

    /** 비밀번호 바꾸기 (BCrypt 암호문) */
    int updatePassword(@Param("memberId") long memberId, @Param("password") String password);
}
