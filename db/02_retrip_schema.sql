-- 02_retrip_schema.sql — 예전 retrip 결제·테마 테이블 (DB 총괄)
--  retrip_expense(결제), retrip_theme(테마)는 ⑤·③이 이어서 쓸지, 지우고 새로 만들지 아직 정하지 않아서 그대로 둡니다 (docs/DB.md 확인 필요).
--  retrip_trip은 retrip_expense가 참조(FK)해서 같이 남겨 둡니다. 정해지면 정리해요.
--  예전 retrip 테이블 8개(retrip_stop, retrip_asset …)는 trip·trip_stop·trip_track과 겹쳐서 뺐어요 (예전 DB는 91_drop_old.sql).
--  다시 실행해도 안전합니다(IF NOT EXISTS).

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
CREATE INDEX IF NOT EXISTS idx_retrip_expense_trip ON retrip_expense (trip_id, deleted_at, paid_at);
