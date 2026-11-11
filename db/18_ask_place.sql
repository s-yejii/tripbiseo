-- 18_ask_place.sql — ④ 신예지 (여행 중 AI에게 묻기 → 장소 찾기, Text-to-SQL). 이상재(DB 총괄) 확인 필요. 각자 \i 'db/18_ask_place.sql' 실행
--  AI(Gemini)는 SQL을 글자로 써 주기만 하고, 서버가 그 SQL을 실행할 때만 ask_reader 계정으로 잠깐 바꿔 들어요
--  ask_reader는 여기 만든 뷰 3개(ask_place, ask_review, ask_menu)와 함수 detour_minutes만 읽을 수 있어요 (회원·여행 표는 안 보임)
--  11/11 ④: ask_place에 biz_group(업종 큰 묶음)·keywords(특징 키워드) 칸, ask_menu(메뉴·가격) 뷰 추가
--   리뷰 10개 글자 검색으로는 "조용한 카페"가 0곳이라, 방문자 키워드 통계(place_keyword)로 근거를 바꿈 (docs/ASK_EVAL.md)
--  다시 실행해도 안전하게 (CREATE OR REPLACE, IF NOT EXISTS)

-- ---------------------------------------------------------------- ask_place_base · 장소 기본 칸 (내부용 뷰, ask_reader는 못 읽음)
--  kind·rating_adj 계산은 backend PlaceMapper.xml의 kindCase·adjustedRating과 같아요. 한쪽을 고치면 같이 고쳐요
--  관광지(tour)는 평점·리뷰 수가 없어서 rating_adj가 기본값 4.6으로 나와요
--  11/11: 특징 키워드 계산에도 kind가 필요해서, 원래 ask_place의 계산을 여기로 옮김 (같은 CASE를 두 번 쓰지 않으려고)
CREATE OR REPLACE VIEW ask_place_base AS
SELECT id,
       name,
       CASE WHEN content_type_id = '32' THEN 'stay'
            WHEN content_type_id IN ('12', '15', '28') THEN 'tour'
            WHEN content_type_id = '14' THEN 'culture'
            WHEN content_type_id = '38' THEN 'shop'
            WHEN COALESCE(biz_type, '') ~ '스터디카페|PC방|노래방|키즈카페' THEN 'etc'
            WHEN COALESCE(biz_type, '') ~ '카페|커피|디저트|베이커리|케이크|도넛|와플|아이스크림|베이글|크레페|^차$' THEN 'cafe'
            WHEN COALESCE(biz_type, '') ~ '이자카야|주점|맥주|호프|포장마차|바\(BAR\)|와인' THEN 'bar'
            ELSE 'food' END AS kind,
       biz_type,
       ROUND(((COALESCE(review_count, 0) * COALESCE(rating, 0) + 100 * 4.6)
              / (COALESCE(review_count, 0) + 100))::numeric, 2) AS rating_adj,   -- 보정 평점 (리뷰 적은 5.0보다 리뷰 많은 4.7을 더 믿게)
       COALESCE(review_count, 0) AS review_count,
       amenities,                         -- 가게 정보 편의시설 (무선 인터넷, 포장, 예약, 반려동물 동반 …)
       use_time, rest_date,               -- 영업시간·휴무 (영업 중인지는 서버가 봄)
       latitude AS lat, longitude AS lng,
       geom,
       biz_group                          -- 업종 큰 묶음 (한식·고기·구이·일식·중식·카페·디저트 …, db/10에서 채움)
FROM retrip_place;

-- ---------------------------------------------------------------- ask_keyword_feature · 가게별 특징 키워드 (내부용 뷰, 11/11)
--  특징 키워드 = 그 가게 안 비율이 같은 kind 가게들 평균 비율의 1.5배 이상 + 5표 이상인 방문자 키워드
--  비율 = 그 키워드 표 ÷ 그 가게 키워드 표 합계 (손님 많은 가게·적은 가게를 같은 기준으로 맞추려고)
--  1.5배·5표: 영등포 카페 191곳에 1.0·1.2·1.5·2.0배를 비교해서 고른 값 (1.0배는 거의 다 붙고, 2.0배는 진짜 특징도 빠짐)
--  키워드 통계는 영등포 크롤링 가게(db/07)만 있어서 나머지 가게는 행이 없음 → ask_place.keywords가 NULL
CREATE OR REPLACE VIEW ask_keyword_feature AS
WITH shares AS (            -- 가게마다 키워드 비율
    SELECT k.place_id, k.keyword, k.cnt, b.kind,
           -- SUM(...) OVER (PARTITION BY 가게): 가게별 합계를 각 줄 옆에 붙이는 문법(윈도우 함수)
           k.cnt::numeric / SUM(k.cnt) OVER (PARTITION BY k.place_id) AS share
    FROM place_keyword k
    JOIN ask_place_base b ON b.id = k.place_id
), averages AS (            -- 같은 kind 가게들의 키워드별 평균 비율
    SELECT kind, keyword, AVG(share) AS avg_share
    FROM shares
    GROUP BY kind, keyword
)
SELECT s.place_id,
       STRING_AGG(s.keyword, ', ' ORDER BY s.share / a.avg_share DESC) AS keywords  -- 특징이 강한 순으로 이어 붙임
