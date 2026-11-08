-- 10_retrip_update_demo_places.sql — 시연 장소 정보를 네이버 지도 화면대로 고치기 (11/06 ① 이상재). 09 다음에 실행
--  이유: 시연 화면에 나오는 장소의 평점·영업시간·소개를 실제 네이버 지도와 맞추려고 (상재가 캡처해 준 화면 기준)
--  다시 실행해도 같은 결과
--  실행: "C:\Program Files\PostgreSQL\17\bin\psql.exe" -h localhost -p 5432 -U postgres -d tripbiseo -f db\10_retrip_update_demo_places.sql

-- 문래 골목집 (네이버 지도 11/06 캡처)
UPDATE retrip_place SET
  rating = 4.77,
  image_url = 'https://search.pstatic.net/common/?src=https%3A%2F%2Fldb-phinf.pstatic.net%2F20260306_79%2F17727800462594s70y_JPEG%2FKakaoTalk_Image_2026-03-06-15-51-29_029.jpeg',
  use_time = '11:00~22:30 (요일별: 월 정기휴무 (매주 월요일) / 화 11:00-22:30 / 수 11:00-22:30 / 목 11:00-22:30 / 금 11:00-23:00 / 토 11:00-23:00 / 일 11:00-22:30)',
  rest_date = '매주 월요일',
  overview = '푸짐한 세트로 누리는 풍성한 식사. 인기 메뉴: 오감탕, 2인세트, 오리야채불고기. 1회 평균 4만원대. 금요일 오후 6시가 가장 붐벼요. 방송: 식객 허영만의 백반기행 353회(오리감자탕), 생방송 오늘아침, 생방송 투데이 등',
  updated_at = '2026-11-06T00:00:00.000Z'
WHERE id = 'naver-597569164';

-- 하나마토 문래 (네이버 지도 11/06 캡처). 영업시간·전화·주소는 원래 DB와 같아서 그대로 둠
UPDATE retrip_place SET
  rating = 4.93,
  image_url = 'https://search.pstatic.net/common/?src=https%3A%2F%2Fldb-phinf.pstatic.net%2F20260616_285%2F17815436550040M6Xu_JPEG%2FIMG_4302.jpg',
  amenities = '남/녀 화장실 구분, 무선 인터넷, 단체 이용 가능, 예약',
  overview = '문래동 골목 속 일본 현지 분위기의 야키토리 이자카야. 비장탄 화로에 직접 구운 꼬치, 대표 메뉴 야키토리 5종·오뎅 5종과 생맥주·사케. 오뎅탕과 삿포로 생맥주가 잘 어울려요. 신도림역 6번 출구에서 375m. 콜키지 가능(유료), 주차 불가. 네이버 예약 가능. 인스타그램 @hanamato_mullae',
  updated_at = '2026-11-06T00:00:00.000Z'
WHERE id = 'naver-2085802165';

-- 무탄 여의도점 (네이버 지도 11/06 캡처). 평점·주소·전화·영업시간은 원래 DB와 같아서 그대로 둠
--  사진은 칸이 하나라 첫 장만 넣음. 나머지 두 장(나중에 사진 여러 장을 보여 주게 되면 쓰기):
--  https://search.pstatic.net/common/?src=https%3A%2F%2Fldb-phinf.pstatic.net%2F20240919_102%2F1726727898792bwPgn_JPEG%2F66.jpg
--  https://search.pstatic.net/common/?src=https%3A%2F%2Fldb-phinf.pstatic.net%2F20240919_132%2F1726727892763ygucV_JPEG%2F88.jpg
UPDATE retrip_place SET
  image_url = 'https://search.pstatic.net/common/?src=https%3A%2F%2Fldb-phinf.pstatic.net%2F20240919_124%2F1726727906988fH0id_JPEG%2F33.jpg',
  amenities = '단체 이용 가능, 포장, 배달, 예약, 남/녀 화장실 구분, 유아의자, 대기공간, 간편결제, 주차',
  overview = '흑백요리사2 윤진원 셰프의 차이니즈 다이닝. 트러플 풍미 가득한 스테이크 짜장면이 대표 메뉴. 런치 특선 19,000원(2026.11.04~2027.10.31). 라스트오더 20:50. 여의도역(5·9호선) 1번 출구에서 51m. 주차 유료(최초 10분 1,500원), 콜키지 가능(유료), 글루텐프리 메뉴, 생일·기념일 혜택, 반려동물 동반 불가. 네이버 예약 가능. 인스타그램 @mutandining',
  updated_at = '2026-11-06T00:00:00.000Z'
