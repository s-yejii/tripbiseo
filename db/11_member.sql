-- 11_member.sql — ① 이상재 (회원·홈·알림)
--  테이블: member_style, notification, friend(여유)
--  만들기 전에 docs/DB.md "각자 채울 칸"에 컬럼을 적고 DB 총괄 확인을 받아요. 다시 실행해도 안전하게 (IF NOT EXISTS)

-- 2026-11-03 ① 이상재: 알림함 (docs/DB.md notification)
--   ④ 여행 중 알림·대안 적용, ① 일정 알림(내일 여행·오늘 여행·돌아보기)이 쌓여요
--   (member_id, notice_key) UNIQUE → 같은 알림은 한 번만. notice_key가 NULL이면 매번 쌓임
CREATE TABLE IF NOT EXISTS notification (
  notification_id BIGSERIAL    PRIMARY KEY,
  member_id       BIGINT       NOT NULL REFERENCES member(member_id) ON DELETE CASCADE,
  kind            VARCHAR(10)  NOT NULL,                 -- trip(여행 중) / plan(일정) / info(날씨·안내)
  level           VARCHAR(10)  NOT NULL DEFAULT 'info',  -- ok / info / warn / danger
  title           VARCHAR(100) NOT NULL,
  text            VARCHAR(300),
  link            VARCHAR(200),                          -- 누르면 갈 화면 주소
  notice_key      VARCHAR(100),                          -- 같은 알림 한 번만 쌓기
  read_at         TIMESTAMP,                             -- NULL이면 안 읽음
  created_at      TIMESTAMP    NOT NULL DEFAULT NOW(),
  UNIQUE (member_id, notice_key)
);
CREATE INDEX IF NOT EXISTS idx_notification_member ON notification (member_id, created_at DESC);