FROM shares s
JOIN averages a ON a.kind = s.kind AND a.keyword = s.keyword
WHERE s.cnt >= 5                         -- 5표 이상 (한두 명이 만든 우연 빼기)
  AND s.share >= 1.5 * a.avg_share       -- 같은 종류 평균의 1.5배 이상
GROUP BY s.place_id;

-- ---------------------------------------------------------------- ask_place · 묻기용 장소 표 (뷰, AI가 읽음)
--  기존 칸은 순서를 바꾸면 안 돼요 (CREATE OR REPLACE VIEW는 새 칸을 맨 뒤에만 붙일 수 있음)
CREATE OR REPLACE VIEW ask_place AS
SELECT b.id, b.name, b.kind, b.biz_type, b.rating_adj, b.review_count, b.amenities,
       b.use_time, b.rest_date, b.lat, b.lng, b.geom,
       b.biz_group,                       -- 11/11 새 칸: 업종 큰 묶음
       f.keywords                         -- 11/11 새 칸: 특징 키워드 (", "로 이음, 없으면 NULL)
FROM ask_place_base b
LEFT JOIN ask_keyword_feature f ON f.place_id = b.id;

-- ---------------------------------------------------------------- ask_review · 묻기용 리뷰 표 (뷰)
--  리뷰는 장소당 최대 10개라 "조용한"·"뷰 좋은" 같은 분위기 조건에만 보조로 써요
CREATE OR REPLACE VIEW ask_review AS
SELECT place_id, content FROM retrip_review;

-- ---------------------------------------------------------------- ask_menu · 묻기용 메뉴 표 (뷰, 11/11)
--  "만원 이하 파스타"처럼 메뉴·가격으로 찾을 때. 영등포 크롤링 가게(db/07)만 있어요
CREATE OR REPLACE VIEW ask_menu AS
SELECT place_id, name, price, is_main FROM place_menu;

-- ---------------------------------------------------------------- detour_minutes · 가는 길에서 들르면 몇 분 더
--  경로선까지 거리 × 2(갔다가 돌아옴) ÷ 걷는 속도 75m/분, 올림 (150m → 4분, 190m → 6분)
CREATE OR REPLACE FUNCTION detour_minutes(place geography, route geography) RETURNS int
LANGUAGE sql IMMUTABLE PARALLEL SAFE AS
$$ SELECT CEIL(ST_Distance(place, route) * 2 / 75)::int $$;

-- ---------------------------------------------------------------- 리뷰 글 검색 빠르게 ("조용", "뷰" 같은 ILIKE)
--  처음 만들 때 리뷰 11만 줄이라 몇 초~수십 초 걸려요 (한 번만)
CREATE EXTENSION IF NOT EXISTS pg_trgm;
CREATE INDEX IF NOT EXISTS idx_retrip_review_content_trgm ON retrip_review USING GIN (content gin_trgm_ops);

-- ---------------------------------------------------------------- ask_reader · 묻기 전용 읽기 출입증
--  로그인은 못 하고, 서버가 AI SQL을 실행할 때만 SET LOCAL ROLE ask_reader로 잠깐 바꿔 들어요
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'ask_reader') THEN
    CREATE ROLE ask_reader NOLOGIN;
  END IF;
END $$;
GRANT SELECT ON ask_place, ask_review, ask_menu TO ask_reader;                 -- 이 3개만 읽기 (ask_place_base·ask_keyword_feature는 안 줌)
GRANT EXECUTE ON FUNCTION detour_minutes(geography, geography) TO ask_reader;
GRANT ask_reader TO CURRENT_USER;  -- 앱이 postgres가 아닌 계정으로 붙는 PC에서도 바꿔 들 수 있게
