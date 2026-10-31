package com.tripbiseo.member.mapper;

import org.apache.ibatis.annotations.Mapper;

import com.tripbiseo.member.dto.Member;

/** 회원 (member 테이블). SQL은 resources/mapper/member/MemberMapper.xml */
@Mapper
public interface MemberMapper {

    /** 아이디로 회원 한 명 (없으면 null) */
    Member selectMemberByUsername(String username);

    /** 회원가입 */
    int insertMember(Member member);
}
