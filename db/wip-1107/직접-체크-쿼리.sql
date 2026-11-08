-- 직접-체크-쿼리.sql — 상재가 pgAdmin에서 눈으로 보는 체크 시트 (2026-11-07, 노트북 DB)
--  쓰는 법: pgAdmin → tripbiseo → Query Tool → 이 파일 열기 → 쿼리 하나를 드래그해서 F5
--  요령: 빈도표는 맨 아래(개수 적은 것)부터 보세요. 예지가 편의시설에서 "시그니처버거"를 찾은 것처럼 이상한 값은 보통 1~2개짜리예요
--  모두 SELECT만 있어서 DB는 바뀌지 않아요

-- 1. 편의시설 값 빈도
--  보는 것: 쉼표로 나눈 편의시설 값마다 몇 곳인지 / 정상: 18가지(무선 인터넷, 주차, 예약 …)만 나옴
--  이상한 예: 시그니처버거, 리저브, 빈 글자 → 메뉴·지점 이름이 섞여 들어온 것
SELECT TRIM(item) AS 편의시설, count(*) AS 곳
FROM retrip_place, unnest(string_to_array(amenities, ',')) AS item
WHERE amenities IS NOT NULL
GROUP BY 1 ORDER BY 2 DESC;

-- 2. 업종(biz_type) 빈도
--  보는 것: 네이버 업종 글자마다 몇 곳인지 (141가지) / 정상: 음식·카페 업종 이름
--  이상한 예: 가게 이름(맘스터치였던 것), 스터디카페·고양이카페·애견카페·가공식품처럼 여행 식당이 아닌 것, 빈 칸(관광공사·샘플은 원래 빈 칸)
SELECT biz_type AS 업종, count(*) AS 곳, min(name) AS 예시
FROM retrip_place
GROUP BY 1 ORDER BY 2 DESC;

-- 3. 종류 맞는지 (content_type_id × category)
--  보는 것: 번호와 분류가 짝이 맞는지 / 정상: 39=EAT, 32=STAY, 12·14·38=PLAY만
--  이상한 예: 39인데 PLAY, 12인데 EAT 같은 줄
SELECT content_type_id AS 번호, category AS 분류, source AS 출처, count(*) AS 곳
FROM retrip_place
GROUP BY 1, 2, 3 ORDER BY 1, 2, 3;

-- 4. 이름에 이상한 단어
--  보는 것: 여행지가 아닐 것 같은 이름 / 정상: 08에서 일부러 남긴 세종대왕 동상 등 + "○○병원점·학원사거리점·페어필드"처럼 지점 이름에 걸린 것 (11/07 노트북 43곳)
--  이상한 예: ○○터, 참전기념비, 방탈출, 폰케이스, 스터디, 독서실, 학원, 병원, 폐업, 임시휴업, (구)
SELECT id, name, biz_type, content_type_id, split_part(address, ' ', 2) AS 구
FROM retrip_place
WHERE name ~ '동상|기념비|전적비|참전|공적비|추모|위령|묘역|터$|어린이공원|방송|청사|의사당|케이스|방탈출|페어|스터디|독서실|학원|병원|의원|약국|폐업|휴업|이전|\(구\)|테스트'
ORDER BY name;

-- 5. 구별 장소 수
--  보는 것: 서울 25개 구가 다 있고 고르게 들어왔는지 / 정상: 네이버는 "서울", 관광공사는 "서울특별시"로 시작해서 한 구가 두 줄로 나옴 (서울시 1곳도 있음)
--  이상한 예: "서울특별시"가 아닌 줄, 경기도 시 이름, 구가 26개 이상(주소 모양이 다른 것)
SELECT split_part(address, ' ', 1) AS 시, split_part(address, ' ', 2) AS 구, count(*) AS 곳
FROM retrip_place
GROUP BY 1, 2 ORDER BY 3 DESC;

