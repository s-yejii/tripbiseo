-- 트립비서 회원 (Spring Security 로그인)
--  role: ROLE_USER(기본) / ROLE_ADMIN(API 키 설정·장소 가져오기·테마 등록)
--  관리자 만들기: 회원가입 후  UPDATE member SET role='ROLE_ADMIN' WHERE username='아이디';
CREATE TABLE IF NOT EXISTS member (
  member_id  BIGSERIAL PRIMARY KEY,
  username   VARCHAR(50)  NOT NULL UNIQUE,
  password   VARCHAR(100) NOT NULL,              -- BCrypt 암호문
  nickname   VARCHAR(50),
  role       VARCHAR(20)  NOT NULL DEFAULT 'ROLE_USER',
  created_at TIMESTAMP    NOT NULL DEFAULT NOW()
);