WHERE id = 'naver-1971705948';

-- 테디뵈르하우스 더현대서울점 (네이버 지도 11/06 캡처). 평점·주소·전화·영업시간은 원래 DB와 같아서 그대로 둠
--  사진은 칸이 하나라 첫 장만 넣음. 나머지 두 장:
--  https://search.pstatic.net/common/?src=https%3A%2F%2Fldb-phinf.pstatic.net%2F20260926_209%2F17904252764853W7oh_JPEG%2F10.jpg
--  https://search.pstatic.net/common/?src=https%3A%2F%2Fldb-phinf.pstatic.net%2F20260926_218%2F1790425280061BB76W_JPEG%2FDSCF1273.jpg
UPDATE retrip_place SET
  image_url = 'https://search.pstatic.net/common/?src=https%3A%2F%2Fldb-phinf.pstatic.net%2F20260926_253%2F1790425279881wqbAK_JPEG%2FKakaoTalk_Photo_2024-06-07-01-13-21_025.jpg',
  amenities = '단체 이용 가능, 포장',
  overview = '서울 3대 크로아상. 흑백요리사2 김훈 셰프, tvN 스트릿 레스토랑 파이터 우승. 프랑스 AOP 인증 레스큐어 버터 사용. 퀸아망과 마르게리타가 특별한 맛. 더현대서울 지하 1층 파이브가이즈 옆 (1층 4-5번 게이트 앞 에스컬레이터). 여의나루역 1번 출구에서 384m. 라스트오더 20:25. 1인석·바테이블·단체석(3~10명). 매장 주차는 없고 더현대서울 주차장을 이용해요. 인스타그램 @we.love_teddy',
  updated_at = '2026-11-06T00:00:00.000Z'
WHERE id = 'naver-1096877686';

-- 여의도한강공원 (네이버 지도 11/06 캡처). 사진·소개는 관광공사 공식 자료(06)를 그대로 둠
--  상재가 보낸 사진 (방문자 리뷰·블로그 사진이라 쓸 때 출처 주의):
--  https://search.pstatic.net/common/?src=https%3A%2F%2Fpup-review-phinf.pstatic.net%2FMjAyNjA4MTVfMjYw%2FMDAxNzg2NzYyMzg0MzQz.QKNienOatQ1OVR0Su4QV_J-yT7tg1qctijXs0e1Tg4Ag.1YOnXq2R0S1Ucj0fjg2411qXrHQ29bdVhS8duSfKnN0g.JPEG%2F20260812_172714.jpg.jpg
--  https://search.pstatic.net/common/?src=https%3A%2F%2Fblogfiles.pstatic.net%2FMjAyNjExMDNfMTMy%2FMDAxNzkzNzEwODkyOTQx.zUny1RBUNbX3CR7plJGJLmVQHbGxLMZBlcXf_ew1IJsg.OUeFyJTlg7eKpF64h7EdIQRCANWDiSylyVtjTvx3jNcg.JPEG%2FIMG%EF%BC%BF9714.jpg%2F900x1200
--  https://search.pstatic.net/common/?src=https%3A%2F%2Fblogfiles.pstatic.net%2FMjAyNjExMDNfMjIw%2FMDAxNzkzNzEwODkyMDYw.e_RdT3o71qzdF0DPQJcO93NyTQa_XKNLHRkNIPuUaWMg.RDHa10ofiqCeeKdbqc7sGSubaxfi-0fzVCFo_aqBwEAg.JPEG%2FIMG%EF%BC%BF9646.jpg%2F900x676
UPDATE retrip_place SET
  biz_type = '시민공원', tel = '02-3780-0561',
  use_time = '24시간 운영', rest_date = '연중무휴',
  amenities = '남/녀 화장실 구분',
  updated_at = '2026-11-06T00:00:00.000Z'
