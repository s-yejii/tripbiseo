-- 12_trip.sql — ② 이찬기 (여행·일정·점검·장소)
--  trip·trip_stop·retrip_place는 공통 테이블이라 01_common.sql에 있어요.
--  더 만들 테이블: pack_item(여유). 만들기 전에 docs/DB.md에 적고 DB 총괄 확인. 다시 실행해도 안전하게 (IF NOT EXISTS)

-- ---------------------------------------------------------------- trip_chat · 대화 기록 (AI 계획 대화 + 여행 중 대화)
CREATE TABLE IF NOT EXISTS trip_chat (
  chat_id     BIGSERIAL PRIMARY KEY,
  trip_id     BIGINT        NOT NULL REFERENCES trip(trip_id) ON DELETE CASCADE,
  role        VARCHAR(2)    NOT NULL CHECK (role IN ('me', 'ai')),
  text        VARCHAR(2000) NOT NULL,
  created_at  TIMESTAMP     NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_chat_trip ON trip_chat (trip_id, created_at);
