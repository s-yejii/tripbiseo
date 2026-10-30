package com.tripbiseo.member.service;

import static com.tripbiseo.common.exception.ApiException.fail;

import java.util.List;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.tripbiseo.member.dto.Member;
import com.tripbiseo.member.dto.MemberStyle;
import com.tripbiseo.member.dto.SignupRequest;
import com.tripbiseo.member.mapper.MemberMapper;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class MemberServiceImpl implements MemberService {

    private static final int PASSWORD_MIN = 8;
    private static final int PASSWORD_MAX = 72; // BCrypt가 읽는 최대 길이
    private static final int NICKNAME_MAX = 20;

    private final MemberMapper memberMapper;
    private final PasswordEncoder passwordEncoder;

    /** 아이디로 회원 한 명 (없으면 null) */
    @Override
    public Member getMember(String username) {
        return memberMapper.selectMemberByUsername(username);
    }

    /** 회원가입: 입력 검사 + BCrypt로 저장 */
    @Override
    public void createMember(SignupRequest request) {
        String username = trim(request.getUsername());
        String password = request.getPassword() == null ? "" : request.getPassword();
        String nickname = trim(request.getNickname());
        if (!username.matches("[A-Za-z0-9_]{4,30}")) {
            throw fail(400, "아이디는 영문·숫자·_ 4~30자예요.");
        }
        if (password.length() < PASSWORD_MIN || password.length() > PASSWORD_MAX) {
            throw fail(400, "비밀번호는 8~72자예요.");
        }
        if (nickname.length() > NICKNAME_MAX) {
            throw fail(400, "닉네임은 20자까지예요.");
        }
        if (memberMapper.selectMemberByUsername(username) != null) {
            throw fail(409, "이미 쓰는 아이디예요.");
        }
        Member member = new Member();
        member.setUsername(username);
        member.setPassword(passwordEncoder.encode(password));
        member.setNickname(nickname.isEmpty() ? username : nickname);
        try {
            memberMapper.insertMember(member);
        } catch (DuplicateKeyException e) { // 거의 동시에 같은 아이디로 가입한 경우
            throw fail(409, "이미 쓰는 아이디예요.");
        }
    }

    /** 여행 스타일 */
    // TODO: ① 여행 스타일. member_style 테이블(DB.md 등록 → 11_member.sql) → Mapper → 여기서 조회. 저장한 값이 없으면 기본값
    @Override
    public MemberStyle getStyle(long memberId) {
        MemberStyle style = new MemberStyle();
        style.setPace("보통");
        style.setLikes(List.of("카페", "전시"));
        style.setWalk("보통");
        style.setTransports(List.of("subway", "bus", "walk"));
        style.setIndoorWhenRain(true);
        return style;
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }
}