WHERE id = 'tour-1059479';


-- 여의도한강공원 리뷰 9개 (네이버 방문자 리뷰 추천순 5·최신순 5 중 겹친 1개 뺌, 11/06 상재 캡처). 원문 대신 짧게 요약해서 넣음
INSERT INTO retrip_review (id, place_id, source, content, created_at) VALUES
('demo-rv-hangang-1','tour-1059479','naver','강변이 넓어 걷기만 해도 기분이 좋아요. 자전거 타는 사람, 돗자리 피크닉 분위기가 좋고 해 질 녘 강물 풍경이 예뻐 사진 찍기 좋아요. 사람이 많아 조용한 산책은 어렵지만 활기차요. 날씨 좋은 날 커피 들고 걷기 좋아요.','2026-11-06T00:00:00.000Z'),
('demo-rv-hangang-2','tour-1059479','naver','서울세계불꽃축제를 보러 왔어요. 인파가 엄청났지만 불꽃쇼가 환상적이라 오래 걸어온 피곤함이 잊혔어요.','2026-11-06T00:00:00.000Z'),
('demo-rv-hangang-3','tour-1059479','naver','서울크루즈가 생겨 놀거리가 많아졌어요. 분수대, 피아노물길, 수영장, 잔디밭, 물빛무대, 피크닉 데크가 있어요. 데이트·산책·물놀이·야경 모두 좋고 저녁 크루즈 야경을 추천해요.','2026-11-06T00:00:00.000Z'),
('demo-rv-hangang-4','tour-1059479','naver','8월 말 주말 저녁에 갔어요. 계절이 바뀌는 걸 느끼기 좋은 곳이에요. 저녁 먹고 가족과 강변을 걸으며 야경을 봤어요.','2026-11-06T00:00:00.000Z'),
('demo-rv-hangang-5','tour-1059479','naver','평일 낮이라 수영장이 한적했어요. 물빛광장은 청소 중이라 물이 빠져 있었어요. 여의나루역 쪽 피아노물길에서는 아이들이 물놀이를 하고 있었고, 마포대교 남단 아래에서 물에 발을 담그고 더위를 식히는 사람이 많았어요.','2026-11-06T00:00:00.000Z'),
('demo-rv-hangang-6','tour-1059479','naver','한강 따라 러닝하기 좋아요. 11월 초에도 날씨는 좋지만 강바람이 세서 장갑과 바람막이가 필요해요.','2026-11-06T00:00:00.000Z'),
('demo-rv-hangang-7','tour-1059479','naver','피크닉하러 왔는데 붐비지 않아 편하게 즐겼어요.','2026-11-06T00:00:00.000Z'),
('demo-rv-hangang-8','tour-1059479','naver','가까워서 가기 쉽고 구성도 괜찮아 다음에 또 오고 싶어요.','2026-11-06T00:00:00.000Z'),
('demo-rv-hangang-9','tour-1059479','naver','카트가 있어 텐트와 돗자리를 옮기기 편했어요. 친구와 편하게 놀다 왔어요.','2026-11-06T00:00:00.000Z')
ON CONFLICT (id) DO NOTHING;

