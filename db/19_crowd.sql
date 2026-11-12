-- 11/12 ② 이찬기 — 시간대별 혼잡도 (서울 생활인구 250m). 이상재(DB 총괄) 확인 필요
-- 실행(도커): docker compose exec -T db psql -U postgres -d tripbiseo -f /db/19_crowd.sql
-- 실행(도커 없음): 저장소 맨 위 폴더에서 psql 접속 후 \i 'db/19_crowd.sql'
-- CSV 두 개(평균내기.py 결과)를 db/crowd/ 에 넣어 두세요. 다시 실행해도 됨 (지우고 다시 넣음)
-- 새 파일이라 docker/db-init/00_init.sql 에 \i /db/19_crowd.sql 한 줄을 같은 커밋에 넣어야 해요
-- 출처: 서울 열린데이터광장 [내국인]·[단기외국인]·[장기외국인] 서울 생활인구(250m) 격자 단위, 공공누리 1유형(출처 표시)
-- 생활인구 = 매시각 그 칸에 있던 사람(시각끼리 더하면 안 됨). 7~23시만.



-- ① 칸: 250m 네모 하나
CREATE TABLE IF NOT EXISTS crowd_cell (
    cell_id   VARCHAR(20) PRIMARY KEY,           -- 격자 ID (예: 다사47254650)
    dong_code VARCHAR(10),                       -- 행정동 코드 (칸이 여러 동에 걸치면 인원이 가장 많은 동)
    x5179     INTEGER NOT NULL,                  -- 칸 왼쪽 아래 X (EPSG:5179)
    y5179     INTEGER NOT NULL,                  -- 칸 왼쪽 아래 Y
    geom      geometry(Polygon, 4326)            -- 250m 네모 (경위도), 오버레이용
);
CREATE INDEX IF NOT EXISTS crowd_cell_geom_idx ON crowd_cell USING GIST (geom);

-- ② 평균: 칸 × 요일 × 시각마다 평소 몇 명 (평균 방식, 지금 쓸 것)
CREATE TABLE IF NOT EXISTS crowd_hour (
    cell_id    VARCHAR(20) NOT NULL REFERENCES crowd_cell(cell_id) ON DELETE CASCADE,
    day_kind   SMALLINT NOT NULL,                -- 1=월 … 7=일 (ISODOW), 8=공휴일
    hour       SMALLINT NOT NULL,                -- 7~23시
    people     INTEGER NOT NULL,                 -- 평균 인원 = 내국인 + 단기외국인 + 장기외국인
    foreign_short INTEGER NOT NULL DEFAULT 0,    -- 그중 단기외국인(관광객) 평균
    seoul_pct  SMALLINT NOT NULL,                -- 같은 day_kind·hour 서울 칸 중 순위 (0~100, 높을수록 붐빔) → "원래 붐비는 동네"
    self_pct   SMALLINT NOT NULL,                -- 이 칸 가장 붐빈 때 대비 % (0~100) → "지금은 덜 붐벼요 / 가장 붐빌 때"
    PRIMARY KEY (cell_id, day_kind, hour)
);  -- 지금은 중구·강남구·종로구만: 1,076칸, 146,135줄 (최근 8주 평균 2026-09-05~10-31, 공휴일만 1년치)

TRUNCATE crowd_hour, crowd_cell;
\copy crowd_cell (cell_id, dong_code, x5179, y5179) FROM 'db/crowd/crowd_cell.csv' WITH (FORMAT csv, HEADER true, ENCODING 'UTF8')
\copy crowd_hour (cell_id, day_kind, hour, people, foreign_short, seoul_pct, self_pct) FROM 'db/crowd/crowd_hour.csv' WITH (FORMAT csv, HEADER true, ENCODING 'UTF8')
UPDATE crowd_cell SET geom = ST_Transform(ST_MakeEnvelope(x5179, y5179, x5179 + 250, y5179 + 250, 5179), 4326);
ANALYZE crowd_cell; ANALYZE crowd_hour;
SELECT COUNT(*) AS cells FROM crowd_cell;
SELECT COUNT(*) AS rows  FROM crowd_hour;

-- ③ 예측 (ML로 가면 추가, 지금은 만들지 않음): 앞으로 14일만, 매일 지우고 다시 넣음
-- CREATE TABLE IF NOT EXISTS crowd_forecast (
--     cell_id   VARCHAR(20) NOT NULL REFERENCES crowd_cell(cell_id) ON DELETE CASCADE,
--     ymd       DATE NOT NULL,
--     hour      SMALLINT NOT NULL,
--     people    INTEGER NOT NULL,              -- 예측 인원 (날씨 예보 반영)
--     made_at   TIMESTAMP NOT NULL DEFAULT now(),
--     PRIMARY KEY (cell_id, ymd, hour)
-- );

-- 쓰는 법 (장소 하나, 토요일 15시):
-- SELECT h.people, h.seoul_pct, h.self_pct FROM crowd_cell c JOIN crowd_hour h USING (cell_id)
--  WHERE ST_Contains(c.geom, ST_SetSRID(ST_MakePoint(경도, 위도), 4326)) AND h.day_kind = 6 AND h.hour = 15;
-- 공휴일이면 day_kind = 8 (공휴일 판단은 서버 쪽 공휴일 목록으로)
