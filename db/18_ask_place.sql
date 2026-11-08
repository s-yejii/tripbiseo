-- 18_ask_place.sql — ④ 신예지 (여행 중 AI에게 묻기 → 장소 찾기, Text-to-SQL). 이상재(DB 총괄) 확인 필요. 각자 \i 'db/18_ask_place.sql' 실행
--  AI(Gemini)는 SQL을 글자로 써 주기만 하고, 서버가 그 SQL을 실행할 때만 ask_reader 계정으로 잠깐 바꿔 들어요
--  ask_reader는 여기 만든 뷰 2개(ask_place, ask_review)와 함수 detour_minutes만 읽을 수 있어요 (회원·여행 표는 안 보임)
--  다시 실행해도 안전하게 (CREATE OR REPLACE, IF NOT EXISTS)

-- ---------------------------------------------------------------- ask_place · 묻기용 장소 표 (뷰)
--  kind·rating_adj 계산은 backend PlaceMapper.xml의 kindCase·adjustedRating과 같아요. 한쪽을 고치면 같이 고쳐요
--  관광지(tour)는 평점·리뷰 수가 없어서 rating_adj가 기본값 4.6으로 나와요
CREATE OR REPLACE VIEW ask_place AS
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
       geom
FROM retrip_place;

-- ---------------------------------------------------------------- ask_review · 묻기용 리뷰 표 (뷰)
--  리뷰는 장소당 최대 10개라 "조용한"·"뷰 좋은" 같은 분위기 조건에만 보조로 써요
CREATE OR REPLACE VIEW ask_review AS
SELECT place_id, content FROM retrip_review;

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
GRANT SELECT ON ask_place, ask_review TO ask_reader;                           -- 이 2개만 읽기
GRANT EXECUTE ON FUNCTION detour_minutes(geography, geography) TO ask_reader;
GRANT ask_reader TO CURRENT_USER;  -- 앱이 postgres가 아닌 계정으로 붙는 PC에서도 바꿔 들 수 있게