-- 영등포 19곳: 영업시간 글에 날짜 붙은 하루 휴무(예: "토(11/7) 휴무")가 있어서 서버가 매주 휴무로 읽던 곳 (11/7 크롤링 요일별 영업시간으로 다시 씀)
--  그날만 쉰 요일은 다른 요일에 가장 많이 나온 시간으로 넣음. 이 장소가 없는 DB에서는 아무 일도 안 일어나요
UPDATE retrip_place SET use_time = '07:30~19:00 (요일별: 월 07:30-19:00 / 화 07:30-19:00 / 수 07:30-19:00 / 목 07:30-19:00 / 금 07:30-19:00 / 토 07:30-19:00 / 일 11:00-19:00)', rest_date = NULL WHERE id = 'naver-1005619485'; -- 애드커피
UPDATE retrip_place SET use_time = '17:00~24:00 (요일별: 월 17:00-24:00 / 화 17:00-24:00 / 수 17:00-24:00 / 목 17:00-24:00 / 금 17:00-01:00 / 토 17:00-01:00 / 일 17:00-24:00)', rest_date = NULL WHERE id = 'naver-1087691082'; -- 양화포차 당산역점
UPDATE retrip_place SET use_time = '11:30~21:00 (요일별: 월 11:30-21:00 / 화 정기휴무 (매주 화요일) / 수 11:30-21:00 / 목 11:30-21:00 / 금 11:30-21:00 / 토 11:30-21:00 / 일 11:30-21:00)', rest_date = '매주 화요일' WHERE id = 'naver-1119757354'; -- 도림186
UPDATE retrip_place SET use_time = '10:00~22:00 (요일별: 월 10:00-22:00 / 화 10:00-22:00 / 수 10:00-22:00 / 목 10:00-22:00 / 금 10:00-22:00 / 토 10:00-22:00 / 일 10:00-22:00)', rest_date = NULL WHERE id = 'naver-1200240373'; -- 마이유
UPDATE retrip_place SET use_time = '10:30~20:00 (요일별: 월 10:30-20:00 / 화 10:30-20:00 / 수 10:30-20:00 / 목 10:30-20:00 / 금 10:30-20:30 / 토 10:30-20:30 / 일 10:30-20:30)', rest_date = NULL WHERE id = 'naver-1214308227'; -- 37.5 시그니처 타임스퀘어점
UPDATE retrip_place SET use_time = '11:30~22:00 (요일별: 월 11:30-22:00 / 화 11:30-22:00 / 수 정기휴무 (매주 수요일) / 목 11:30-22:00 / 금 11:30-22:00 / 토 11:30-22:00 / 일 11:30-22:00)', rest_date = '매주 수요일' WHERE id = 'naver-1254535431'; -- 스시서정
UPDATE retrip_place SET use_time = '16:30~00:40 (요일별: 월 16:30-00:40 / 화 16:30-00:40 / 수 16:30-00:40 / 목 16:30-00:40 / 금 16:30-01:00 / 토 16:30-00:30 / 일 16:30-00:40)', rest_date = NULL WHERE id = 'naver-1281480231'; -- 한윤주가 영등포 본점
UPDATE retrip_place SET use_time = '16:00~24:00 (요일별: 월 16:00-24:00 / 화 16:00-24:00 / 수 16:00-24:00 / 목 16:00-24:00 / 금 16:00-02:00 / 토 12:00-02:00 / 일 12:00-23:00)', rest_date = NULL WHERE id = 'naver-1303929477'; -- 상록회관연탄구이 문래점
UPDATE retrip_place SET use_time = '10:00~19:00 (요일별: 월 10:00-19:00 / 화 10:00-19:00 / 수 10:00-19:00 / 목 10:00-19:00 / 금 10:00-21:00 / 토 10:00-21:00 / 일 10:00-21:00)', rest_date = NULL WHERE id = 'naver-1551120711'; -- 슬래시위드
UPDATE retrip_place SET use_time = '12:00~21:00 (요일별: 월 12:00-21:00 / 화 12:00-21:00 / 수 12:00-21:00 / 목 12:00-21:00 / 금 12:00-21:00 / 토 정기휴무 (매주 토요일) / 일 정기휴무 (매주 일요일))', rest_date = '매주 토·일요일' WHERE id = 'naver-1742445507'; -- 라트리
UPDATE retrip_place SET use_time = '11:00~20:30 (요일별: 월 11:00-20:30 / 화 11:00-20:30 / 수 11:00-20:30 / 목 11:00-20:30 / 금 11:00-20:30 / 토 11:00-20:30 / 일 정기휴무 (매주 일요일))', rest_date = '매주 일요일' WHERE id = 'naver-1845042944'; -- 온미동
UPDATE retrip_place SET use_time = '17:00~01:00 (요일별: 월 17:00-01:00 / 화 17:00-01:00 / 수 17:00-01:00 / 목 17:00-01:00 / 금 17:00-02:00 / 토 17:00-02:00 / 일 17:00-01:00)', rest_date = NULL WHERE id = 'naver-2006246149'; -- 취중화
UPDATE retrip_place SET use_time = '19:00~00:30 (요일별: 월 정기휴무 (매주 월요일) / 화 정기휴무 (매주 화요일) / 수 정기휴무 (매주 수요일) / 목 정기휴무 (매주 목요일) / 금 정기휴무 (매주 금요일) / 토 19:00-00:30 / 일 19:00-00:30)', rest_date = '매주 월·화·수·목·금요일' WHERE id = 'naver-2024950566'; -- 김말자 혼술바 영등포구청점
UPDATE retrip_place SET use_time = '19:00~23:00 (요일별: 월 19:00-23:00 / 화 19:00-23:00 / 수 19:00-23:00 / 목 19:00-23:00 / 금 19:00-23:00 / 토 18:00-22:00 / 일 19:00-23:00)', rest_date = NULL WHERE id = 'naver-2029554364'; -- 포엠
UPDATE retrip_place SET use_time = '16:00~01:00 (요일별: 월 16:00-23:00 / 화 16:00-01:00 / 수 16:00-01:00 / 목 16:00-01:00 / 금 16:00-01:00 / 토 16:00-02:00 / 일 16:00-01:00)', rest_date = NULL WHERE id = 'naver-2057550036'; -- 이유없는한잔
UPDATE retrip_place SET use_time = '13:00~22:00 (요일별: 월 13:00-22:00 / 화 13:00-22:00 / 수 13:00-22:00 / 목 13:00-22:00 / 금 13:00-22:00 / 토 13:00-22:00 / 일 13:00-22:00)', rest_date = NULL WHERE id = 'naver-2067621894'; -- 발원지
UPDATE retrip_place SET use_time = '10:30~20:00 (요일별: 월 10:30-20:00 / 화 10:30-20:00 / 수 10:30-20:00 / 목 10:30-20:00 / 금 10:30-20:30 / 토 10:30-20:30 / 일 10:30-20:30)', rest_date = NULL WHERE id = 'naver-2071505975'; -- 토다 신세계백화점 타임스퀘어점
UPDATE retrip_place SET use_time = '17:00~01:00 (요일별: 월 17:00-01:00 / 화 17:00-01:00 / 수 17:00-01:00 / 목 17:00-01:00 / 금 17:00-01:00 / 토 17:00-01:00 / 일 17:00-01:00)', rest_date = NULL WHERE id = 'naver-2073122052'; -- 이자카야 편안할 온
UPDATE retrip_place SET use_time = '17:00~23:00 (요일별: 월 17:00-23:00 / 화 17:00-23:00 / 수 17:00-23:00 / 목 17:00-23:00 / 금 17:00-23:00 / 토 17:00-23:00 / 일 정기휴무 (매주 일요일))', rest_date = '매주 일요일' WHERE id = 'naver-2095302987'; -- 오늘통닭 당산점

