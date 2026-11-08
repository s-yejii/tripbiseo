package com.tripbiseo.member.service;

import static com.tripbiseo.common.exception.ApiException.fail;

import java.security.SecureRandom;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Service;

import com.tripbiseo.common.external.MailClient;
import com.tripbiseo.member.dto.EmailCodeResponse;
import com.tripbiseo.member.dto.Member;

import lombok.RequiredArgsConstructor;

/**
 * 이메일 인증번호. 번호는 DB에 저장하지 않고 서버 메모리(Map)에 5분만 둬요 (서버를 다시 켜면 다시 받으면 됨).
 * 키는 "목적:이메일". 인증한 브라우저(세션 id)만 그 결과로 가입·비밀번호 바꾸기를 할 수 있어요.
 */
@Service
@RequiredArgsConstructor
public class EmailCodeServiceImpl implements EmailCodeService {

    public static final String PURPOSE_SIGNUP = "signup";
    public static final String PURPOSE_FIND = "find";
    private static final List<String> PURPOSES = List.of(PURPOSE_SIGNUP, PURPOSE_FIND);
    private static final int CODE_RANGE = 1_000_000;        // 000000~999999
    private static final long CODE_TTL_MS = 5 * 60_000L;    // 인증번호 유효 5분
    private static final long RESEND_GAP_MS = 60_000L;      // 다시 받기는 1분 뒤부터
    private static final long VERIFIED_TTL_MS = 30 * 60_000L; // 인증한 뒤 30분 안에 가입·변경
    private static final int MAX_TRIES = 5;
    private static final int EMAIL_MAX = 100;
    private static final String EMAIL_PATTERN = "[^\\s@]+@[^\\s@]+\\.[^\\s@]+";

    private final MailClient mailClient;
    private final MemberService memberService;
    private final SecureRandom random = new SecureRandom(); // 맞히기 어려운 난수 (Random보다 안전)
    // ConcurrentHashMap: 여러 요청이 동시에 써도 안전한 Map
    private final Map<String, EmailCode> codes = new ConcurrentHashMap<>();

    /** 인증번호를 만들어 메일로 보냄 */
    @Override
    public EmailCodeResponse sendCode(String email, String purpose, String username, String clientId) {
        String address = normalize(email);
        checkPurpose(purpose);
        checkMember(address, purpose, username);
        String key = purpose + ":" + address;
        long now = System.currentTimeMillis();
        EmailCode old = codes.get(key);
        if (old != null && now - old.sentAt < RESEND_GAP_MS) {
            throw fail(429, "인증번호는 1분에 한 번 받을 수 있어요.");
        }
        String code = String.format("%06d", random.nextInt(CODE_RANGE));
        mailClient.send(address, "[착착트립] 인증번호 " + code, makeMailText(code, purpose));
        codes.put(key, new EmailCode(code, clientId, now));

        EmailCodeResponse response = new EmailCodeResponse();
        response.setExpiresInSec((int) (CODE_TTL_MS / 1000));
        response.setResendAfterSec((int) (RESEND_GAP_MS / 1000));
        return response;
    }

    /** 인증번호 확인 */
    @Override
    public void verifyCode(String email, String code, String purpose, String clientId) {
        String address = normalize(email);
        checkPurpose(purpose);
        String key = purpose + ":" + address;
        EmailCode saved = codes.get(key);
        if (saved == null || saved.code == null || !saved.clientId.equals(clientId)) {
            throw fail(400, "인증번호를 먼저 받아 주세요.");
        }
        if (System.currentTimeMillis() - saved.sentAt > CODE_TTL_MS) {
            codes.remove(key);
            throw fail(400, "인증번호 시간이 지났어요. 다시 받아 주세요.");
        }
        if (!saved.code.equals(code == null ? "" : code.trim())) {
            saved.tries++;
            if (saved.tries >= MAX_TRIES) {
                codes.remove(key);
                throw fail(400, "5번 틀려서 인증번호를 지웠어요. 다시 받아 주세요.");
            }
            throw fail(400, "인증번호가 맞지 않아요. (" + saved.tries + "/" + MAX_TRIES + ")");
        }
        saved.code = null; // 한 번 쓴 번호는 다시 못 씀
        saved.verifiedAt = System.currentTimeMillis();
    }

    /** 이 브라우저에서 인증을 마쳤고 30분이 안 지났는지 */
    @Override
    public boolean isVerified(String email, String purpose, String clientId) {
        EmailCode saved = codes.get(purpose + ":" + normalizeQuietly(email));
        if (saved == null || saved.verifiedAt == 0 || !saved.clientId.equals(clientId)) {
            return false;
        }
        return System.currentTimeMillis() - saved.verifiedAt <= VERIFIED_TTL_MS;
    }

    /** 다 쓴 인증 지우기 */
    @Override
    public void clearVerified(String email, String purpose) {
        codes.remove(purpose + ":" + normalizeQuietly(email));
    }

    // 가입이면 아직 없는 이메일이어야 하고, 찾기면 가입된 이메일이어야 함
    private void checkMember(String address, String purpose, String username) {
        Member member = memberService.getMemberByEmail(address);
        if (PURPOSE_SIGNUP.equals(purpose) && member != null) {
            throw fail(409, "이미 가입된 이메일이에요.");
        }
        if (PURPOSE_FIND.equals(purpose) && member == null) {
            throw fail(404, "이 이메일로 가입한 회원이 없어요.");
        }
        boolean hasUsername = username != null && !username.isBlank();
        if (PURPOSE_FIND.equals(purpose) && hasUsername && !member.getUsername().equals(username.trim())) {
            throw fail(404, "아이디와 이메일이 맞지 않아요.");
        }
    }

    private String makeMailText(String code, String purpose) {
        String what = PURPOSE_SIGNUP.equals(purpose) ? "회원가입" : "아이디·비밀번호 찾기";
        return "착착트립 " + what + " 인증번호는 " + code + " 예요.\n"
             + "5분 안에 화면에 입력해 주세요.\n\n"
             + "직접 요청하지 않았다면 이 메일은 무시해도 돼요.";
    }

    private void checkPurpose(String purpose) {
        if (!PURPOSES.contains(purpose)) {
            throw fail(400, "purpose는 signup·find 중 하나예요.");
        }
    }

    /** 앞뒤 공백 빼고 소문자로 + 형식 검사 */
    private String normalize(String email) {
        String address = normalizeQuietly(email);
        if (address.length() > EMAIL_MAX || !address.matches(EMAIL_PATTERN)) {
            throw fail(400, "이메일 형식이 맞지 않아요.");
        }
        return address;
    }

    private String normalizeQuietly(String email) {
        return email == null ? "" : email.trim().toLowerCase();
    }

    /** 보낸 인증번호 하나 (메모리에만) */
    private static class EmailCode {
        private String code;          // 맞히면 null
        private final String clientId; // 받은 브라우저(세션 id)
        private final long sentAt;
        private int tries;
        private long verifiedAt;      // 맞힌 시각 (0이면 아직)

        EmailCode(String code, String clientId, long sentAt) {
            this.code = code;
            this.clientId = clientId;
            this.sentAt = sentAt;
        }
    }
}
