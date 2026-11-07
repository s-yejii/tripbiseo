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

-- ---------------------------------------------------------------- payment · 결제 내역 (11/07 박석진, DB 총괄 이상재 확인)
--  stop_id는 결제 시각에 있던 일정 칸(실제 도착 ~ 떠남)으로 서버가 붙여요. NULL이면 이동 중·계획 밖
--  source: manual(직접 입력) / card(카드 내역 가져옴, 지금은 목업 → CODEF) / capture(결제 캡처, 나중에)
CREATE TABLE IF NOT EXISTS payment (
  payment_id  BIGSERIAL PRIMARY KEY,
  trip_id     BIGINT NOT NULL REFERENCES trip(trip_id) ON DELETE CASCADE,
  stop_id     BIGINT REFERENCES trip_stop(stop_id) ON DELETE SET NULL,
  paid_at     TIMESTAMP NOT NULL,
  store_name  VARCHAR(100) NOT NULL,
  amount      INT NOT NULL CHECK (amount > 0),
  method      VARCHAR(10) NOT NULL DEFAULT 'card',
  source      VARCHAR(10) NOT NULL DEFAULT 'manual',
  memo        VARCHAR(200),
  created_at  TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_payment_trip ON payment (trip_id, paid_at);

-- 11/07 박석진: 정산용 "낸 사람" (나 / 동행 1 / 동행 2 …). 이미 만든 payment에 칸만 추가
ALTER TABLE payment ADD COLUMN IF NOT EXISTS payer VARCHAR(20) NOT NULL DEFAULT '나';

-- ---------------------------------------------------------------- trip_companion · 같이 간 사람 (11/07 박석진, 이상재 확인)
--  정산의 "낸 사람" 목록. sort_no 0은 '나'. 처음 열 때 여행 인원만큼(나, 동행 1 …) 만들어 둠
--  friend_member_id: 친구 기능(①)이 생기면 친구 회원 번호. 지금은 NULL
CREATE TABLE IF NOT EXISTS trip_companion (
  companion_id     BIGSERIAL PRIMARY KEY,
  trip_id          BIGINT NOT NULL REFERENCES trip(trip_id) ON DELETE CASCADE,
  name             VARCHAR(20) NOT NULL,
  friend_member_id BIGINT REFERENCES member(member_id) ON DELETE SET NULL,
  sort_no          INT NOT NULL DEFAULT 0,
  created_at       TIMESTAMP NOT NULL DEFAULT NOW(),
  UNIQUE (trip_id, name)
);

-- ---------------------------------------------------------------- card · 카드사 연결 (11/07 박석진, 이상재 확인)
--  CODEF 카드사 연결 번호(connected_id)만 저장. 카드사 아이디·비밀번호·카드 번호는 저장하지 않음
CREATE TABLE IF NOT EXISTS card (
  card_id           BIGSERIAL PRIMARY KEY,
  member_id         BIGINT NOT NULL REFERENCES member(member_id) ON DELETE CASCADE,
  organization      VARCHAR(4) NOT NULL,
  organization_name VARCHAR(20) NOT NULL,
  connected_id      VARCHAR(100) NOT NULL,
  created_at        TIMESTAMP NOT NULL DEFAULT NOW(),
  UNIQUE (member_id, organization)
);
