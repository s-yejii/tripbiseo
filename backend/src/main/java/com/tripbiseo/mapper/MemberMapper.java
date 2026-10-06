package com.tripbiseo.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import com.tripbiseo.dto.Member;

@Mapper
public interface MemberMapper {

    // 로그인할 때 아이디로 회원 찾기
    @Select("SELECT * FROM member WHERE username = #{username}")
    Member findByUsername(String username);

    // 회원가입
    @Insert("INSERT INTO member (username, password, nickname) " +
            "VALUES (#{username}, #{password}, #{nickname})")
    int insert(Member member);
}