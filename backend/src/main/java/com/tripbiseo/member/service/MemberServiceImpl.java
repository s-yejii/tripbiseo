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
    private static final String USERNAME_PATTERN = "[A-Za-z0-9_]{4,30}"; // 아이디 규칙
    private static final String DEMO_PREFIX = "demo_"; // 시연용 가상 회원(db/95_demo_seed.sql) 아이디 앞글자. 실제 가입은 못 쓰게


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
        String email = trim(request.getEmail()).toLowerCase();
        if (!username.matches(USERNAME_PATTERN)) {
            throw fail(400, "아이디는 영문·숫자·_ 4~30자예요.");
        }
        checkNotDemo(username);
        if (password.length() < PASSWORD_MIN || password.length() > PASSWORD_MAX) {
            throw fail(400, "비밀번호는 8~72자예요.");
        }
        if (nickname.length() > NICKNAME_MAX) {
            throw fail(400, "닉네임은 20자까지예요.");
        }
        if (memberMapper.selectMemberByUsername(username) != null) {
            throw fail(409, "이미 쓰는 아이디예요.");
        }
        if (email.isEmpty()) {
            throw fail(400, "이메일 인증을 먼저 해 주세요.");
        }
        if (memberMapper.selectMemberByEmail(email) != null) {
            throw fail(409, "이미 가입된 이메일이에요.");
        }
        Member member = new Member();
        member.setUsername(username);
        member.setPassword(passwordEncoder.encode(password));
        member.setNickname(nickname.isEmpty() ? username : nickname);
        member.setEmail(email);
        try {
            memberMapper.insertMember(member);
        } catch (DuplicateKeyException e) { // 거의 동시에 같은 아이디·이메일로 가입한 경우
            throw fail(409, "이미 쓰는 아이디나 이메일이에요.");
        }
    }

    /** 아이디를 쓸 수 있는지: 규칙 검사 → DB에 같은 아이디가 없으면 true */
    @Override
    public boolean isUsernameAvailable(String username) {
        String name = trim(username);
        if (!name.matches(USERNAME_PATTERN)) {
            throw fail(400, "아이디는 영문·숫자·_ 4~30자예요.");
        }
        checkNotDemo(name);
        return memberMapper.selectMemberByUsername(name) == null;
    }

    /** 이메일로 회원 한 명 (없으면 null) */
    @Override
    public Member getMemberByEmail(String email) {
        return email == null ? null : memberMapper.selectMemberByEmail(email.trim().toLowerCase());
    }

    /** 계정 찾기: 새 비밀번호 규칙 검사 → 암호문으로 저장 */
    @Override
    public void resetPassword(String email, String newPassword) {
        String next = newPassword == null ? "" : newPassword;
        Member member = getMemberByEmail(email);
        if (member == null) {
            throw fail(404, "이 이메일로 가입한 회원이 없어요.");
        }
        if (next.length() < PASSWORD_MIN || next.length() > PASSWORD_MAX) {
            throw fail(400, "비밀번호는 8~72자예요.");
        }
        memberMapper.updatePassword(member.getMemberId(), passwordEncoder.encode(next));
    }

    /** 회원 번호로 한 명. 없으면 404 */
    @Override
    public Member getMember(long memberId) {
        Member member = memberMapper.selectMember(memberId);
        if (member == null) {
            throw fail(404, "회원 정보를 찾을 수 없어요.");
        }
        return member;
    }

    /** 닉네임 바꾸기 (앞뒤 공백 빼고 1~20자) */
    @Override
    public Member updateNickname(long memberId, String nickname) {
        String name = trim(nickname);
        if (name.isEmpty() || name.length() > NICKNAME_MAX) {
            throw fail(400, "이름을 1~20자로 입력해 주세요.");
        }
        getMember(memberId);
        memberMapper.updateNickname(memberId, name);
        return getMember(memberId);
    }

    /** 비밀번호 바꾸기: 지금 비밀번호 확인(BCrypt 비교) → 새 비밀번호 규칙 검사 → 암호문으로 저장 */
    @Override
    public void changePassword(long memberId, String currentPassword, String newPassword) {
        String current = currentPassword == null ? "" : currentPassword;
        String next = newPassword == null ? "" : newPassword;
        Member member = getMember(memberId);
        if (!passwordEncoder.matches(current, member.getPassword())) {
            throw fail(400, "지금 비밀번호가 맞지 않아요.");
        }
        if (next.length() < PASSWORD_MIN || next.length() > PASSWORD_MAX) {
            throw fail(400, "비밀번호는 8~72자예요.");
        }
        if (next.equals(current)) {
            throw fail(400, "지금 비밀번호와 다른 비밀번호로 정해 주세요.");
        }
        memberMapper.updatePassword(memberId, passwordEncoder.encode(next));
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

    // demo_로 시작하는 아이디는 시연 데이터 지우기(DELETE ... LIKE 'demo\_%')에 같이 지워져서 막아요
    private static void checkNotDemo(String username) {
        if (username.toLowerCase().startsWith(DEMO_PREFIX)) {
            throw fail(400, "demo_로 시작하는 아이디는 쓸 수 없어요.");
        }
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }
}