-- 6. 영업시간 모양 빈도 (앞 40글자)
--  보는 것: 많이 나오는 영업시간 글 / 정상: "11:00~22:00 (요일별: …)", "상시 개방", "24시간 운영"
--  이상한 예: "점포 별로 상이함"(서버가 못 읽음), "월(10/12) 휴무"처럼 날짜 붙은 휴무, 시각 없이 휴무만
SELECT left(use_time, 40) AS 영업시간_앞부분, count(*) AS 곳
FROM retrip_place
GROUP BY 1 ORDER BY 2 DESC
LIMIT 60;

-- 7. 서버가 시각을 못 읽는 영업시간 전부
--  보는 것: 숫자 시각도 상시도 없는 영업시간 (지금 30곳) / 정상: "점포 별로 상이함"(쇼핑·공연장)
--  이상한 예: 음식점인데 시각이 없음, 일주일 내내 휴무(북경원), 이상한 기호만
SELECT id, name, source, content_type_id, use_time
FROM retrip_place
WHERE use_time IS NOT NULL AND use_time !~ '[0-9]{1,2}:[0-9]{2}' AND use_time !~ '상시|24시간'
ORDER BY source, name;

-- 8. 남은 날짜 붙은 휴무 (영등포 10곳 + 북경원)
--  보는 것: fix-노트북.sql에서 일부러 남긴 곳 / 정상: 영등포구 10곳(학원 PC에서 처리) + 중구 북경원 1곳
--  이상한 예: 영등포·북경원이 아닌 곳이 나옴
SELECT id, name, split_part(address, ' ', 2) AS 구, use_time
FROM retrip_place
WHERE use_time ~ '\([0-9]+/[0-9]+\) 휴무'
ORDER BY 3, 2;

-- 9. 휴무일(rest_date) 빈도
--  보는 것: 휴무일 글 모양 / 정상: "매주 일요일", "연중무휴", 빈 칸
--  이상한 예: 날짜(10/12), "확인 필요", 영업시간 글이 잘못 들어온 것
SELECT rest_date AS 휴무일, count(*) AS 곳
FROM retrip_place
GROUP BY 1 ORDER BY 2 DESC;

-- 10. 사진·지도링크·평점이 빈 곳 (출처별)
--  보는 것: 출처마다 빈 칸 수 / 정상: 네이버는 사진 거의 없음, 관광공사는 평점·지도링크 없음
--  이상한 예: 네이버인데 지도링크 없음, http로 시작하는 사진(https 배포 때 막힐 수 있음)
SELECT source AS 출처, count(*) AS 곳,
       count(*) FILTER (WHERE image_url IS NULL OR image_url = '') AS 사진없음,
       count(*) FILTER (WHERE image_url LIKE 'http://%') AS 사진_http,
       count(*) FILTER (WHERE map_url IS NULL OR map_url = '') AS 지도링크없음,
       count(*) FILTER (WHERE rating IS NULL) AS 평점없음,
       count(*) FILTER (WHERE tel IS NULL OR tel = '') AS 전화없음
FROM retrip_place
GROUP BY 1 ORDER BY 2 DESC;

-- 11. 좌표 확인: 구 경계 밖으로 튄 곳
--  보는 것: 같은 구 장소들의 가운데에서 5km 넘게 떨어진 곳 / 정상: 청계산(서초)·수서역(강남)·북한산(은평)·항동(구로)처럼 구 끝자락 (11/07 노트북 41곳, 모두 정상)
--  이상한 예: 주소는 종로구인데 좌표가 강남, 위도·경도가 뒤바뀐 곳
SELECT p.id, p.name, split_part(p.address, ' ', 2) AS 구, p.address, round(ST_Distance(p.geom, c.center)) AS 가운데에서_m
FROM retrip_place p
JOIN (SELECT split_part(address, ' ', 2) AS gu, ST_Centroid(ST_Collect(geom::geometry))::geography AS center
      FROM retrip_place GROUP BY 1) c ON c.gu = split_part(p.address, ' ', 2)
