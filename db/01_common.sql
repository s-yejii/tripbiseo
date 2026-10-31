-- 01_common.sql — 공통 테이블 (DB 총괄 ① 이상재만 고쳐요)
--  member · retrip_place · retrip_review · trip · trip_stop
--  다시 실행해도 안전합니다 (IF NOT EXISTS). 이미 만든 테이블을 바꿀 때는 CREATE를 고치지 말고 맨 아래에 ALTER를 추가해요.
--  실행 순서: 01 → 02 → 03 → 04 → 05 → 11~15 → 90 (→ 예전 DB만 91)

-- PostGIS: 반경·근처 검색(ST_DWithin, geom <-> 점)에 필요
CREATE EXTENSION IF NOT EXISTS postgis;

-- ---------------------------------------------------------------- member · 회원 (Spring Security 로그인)
--  role: ROLE_USER(기본) / ROLE_ADMIN
--  관리자 만들기: 회원가입 후  UPDATE member SET role = 'ROLE_ADMIN' WHERE username = '아이디';
CREATE TABLE IF NOT EXISTS member (
  member_id  BIGSERIAL PRIMARY KEY,
  username   VARCHAR(50)  NOT NULL UNIQUE,
  password   VARCHAR(100) NOT NULL,              -- BCrypt 암호문
  nickname   VARCHAR(50),
  role       VARCHAR(20)  NOT NULL DEFAULT 'ROLE_USER',
  created_at TIMESTAMP    NOT NULL DEFAULT NOW()
);

-- ---------------------------------------------------------------- retrip_place · 장소 (샘플·TourAPI·네이버)
--  이 테이블만 좌표 이름이 latitude·longitude예요. geom은 맨 아래 ALTER로 자동 생성
CREATE TABLE IF NOT EXISTS retrip_place (
  id                TEXT PRIMARY KEY,
  source            TEXT NOT NULL,
  source_id         TEXT NOT NULL,
  name              TEXT NOT NULL,
  category          TEXT,
  content_type_id   TEXT,
  address           TEXT,
  tel               TEXT,
  longitude         DOUBLE PRECISION NOT NULL CHECK (longitude BETWEEN -180 AND 180),
  latitude          DOUBLE PRECISION NOT NULL CHECK (latitude BETWEEN -90 AND 90),
  use_time          TEXT,
  rest_date         TEXT,
  image_url         TEXT,
  biz_type          TEXT,
  rating            DOUBLE PRECISION,
  review_count      INTEGER,
  blog_review_count INTEGER,
  amenities         TEXT,
  map_url           TEXT,
  overview          TEXT,
  created_at        TEXT NOT NULL,
  updated_at        TEXT NOT NULL,
  UNIQUE (source, source_id)
);

-- ---------------------------------------------------------------- retrip_review · 장소 리뷰
CREATE TABLE IF NOT EXISTS retrip_review (
  id         TEXT PRIMARY KEY,
  place_id   TEXT NOT NULL REFERENCES retrip_place(id) ON DELETE CASCADE,
  source     TEXT NOT NULL,
  content    TEXT NOT NULL,
  created_at TEXT NOT NULL
);

-- ---------------------------------------------------------------- trip · 여행 (회원별)
CREATE TABLE IF NOT EXISTS trip (
  trip_id     BIGSERIAL PRIMARY KEY,
  member_id   BIGINT       NOT NULL REFERENCES member(member_id) ON DELETE CASCADE,
  title       VARCHAR(60)  NOT NULL,
  start_date  DATE         NOT NULL,
  end_date    DATE         NOT NULL,
  people      INT          NOT NULL DEFAULT 1 CHECK (people BETWEEN 1 AND 20),
  transports  VARCHAR(60)  NOT NULL DEFAULT 'subway,bus,walk',   -- bus,subway,walk,taxi,car 쉼표로
  status      VARCHAR(10)  NOT NULL DEFAULT 'planned' CHECK (status IN ('planned', 'ongoing', 'done')),
  started_at  TIMESTAMP,
  finished_at TIMESTAMP,
  created_at  TIMESTAMP    NOT NULL DEFAULT NOW(),
  updated_at  TIMESTAMP    NOT NULL DEFAULT NOW(),
  CHECK (end_date >= start_date AND end_date - start_date < 30)
);

