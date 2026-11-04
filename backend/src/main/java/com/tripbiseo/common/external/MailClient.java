package com.tripbiseo.common.external;

import static com.tripbiseo.common.exception.ApiException.fail;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

/**
 * 메일 보내기 (Gmail SMTP). 보내는 계정은 MAIL_USERNAME, 비밀번호는 Gmail "앱 비밀번호" MAIL_PASSWORD
 * (application-local.properties). 받는 사람은 사용자가 적은 이메일.
 */
@Component
@RequiredArgsConstructor
public class MailClient {

    private static final Logger log = LoggerFactory.getLogger(MailClient.class);

    // JavaMailSender: Spring이 spring.mail.* 설정으로 만들어 주는 "메일 보내는 도구"
    private final JavaMailSender mailSender;

    @Value("${MAIL_USERNAME:}")
    private String from;

    /** 보내는 계정이 설정돼 있는지 */
    public boolean isReady() {
        return !from.isBlank();
    }

    /** 글자 메일 한 통 보내기. 실패하면 이유를 알 수 있는 문장으로 오류 */
    public void send(String to, String subject, String text) {
        if (!isReady()) {
            throw fail(503, "메일 계정이 설정되지 않았어요. MAIL_USERNAME·MAIL_PASSWORD를 확인해 주세요.");
        }
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(to);
        message.setSubject(subject);
        message.setText(text);
        try {
            mailSender.send(message);
        } catch (MailAuthenticationException e) {
            log.warn("메일 계정 로그인 실패: {}", e.getMessage());
            throw fail(502, "메일 계정 로그인에 실패했어요. MAIL_PASSWORD에 Gmail 앱 비밀번호(16자)를 넣었는지 확인해 주세요.");
        } catch (MailException e) {
            log.warn("메일 보내기 실패: {}", e.getMessage());
            throw fail(502, "인증 메일을 보내지 못했어요. 잠시 뒤 다시 시도해 주세요.");
        }
    }
}