WHERE ST_Distance(p.geom, c.center) > 5000
ORDER BY 5 DESC;

-- 12. 리뷰 무작위 20개
--  보는 것: 리뷰 글이 장소와 맞는지, 깨진 글자 없는지 / 정상: 그 가게 음식·분위기 얘기
--  이상한 예: 다른 가게 얘기, 광고 글, "."·"굿"만, 깨진 글자(???), 같은 글 반복
SELECT r.id, p.name, p.biz_type, r.content
FROM retrip_review r JOIN retrip_place p ON p.id = r.place_id
ORDER BY random()
LIMIT 20;

-- 13. 장소 무작위 30개 (네이버 지도 링크로 실제와 비교)
--  보는 것: 이름·업종·주소·영업시간이 실제와 맞는지 / map_url을 눌러 네이버 지도와 비교
--  이상한 예: 폐업("영업 종료"), 이름은 카페인데 업종이 중식당, 영업시간이 실제와 다름
SELECT id, name, biz_type, content_type_id, address, rating, review_count, left(use_time, 50) AS 영업시간, rest_date, amenities, map_url
FROM retrip_place
ORDER BY random()
LIMIT 30;

-- 14. 이름이 같은 장소 (지점 vs 중복)
--  보는 것: 같은 이름이 여러 곳 / 정상: 서로 다른 지점(구가 다름, 거리 멀음)
--  이상한 예: 같은 구·50m 안에 같은 이름 두 개 → 중복으로 들어온 것
SELECT a.name, a.id, b.id, split_part(a.address, ' ', 2) AS 구, round(ST_Distance(a.geom, b.geom)) AS 거리_m
FROM retrip_place a JOIN retrip_place b ON a.name = b.name AND a.id < b.id
ORDER BY 5
LIMIT 50;

-- 15. 일정 칸이 쓰는 장소 (지우면 안 되는 곳)
--  보는 것: trip_stop·place_area가 가리키는 장소 / 정상: 샘플 경복궁·북촌한옥마을·광화문광장, 여의도한강공원
--  이상한 예: 장소 이름이 비어 있음(장소가 지워져 연결이 끊김)
SELECT 'trip_stop' AS 어디서, s.name AS 칸이름, s.place_id, p.name AS 장소이름, p.source
FROM trip_stop s LEFT JOIN retrip_place p ON p.id = s.place_id
UNION ALL
SELECT 'place_area', a.name, a.place_id, p.name, p.source
FROM place_area a LEFT JOIN retrip_place p ON p.id = a.place_id;

-- 16. 묶음(biz_group)별 곳 수와 들어간 원래 업종
--  보는 것: 묶음마다 원래 업종이 알맞게 들어갔는지 / 정상: 한식에 국밥·냉면, 일식에 초밥·라멘처럼 맞는 것끼리
--  이상한 예: 카페·디저트에 국밥, 술집에 베이커리, 묶음 없음(NULL) 줄이 생김
SELECT COALESCE(biz_group, '(묶음 없음)') AS 묶음, count(*) AS 곳,
       string_agg(DISTINCT COALESCE(biz_type, '(빈칸)'), ', ') AS 원래_업종
FROM retrip_place
GROUP BY 1 ORDER BY 2 DESC;

-- 17. 묶음이 안 붙은 음식점·카페 (새 업종이 들어오면 여기에 나옴)
--  보는 것: content_type 39인데 biz_group이 비어 있는 곳 / 정상: 0줄
--  이상한 예: 내일 영등포 크롤링으로 들어온 새 업종 → biz-group-매핑.sql 목록에 추가하고 다시 실행
SELECT id, name, biz_type, split_part(address, ' ', 2) AS 구
FROM retrip_place
WHERE content_type_id = '39' AND biz_group IS NULL
ORDER BY biz_type, name;
