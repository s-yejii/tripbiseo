-- 15_after.sql — ⑤ 박석진 (여행 후·지도 부가)
--  테이블: payment, card, photo(여유), extra_visit(여유), amenity(여유, 반경 검색 → lat·lng + geom + GIST)
--  만들기 전에 docs/DB.md "각자 채울 칸"에 컬럼을 적고 DB 총괄 확인을 받아요. 다시 실행해도 안전하게 (IF NOT EXISTS)

-- ---------------------------------------------------------------- photo · 사진·영상 (11/06 박석진)
--  파일은 서버 폴더(PHOTO_DIR)에 두고 DB에는 파일 이름만. stop_id가 NULL이면 이동 중에 찍은 것
CREATE TABLE IF NOT EXISTS photo (
  photo_id      BIGSERIAL PRIMARY KEY,
  trip_id       BIGINT NOT NULL REFERENCES trip(trip_id) ON DELETE CASCADE,
  stop_id       BIGINT REFERENCES trip_stop(stop_id) ON DELETE SET NULL,
  media_type    VARCHAR(10) NOT NULL DEFAULT 'photo',
  file_name     VARCHAR(100) NOT NULL,
  original_name VARCHAR(200),
  duration_sec  INT,
  taken_at      TIMESTAMP NOT NULL,
  created_at    TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_photo_trip ON photo (trip_id, taken_at);
