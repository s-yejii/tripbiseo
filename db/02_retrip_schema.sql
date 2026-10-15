-- 여행어때 retrip 테이블 (aidot SQLite 701~707 → PostgreSQL)
--  · 시각은 ISO 문자열(TEXT)로 둡니다. 화면·계산 엔진이 그대로 읽는 형식이라 변환 없이 씁니다.
--  · JSON 칸(*_json)은 TEXT에 JSON 문자열.
--  · owner_key = 'member:{member_id}' (로그인한 회원)
--  · 다시 실행해도 안전합니다(IF NOT EXISTS).

CREATE TABLE IF NOT EXISTS retrip_trip (
  id           TEXT PRIMARY KEY,
  owner_key    TEXT NOT NULL,
  title        TEXT NOT NULL,
  subtitle     TEXT,
  dates        TEXT,
  cover        VARCHAR(2048),
  members_json TEXT,
  timezone     TEXT NOT NULL,
  status       TEXT NOT NULL CHECK (status IN ('recording','complete')),
  started_at   TEXT NOT NULL,
  ended_at     TEXT,
  version      INTEGER NOT NULL DEFAULT 1 CHECK (version > 0),
  track_count  INTEGER NOT NULL DEFAULT 0 CHECK (track_count >= 0),
  created_at   TEXT NOT NULL,
  updated_at   TEXT NOT NULL,
  deleted_at   TEXT
);

CREATE TABLE IF NOT EXISTS retrip_stop (
  id               TEXT PRIMARY KEY,
  trip_id          TEXT NOT NULL REFERENCES retrip_trip(id) ON DELETE CASCADE,
  source_id        TEXT,
  name             TEXT NOT NULL,
  kind             TEXT NOT NULL,
  day              INTEGER NOT NULL CHECK (day BETWEEN 1 AND 30),
  clock_time       TEXT NOT NULL,
  duration_minutes DOUBLE PRECISION NOT NULL CHECK (duration_minutes >= 0),
  travel_minutes   DOUBLE PRECISION NOT NULL CHECK (travel_minutes >= 0),
  longitude        DOUBLE PRECISION NOT NULL CHECK (longitude BETWEEN -180 AND 180),
  latitude         DOUBLE PRECISION NOT NULL CHECK (latitude BETWEEN -90 AND 90),
  coord_known      BOOLEAN NOT NULL DEFAULT TRUE,
  altitude         DOUBLE PRECISION NOT NULL DEFAULT 0,
  note             TEXT,
  position         INTEGER NOT NULL,
  transport        TEXT NOT NULL,
  replay_profile   TEXT,
  route_json       TEXT,
  route_parts_json TEXT,
  arrived_at       TEXT,
  departed_at      TEXT,
  fixed_time       BOOLEAN NOT NULL DEFAULT FALSE,   -- 시간 고정 일정(예약·공연)
  created_at       TEXT NOT NULL,
  updated_at       TEXT NOT NULL,
  deleted_at       TEXT,
  UNIQUE (id, trip_id)
);

CREATE TABLE IF NOT EXISTS retrip_asset (
  id            TEXT PRIMARY KEY,
  owner_key     TEXT NOT NULL,
  storage_kind  TEXT NOT NULL CHECK (storage_kind IN ('private','sample')),
  storage_key   TEXT,
  public_path   TEXT,
  poster_path   VARCHAR(2048),
  original_name TEXT NOT NULL,
  mime          TEXT NOT NULL,
  byte_size     BIGINT NOT NULL DEFAULT 0 CHECK (byte_size >= 0),
  sha256        TEXT,
  upload_key    TEXT,
  request_hash  TEXT,
  credit        TEXT,
  source_url    TEXT,
  simulated     BOOLEAN NOT NULL DEFAULT FALSE,
  created_at    TEXT NOT NULL,
  UNIQUE (owner_key, upload_key)
);