-- ---------------------------------------------------------------- trip_stop · 일정 한 칸
--  좌표로 검색하지 않는 테이블이라 lat·lng만 둠 (geom 없음)
CREATE TABLE IF NOT EXISTS trip_stop (
  stop_id     BIGSERIAL PRIMARY KEY,
  trip_id     BIGINT       NOT NULL REFERENCES trip(trip_id) ON DELETE CASCADE,
  day_no      INT          NOT NULL CHECK (day_no BETWEEN 1 AND 30),
  place_id    TEXT         REFERENCES retrip_place(id) ON DELETE SET NULL,  -- DB에 없는 장소면 NULL
  name        VARCHAR(120) NOT NULL,
  category    VARCHAR(60),
  address     VARCHAR(200),
  lat         DOUBLE PRECISION NOT NULL CHECK (lat BETWEEN -90 AND 90),
  lng         DOUBLE PRECISION NOT NULL CHECK (lng BETWEEN -180 AND 180),
  start_time  VARCHAR(5)   NOT NULL CHECK (start_time ~ '^([01][0-9]|2[0-3]):[0-5][0-9]$'),
  stay_min    INT          NOT NULL DEFAULT 60 CHECK (stay_min BETWEEN 0 AND 720),
  fixed       BOOLEAN      NOT NULL DEFAULT FALSE,   -- 공연·예약처럼 늦으면 안 되는 일정
  move_mode   VARCHAR(10)  CHECK (move_mode IN ('walk', 'transit', 'taxi')),  -- 앞 장소에서 오는 수단, NULL이면 자동
  memo        VARCHAR(200),
  state       VARCHAR(10)  NOT NULL DEFAULT 'todo' CHECK (state IN ('todo', 'done', 'skipped')),
  arrived_at  TIMESTAMP,                             -- GPS로 150m 안에 들어온 시각
  departed_at TIMESTAMP                              -- 200m 밖으로 나간 시각
);

-- ---------------------------------------------------------------- 인덱스
CREATE INDEX IF NOT EXISTS idx_retrip_place_name   ON retrip_place (name);
CREATE INDEX IF NOT EXISTS idx_retrip_place_source ON retrip_place (source, review_count);
CREATE INDEX IF NOT EXISTS idx_place_biz           ON retrip_place (biz_type);
CREATE INDEX IF NOT EXISTS idx_retrip_review_place ON retrip_review (place_id);
CREATE INDEX IF NOT EXISTS idx_trip_member         ON trip (member_id, start_date);
CREATE INDEX IF NOT EXISTS idx_stop_trip           ON trip_stop (trip_id, day_no, start_time);

-- ================================================================ 바꾼 기록 (CREATE는 그대로 두고 여기에 추가)

-- 예전 DB(06_tripbiseo.sql 시절)에 이동 수단 칸이 없으면 추가
ALTER TABLE trip_stop ADD COLUMN IF NOT EXISTS move_mode VARCHAR(10) CHECK (move_mode IN ('walk', 'transit', 'taxi'));

-- 10/30 · 뼈대: retrip_place에 geom(자동 생성) + GIST 인덱스. 반경·근처 검색은 이 칸으로
--  geography: 거리를 m 단위로 계산해 줌. longitude·latitude가 바뀌면 자동으로 다시 계산됨
ALTER TABLE retrip_place ADD COLUMN IF NOT EXISTS geom geography(Point, 4326)
  GENERATED ALWAYS AS (ST_SetSRID(ST_MakePoint(longitude, latitude), 4326)::geography) STORED;
CREATE INDEX IF NOT EXISTS idx_retrip_place_geom ON retrip_place USING GIST (geom);