-- ===== 맨 마지막에 한 번 더 (07·09가 새로 넣은 장소에도 적용, 11/07 노트북 정제) =====
-- 주소 앞부분 통일 "서울 ○○구" → "서울특별시 ○○구" (08 F-5와 같은 규칙)
UPDATE retrip_place SET address = regexp_replace(address, '^서울(시)?\s+', '서울특별시 ')
WHERE address ~ '^서울(시)?\s';
-- 사진 주소 http → https (08 F-10과 같은 규칙)
UPDATE retrip_place SET image_url = regexp_replace(image_url, '^http://', 'https://')
WHERE image_url LIKE 'http://%';

-- 격주·매달 정기휴무와 한글날 휴무 조각 지우기 (08 F-8과 같은 규칙인데 영등포도 포함, 11/08). 07 크롤링한 영등포 12곳에 "수(11/11) 정기휴무 (매달 2, 4번째 수요일)" 같은 조각이 있음
--  서버가 날짜를 지우고 매주 그 요일 휴무로 읽어서. 지운 요일은 "정보 없음"이 됨. 다시 실행해도 같은 결과
UPDATE retrip_place SET use_time =
  regexp_replace(regexp_replace(regexp_replace(use_time,
    '\(요일별: [월화수목금토일]\([0-9]+/[0-9]+\) (정기휴무 \((매달|격주)[^)]*\)|한글날 휴무)\)', '', 'g'),
    '[월화수목금토일]\([0-9]+/[0-9]+\) (정기휴무 \((매달|격주)[^)]*\)|한글날 휴무) / ', '', 'g'),
    ' / [월화수목금토일]\([0-9]+/[0-9]+\) (정기휴무 \((매달|격주)[^)]*\)|한글날 휴무)', '', 'g')
