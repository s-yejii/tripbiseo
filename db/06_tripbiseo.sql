-- 여행어때 앱 테이블 (01~05 다음에 실행). 다시 실행해도 안전합니다.
--  trip       : 여행 (회원별)
--  trip_stop  : 여행 일정의 장소 한 칸
--  trip_track : 여행 중 GPS 기록 (회고 지도)
--  trip_chat  : 여행별 대화 기록 (AI 계획 대화 + 여행 중 대화)
--  api_key    : 개발용 API 키 (마이 → v1.0 → API 키 관리)

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

CREATE TABLE IF NOT EXISTS trip_track (
  track_id    BIGSERIAL PRIMARY KEY,
  trip_id     BIGINT NOT NULL REFERENCES trip(trip_id) ON DELETE CASCADE,
  lat         DOUBLE PRECISION NOT NULL,
  lng         DOUBLE PRECISION NOT NULL,
  accuracy    DOUBLE PRECISION,
  recorded_at TIMESTAMP NOT NULL DEFAULT NOW()
);

-- 이미 만든 DB에 이동수단 칸 추가
ALTER TABLE trip_stop ADD COLUMN IF NOT EXISTS move_mode VARCHAR(10) CHECK (move_mode IN ('walk', 'transit', 'taxi'));

CREATE TABLE IF NOT EXISTS trip_chat (
  chat_id     BIGSERIAL PRIMARY KEY,
  trip_id     BIGINT        NOT NULL REFERENCES trip(trip_id) ON DELETE CASCADE,
  role        VARCHAR(2)    NOT NULL CHECK (role IN ('me', 'ai')),
  text        VARCHAR(2000) NOT NULL,
  created_at  TIMESTAMP     NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS api_key (
  name       VARCHAR(40)  PRIMARY KEY,
  value      VARCHAR(400) NOT NULL,
  updated_at TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_trip_member   ON trip (member_id, start_date);
CREATE INDEX IF NOT EXISTS idx_stop_trip     ON trip_stop (trip_id, day_no, start_time);
CREATE INDEX IF NOT EXISTS idx_track_trip    ON trip_track (trip_id, recorded_at);
CREATE INDEX IF NOT EXISTS idx_chat_trip     ON trip_chat (trip_id, created_at);
CREATE INDEX IF NOT EXISTS idx_place_biz     ON retrip_place (biz_type);

-- 팀이 쓰던 카카오 지도 JavaScript 키 (공개용 키, 카카오 콘솔에서 도메인으로 보호)
INSERT INTO api_key (name, value) VALUES ('KAKAO_JS_KEY', '5586371903966a8476356dc3c5812b31') ON CONFLICT (name) DO NOTHING;

-- 관리자 만들기 (회원가입 후):  UPDATE member SET role = 'ROLE_ADMIN' WHERE username = '내아이디';