CREATE TABLE IF NOT EXISTS retrip_stop_media (
  id          TEXT PRIMARY KEY,
  asset_id    TEXT NOT NULL REFERENCES retrip_asset(id),
  trip_id     TEXT NOT NULL,
  stop_id     TEXT NOT NULL,
  caption     TEXT,
  captured_at TEXT,
  position    INTEGER NOT NULL,
  created_at  TEXT NOT NULL,
  deleted_at  TEXT,
  FOREIGN KEY (stop_id, trip_id) REFERENCES retrip_stop(id, trip_id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS retrip_track_batch (
  id           TEXT PRIMARY KEY,
  trip_id      TEXT NOT NULL REFERENCES retrip_trip(id) ON DELETE CASCADE,
  batch_key    TEXT NOT NULL,
  payload_hash TEXT NOT NULL,
  point_count  INTEGER NOT NULL,
  created_at   TEXT NOT NULL,
  UNIQUE (trip_id, batch_key)
);

CREATE TABLE IF NOT EXISTS retrip_track_point (
  trip_id     TEXT NOT NULL,
  seq         INTEGER NOT NULL,
  batch_key   TEXT NOT NULL,
  longitude   DOUBLE PRECISION NOT NULL CHECK (longitude BETWEEN -180 AND 180),
  latitude    DOUBLE PRECISION NOT NULL CHECK (latitude BETWEEN -90 AND 90),
  accuracy    DOUBLE PRECISION NOT NULL CHECK (accuracy >= 0),
  altitude    DOUBLE PRECISION,
  recorded_at TEXT NOT NULL,
  PRIMARY KEY (trip_id, seq),
  FOREIGN KEY (trip_id, batch_key) REFERENCES retrip_track_batch(trip_id, batch_key) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS retrip_audit (
  id            TEXT PRIMARY KEY,
  owner_key     TEXT NOT NULL,
  trip_id       TEXT NOT NULL,
  action        TEXT NOT NULL,
  metadata_json TEXT NOT NULL,
  created_at    TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS retrip_job (
  id           TEXT PRIMARY KEY,
  owner_key    TEXT NOT NULL,
  trip_id      TEXT NOT NULL,
  kind         TEXT NOT NULL,
  state        TEXT NOT NULL DEFAULT 'queued',
  payload_json TEXT NOT NULL,
  due_at       TEXT NOT NULL,
  created_at   TEXT NOT NULL,
  updated_at   TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS retrip_import (
  id           TEXT PRIMARY KEY,
  owner_key    TEXT NOT NULL,
  external_id  TEXT NOT NULL,
  trip_id      TEXT NOT NULL REFERENCES retrip_trip(id) ON DELETE CASCADE,
  payload_hash TEXT NOT NULL,
  created_at   TEXT NOT NULL,
  UNIQUE (owner_key, external_id)
);

CREATE TABLE IF NOT EXISTS retrip_expense (
  id         TEXT PRIMARY KEY,
  trip_id    TEXT NOT NULL REFERENCES retrip_trip(id) ON DELETE CASCADE,
  stop_id    TEXT,
  title      TEXT NOT NULL,
  amount     INTEGER NOT NULL CHECK (amount BETWEEN 0 AND 100000000),
  category   TEXT NOT NULL,
  payer      TEXT,
  paid_at    TEXT,
  memo       TEXT,
  created_at TEXT NOT NULL,
  updated_at TEXT NOT NULL,
  deleted_at TEXT
);

-- 팀 DB 장소 사본(샘플·TourAPI·네이버). 나중에 팀 place 테이블과 합칠 때 source+source_id로 맞춥니다.
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

CREATE TABLE IF NOT EXISTS retrip_review (
  id         TEXT PRIMARY KEY,
  place_id   TEXT NOT NULL REFERENCES retrip_place(id) ON DELETE CASCADE,
  source     TEXT NOT NULL,
  content    TEXT NOT NULL,
  created_at TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS retrip_theme (
  id          TEXT PRIMARY KEY,
  slug        VARCHAR(80) NOT NULL UNIQUE,
  title       VARCHAR(160) NOT NULL,
  theme_type  VARCHAR(40) NOT NULL,
  region      VARCHAR(80),
  description TEXT,
  source_url  VARCHAR(2048),
  stops_json  TEXT NOT NULL,
  is_example  BOOLEAN NOT NULL DEFAULT FALSE,
  created_at  TEXT NOT NULL,
  updated_at  TEXT NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_retrip_trip_owner   ON retrip_trip (owner_key, deleted_at, created_at);
CREATE INDEX IF NOT EXISTS idx_retrip_stop_trip    ON retrip_stop (trip_id, deleted_at, position);
CREATE INDEX IF NOT EXISTS idx_retrip_media_stop   ON retrip_stop_media (stop_id, deleted_at, position);
CREATE INDEX IF NOT EXISTS idx_retrip_media_asset  ON retrip_stop_media (asset_id, deleted_at);
CREATE INDEX IF NOT EXISTS idx_retrip_track_time   ON retrip_track_point (trip_id, recorded_at, seq);
CREATE INDEX IF NOT EXISTS idx_retrip_audit_owner  ON retrip_audit (owner_key, created_at);
CREATE INDEX IF NOT EXISTS idx_retrip_job_due      ON retrip_job (state, due_at);
CREATE INDEX IF NOT EXISTS idx_retrip_expense_trip ON retrip_expense (trip_id, deleted_at, paid_at);
CREATE INDEX IF NOT EXISTS idx_retrip_place_lat    ON retrip_place (latitude, longitude);
CREATE INDEX IF NOT EXISTS idx_retrip_place_name   ON retrip_place (name);
CREATE INDEX IF NOT EXISTS idx_retrip_place_source ON retrip_place (source, review_count);
CREATE INDEX IF NOT EXISTS idx_retrip_review_place ON retrip_review (place_id);
