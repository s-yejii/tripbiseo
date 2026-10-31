-- 14_live.sql — ④ 신예지 (여행 중 판별)
--  더 필요한 테이블은 docs/DB.md "각자 채울 칸"에 먼저 적고 DB 총괄 확인. 다시 실행해도 안전하게 (IF NOT EXISTS)

-- ---------------------------------------------------------------- trip_track · 여행 중 GPS 기록 (⑤ 회고, ① 위치 기록이 읽음)
--  좌표로 검색하지 않는 테이블이라 lat·lng만 둠 (geom 없음)
CREATE TABLE IF NOT EXISTS trip_track (
  track_id    BIGSERIAL PRIMARY KEY,
  trip_id     BIGINT NOT NULL REFERENCES trip(trip_id) ON DELETE CASCADE,
  lat         DOUBLE PRECISION NOT NULL,
  lng         DOUBLE PRECISION NOT NULL,
  accuracy    DOUBLE PRECISION,
  recorded_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_track_trip ON trip_track (trip_id, recorded_at);
