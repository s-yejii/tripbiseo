-- 13_plan.sql — ③ 김주형 (AI 일정·테마)
--  테이블: theme, theme_stop, vector_store(여유)
--  만들기 전에 docs/DB.md "각자 채울 칸"에 컬럼을 적고 DB 총괄 확인을 받아요. 다시 실행해도 안전하게 (IF NOT EXISTS)

-- 2026-11-06 ③ 김주형: 내 장소 (docs/DB.md my_place)
--   친구 소식·장소 상세에서 "저장"한 곳. 식당·카페·놀거리 나누기는 저장하지 않고 읽을 때 retrip_place 분류로 정해요
--   (member_id, place_id) UNIQUE → 같은 곳은 한 번만
CREATE TABLE IF NOT EXISTS my_place (
  my_place_id BIGSERIAL   PRIMARY KEY,
  member_id   BIGINT      NOT NULL REFERENCES member(member_id) ON DELETE CASCADE,
  place_id    TEXT        NOT NULL REFERENCES retrip_place(id) ON DELETE CASCADE,
  from_name   VARCHAR(50),                       -- 누구 소식에서 저장했는지 (예: 민지). 직접 저장이면 NULL
  created_at  TIMESTAMP   NOT NULL DEFAULT NOW(),
  UNIQUE (member_id, place_id)
);
CREATE INDEX IF NOT EXISTS idx_my_place_member ON my_place (member_id, created_at DESC);
