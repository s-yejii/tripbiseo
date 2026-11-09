-- 96_demo_account.sql — 발표 시연 계정 qwer1234 한 명 (11/09 ③ 김주형)
--  다른 PC에서 시연할 때 실행해요. 01·11 다음이면 언제든 OK
--  이미 qwer1234가 있으면 아무것도 바꾸지 않아요 (그 PC의 비밀번호 그대로)
--  비밀번호 원문은 여기에 없어요. 팀 카톡으로만 공유해요 (아래는 BCrypt 암호문)
--  이 계정이 시연 계정이 되는 건 DEMO_ACCOUNTS 기본값(qwer1234) 덕분이에요 (DevServiceImpl)

INSERT INTO member (username, password, nickname, role)
VALUES ('qwer1234', '$2a$10$0dCkNXE2YytyAp59eZ400e6FhAi962FQC9bIGLqclf.GaWKSKYU1C', '여행개발자', 'ROLE_USER')
ON CONFLICT (username) DO NOTHING;