WHERE use_time ~ '[월화수목금토일]\([0-9]+/[0-9]+\) (정기휴무 \((매달|격주)|한글날 휴무)';

-- 업종 큰 묶음 biz_group (biz-group-매핑.sql, 11/07 ① 이상재). biz_type 원래 값은 그대로 둠 (코드가 biz_type 글자를 씀)
--  목록 → 글자 규칙 순서로 묶고, 안 맞으면 NULL(미분류). 코드는 아직 이 칸을 안 씀
ALTER TABLE retrip_place ADD COLUMN IF NOT EXISTS biz_group VARCHAR(20);
ALTER TABLE retrip_place ADD COLUMN IF NOT EXISTS biz_group VARCHAR(20);

UPDATE retrip_place SET biz_group = CASE
  -- 관광공사·시연 장소(음식점 아님)는 분류 번호로
  WHEN content_type_id IN ('12','15','28') THEN '관광'
  WHEN content_type_id = '14' THEN '문화'
  WHEN content_type_id = '38' THEN '쇼핑'
  WHEN content_type_id = '32' THEN '숙박'
  WHEN content_type_id <> '39' OR content_type_id IS NULL THEN NULL

  -- 애매: 여행 식당으로 둘지 상재가 정함 (묶지 않음)
  WHEN biz_type IN ('고양이카페','애견카페','테마카페','가공식품','수산물','호두과자') THEN NULL

  -- ① 정확한 이름 목록 (11/07 노트북 DB의 네이버 업종 전부)
  WHEN biz_type IN ('한식','국밥','감자탕','한정식','국수','냉면','곰탕,설렁탕','찌개,전골','백숙,삼계탕','닭볶음탕','해장국',
                    '두부요리','보리밥','막국수','칼국수,만두','순대,순댓국','백반,가정식','전,빈대떡','갈비탕','추어탕','쌈밥','죽',
                    '향토음식','찜닭','닭요리','샤브샤브','닭발','이북음식') THEN '한식'
  WHEN biz_type IN ('육류,고기요리','돼지고기구이','소고기구이','곱창,막창,양','족발,보쌈','정육식당','양갈비','닭갈비','오리요리') THEN '고기·구이'
  WHEN biz_type IN ('생선회','해물,생선요리','조개요리','장어,먹장어요리','주꾸미요리','게요리','아귀찜,해물찜','생선구이','낙지요리',
                    '복어요리','대게요리','오징어요리','킹크랩요리','바닷가재요리','매운탕,해물탕') THEN '해산물'
  WHEN biz_type IN ('일식당','초밥,롤','돈가스','일본식라면','우동,소바','일식튀김,꼬치','오니기리','카레','덮밥') THEN '일식'
  WHEN biz_type IN ('중식당','마라탕','딤섬,중식만두','양꼬치') THEN '중식'
  WHEN biz_type IN ('양식','이탈리아음식','피자','스파게티,파스타전문','스테이크,립','패밀리레스토랑','스페인음식','프랑스음식','브런치',
                    '독일음식','그리스음식','퓨전음식','오므라이스','다이어트,샐러드') THEN '양식'
  WHEN biz_type IN ('멕시코,남미음식','베트남음식','아시아음식','태국음식','인도음식','터키음식') THEN '아시아·외국'
  WHEN biz_type IN ('햄버거','핫도그','치킨,닭강정','샌드위치','토스트') THEN '패스트푸드'
  WHEN biz_type IN ('분식','종합분식','김밥','떡볶이','만두','라면','도시락,컵밥','불닭','호떡') THEN '분식'
  WHEN biz_type IN ('뷔페','일식,초밥뷔페','해산물뷔페','한식뷔페','고기뷔페') THEN '뷔페'
  WHEN biz_type IN ('카페','카페,디저트','브런치카페','차','테이크아웃커피','떡카페','홍차전문점','아이스크림','와플','크레페',
                    '초콜릿전문점','한방카페','북카페','갤러리카페') THEN '카페·디저트'
  WHEN biz_type IN ('베이커리','케이크전문','도넛','베이글') THEN '베이커리'
  WHEN biz_type IN ('이자카야','요리주점','맥주,호프','와인','포장마차','바(BAR)','전통,민속주점','술집') THEN '술집'

  -- ② 목록에 없는 새 업종: 글자 규칙 (위에서 아래 순서로 먼저 맞는 것)
  WHEN biz_type ~ '스터디|PC방|노래방|키즈카페|애견|고양이' THEN NULL
  WHEN biz_type ~ '이자카야|주점|맥주|호프|와인|포장마차|BAR|술' THEN '술집'
  WHEN biz_type ~ '뷔페' THEN '뷔페'
  WHEN biz_type ~ '베이커리|빵|케이크|도넛|베이글' THEN '베이커리'
  WHEN biz_type ~ '카페|커피|디저트|아이스크림|^차$|차전문' THEN '카페·디저트'
  WHEN biz_type ~ '일식|초밥|스시|라멘|우동|소바|돈가스|돈까스|오마카세' THEN '일식'
  WHEN biz_type ~ '중식|중국|마라|딤섬' THEN '중식'
  WHEN biz_type ~ '햄버거|버거|치킨|핫도그|샌드위치|토스트' THEN '패스트푸드'
  WHEN biz_type ~ '분식|떡볶이|김밥' THEN '분식'
  WHEN biz_type ~ '회$|생선|해물|해산물|조개|게요리|장어|낙지|주꾸미|대게|랍스터' THEN '해산물'
  WHEN biz_type ~ '고기|구이|곱창|막창|갈비|족발|보쌈|삼겹' THEN '고기·구이'
  WHEN biz_type ~ '양식|이탈리아|피자|파스타|스테이크|프랑스|스페인|브런치' THEN '양식'
  WHEN biz_type ~ '음식$' THEN '아시아·외국'      -- 베트남음식·태국음식처럼 "○○음식"
  WHEN biz_type ~ '한식|국밥|탕$|국수|냉면|찌개|백반|정식' THEN '한식'
  ELSE NULL
END;

-- 별점 없이 리뷰 수만 있는 곳은 리뷰 수를 비움 (11/08). 보정 평점 = (리뷰 수 × 평점 + 100 × 4.6) ÷ (리뷰 수 + 100)에서 평점을 0으로 봐서
--  리뷰가 많을수록 점수가 0에 가까워짐 (예: 리뷰 14,710 → 0.03점). 비우면 4.6(모르는 곳 기본값)이 됨. 07 크롤링 영등포 507곳
UPDATE retrip_place SET review_count = NULL WHERE rating IS NULL AND review_count IS NOT NULL;
