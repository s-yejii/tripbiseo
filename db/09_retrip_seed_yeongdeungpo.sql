-- 09_retrip_seed_yeongdeungpo.sql — 시연 시나리오 경로(문래·영등포·여의도) 주변 쇼핑·관광지·숙박 (11/06 ① 이상재). 08 다음에 실행
--  이유: 04(네이버)·06(관광공사)에는 이 근처 백화점·쇼핑몰·호텔이 없어서 시나리오의 더현대 서울·타임스퀘어·숙소가 장소로 안 잡힘
--  좌표: OpenStreetMap(Nominatim)에서 이름으로 찾은 값 (11/06). 코트야드·글래드는 이름으로 안 나와서 도로명 주소로 찾음
--  영업시간·사진은 몰라서 비워 둠 (공원만 상시 개방). 종류는 content_type_id로 정해짐: 38 쇼핑, 12 들를 곳, 32 숙소
--  예전 07_retrip_seed_demo_places.sql(더현대·타임스퀘어 2곳)을 이 파일로 합침. id가 같아서 07을 먼저 실행했어도 중복 없음
--  다시 실행해도 중복 없음 (id가 이미 있으면 건너뜀)
--  실행: "C:\Program Files\PostgreSQL\17\bin\psql.exe" -h localhost -p 5432 -U postgres -d tripbiseo -f db\09_retrip_seed_yeongdeungpo.sql

INSERT INTO retrip_place (id,source,source_id,name,category,content_type_id,address,tel,longitude,latitude,use_time,rest_date,created_at,updated_at) VALUES
-- 쇼핑 (38)
('demo-thehyundai-seoul','demo','thehyundai-seoul','더현대 서울','PLAY','38','서울 영등포구 여의대로 108',NULL,126.9284184,37.5259638,NULL,NULL,'2026-11-06T00:00:00.000Z','2026-11-06T00:00:00.000Z'),
('demo-times-square','demo','times-square','영등포 타임스퀘어','PLAY','38','서울 영등포구 영중로 15',NULL,126.9040371,37.5172094,NULL,NULL,'2026-11-06T00:00:00.000Z','2026-11-06T00:00:00.000Z'),
('demo-ifc-mall','demo','ifc-mall','IFC몰','PLAY','38','서울 영등포구 국제금융로 10',NULL,126.9260330,37.5252490,NULL,NULL,'2026-11-06T00:00:00.000Z','2026-11-06T00:00:00.000Z'),
('demo-lotte-yeongdeungpo','demo','lotte-yeongdeungpo','롯데백화점 영등포점','PLAY','38','서울 영등포구 경인로 846',NULL,126.9074540,37.5159583,NULL,NULL,'2026-11-06T00:00:00.000Z','2026-11-06T00:00:00.000Z'),
('demo-shinsegae-times-square','demo','shinsegae-times-square','신세계백화점 타임스퀘어점','PLAY','38','서울 영등포구 영중로 9',NULL,126.9049251,37.5177985,NULL,NULL,'2026-11-06T00:00:00.000Z','2026-11-06T00:00:00.000Z'),
-- 들를 곳 (12)
('demo-63-square','demo','63-square','63스퀘어 (63빌딩)','PLAY','12','서울 영등포구 63로 50',NULL,126.9402516,37.5198496,NULL,NULL,'2026-11-06T00:00:00.000Z','2026-11-06T00:00:00.000Z'),
('demo-seonyudo-park','demo','seonyudo-park','선유도공원','PLAY','12','서울 영등포구 선유로 343',NULL,126.9003166,37.5434158,'06:00~24:00 (요일별: 월 06:00-24:00 / 화 06:00-24:00 / 수 06:00-24:00 / 목 06:00-24:00 / 금 06:00-24:00 / 토 06:00-24:00 / 일 06:00-24:00)',NULL,'2026-11-06T00:00:00.000Z','2026-11-06T00:00:00.000Z'),
('demo-yeongdeungpo-park','demo','yeongdeungpo-park','영등포공원','PLAY','12','서울 영등포구 영등포본동',NULL,126.9107969,37.5150168,'상시 개방',NULL,'2026-11-06T00:00:00.000Z','2026-11-06T00:00:00.000Z'),
('demo-mullae-art-village','demo','mullae-art-village','문래창작촌','PLAY','12','서울 영등포구 문래동1가',NULL,126.8974312,37.5141903,'상시 개방',NULL,'2026-11-06T00:00:00.000Z','2026-11-06T00:00:00.000Z'),
-- 숙소 (32)
('demo-conrad-seoul','demo','conrad-seoul','콘래드 서울','STAY','32','서울 영등포구 국제금융로 10',NULL,126.9266335,37.5253892,NULL,NULL,'2026-11-06T00:00:00.000Z','2026-11-06T00:00:00.000Z'),
('demo-fairmont-seoul','demo','fairmont-seoul','페어몬트 앰배서더 서울','STAY','32','서울 영등포구 여의대로 108',NULL,126.9290253,37.5251614,NULL,NULL,'2026-11-06T00:00:00.000Z','2026-11-06T00:00:00.000Z'),
('demo-glad-yeouido','demo','glad-yeouido','글래드 여의도','STAY','32','서울 영등포구 의사당대로 16',NULL,126.9172850,37.5274485,NULL,NULL,'2026-11-06T00:00:00.000Z','2026-11-06T00:00:00.000Z'),
('demo-fairfield-seoul','demo','fairfield-seoul','페어필드 바이 메리어트 서울','STAY','32','서울 영등포구 경인로 870',NULL,126.9095753,37.5171665,NULL,NULL,'2026-11-06T00:00:00.000Z','2026-11-06T00:00:00.000Z'),
-- 숙소 (32): 출장용 저렴한 비즈니스호텔, 영등포역 북쪽 (11/06 지금도 있는지 확인함)
('demo-hotel-gms','demo','hotel-gms','호텔 GMS','STAY','32','서울 영등포구 경인로 867',NULL,126.9089452,37.5174808,NULL,NULL,'2026-11-06T00:00:00.000Z','2026-11-06T00:00:00.000Z')
ON CONFLICT (id) DO NOTHING;

-- 네이버 지도 화면으로 채운 정보 (11/06 상재 캡처). 07을 먼저 실행한 DB에도 들어가게 INSERT와 따로 UPDATE로 씀

-- 더현대 서울 (네이버 이름: 현대백화점 더현대 서울, 리뷰 13만). 사진은 칸이 하나라 첫 장만 넣음. 나머지 두 장:
--  https://search.pstatic.net/common/?src=https%3A%2F%2Fldb-phinf.pstatic.net%2F20210527_1%2F1622098670243mqSFW_JPEG%2F2.jpg
--  (아래는 방문자 리뷰 사진이라 쓸 때 출처 주의)
--  https://search.pstatic.net/common/?src=https%3A%2F%2Fpup-review-phinf.pstatic.net%2FMjAyNjA4MjJfMjMg%2FMDAxNzg3MzU4MTQ0NDI0.woMqjgIGs8L4akQ4WTa22Es2grV3xHMQJMVoyCX0fOMg.IGR-3nm-4KeiFhlDcMAc3dOmkfxxWQMxi8sWvzmAcDsg.JPEG%2F20260817_183417.jpg.jpg
UPDATE retrip_place SET
  biz_type = '백화점', tel = '1588-3650', address = '서울 영등포구 여의대로 108 더현대 서울',
  longitude = 126.9284184, latitude = 37.5259638, -- 07을 먼저 실행한 DB의 근삿값 좌표도 고치려고 (geom은 자동으로 따라 바뀜)
  use_time = '10:30~20:00 (요일별: 월 10:30-20:00 / 화 10:30-20:00 / 수 10:30-20:00 / 목 10:30-20:00 / 금 10:30-20:30 / 토 10:30-20:30 / 일 10:30-20:30)',
  amenities = '무선 인터넷, 남/녀 화장실 구분, 간편결제',
  image_url = 'https://search.pstatic.net/common/?src=https%3A%2F%2Fldb-phinf.pstatic.net%2F20210527_297%2F16220986701968f1N7_JPEG%2F1.jpg',
  overview = '현대백화점 더현대 서울. 여의나루역 1번 출구에서 466m. 6층 식당가는 10:30~22:00. 지하 1층에 테디뵈르하우스 등 맛집이 모여 있어요. 제로페이 가능, 문화비 소득공제 적용 매장. 홈페이지 thehyundaiseoul.ehyundai.com'
WHERE id = 'demo-thehyundai-seoul';

-- 영등포 타임스퀘어 (네이버 이름: 타임스퀘어, 리뷰 3.1만). 복합쇼핑몰이라 리뷰는 안 넣음. 사진은 첫 장만, 나머지 두 장(블로그 사진이라 쓸 때 출처 주의):
--  https://search.pstatic.net/common/?src=https%3A%2F%2Fblogfiles.pstatic.net%2FMjAyNjExMDRfNTEg%2FMDAxNzkzNzc3MjgyMzk4.JoV790EGiR1O_1smO7YnBqm5Qud8Tga6ZZQLsn_3ek8g.jIw4jmb6qXRz3MI1XQugp-nPecz51oaxP14_wGuTT-0g.JPEG%2F20261104_134827.jpg%2F2992x2992
--  https://search.pstatic.net/common/?src=https%3A%2F%2Fblogfiles.pstatic.net%2FMjAyNjExMDRfMjcw%2FMDAxNzkzNzIwNzM5OTc3.gUfWY1kusTI0qcYsqXUayJo3QKcSVNdV_e-azd6ME3og.zPCUIoM3lbyyx6aCvyJwrK-0v3ilUo4jHw-S4MWZvVEg.JPEG%2FIMG%EF%BC%BF9316.jpg%2F3024x4032
UPDATE retrip_place SET
  biz_type = '쇼핑센터,할인매장', tel = '02-2638-2000', rating = 4.43, address = '서울 영등포구 영중로 15 타임스퀘어',
  longitude = 126.9040371, latitude = 37.5172094, -- 07을 먼저 실행한 DB의 근삿값 좌표도 고치려고
  use_time = '10:30~22:00 (요일별: 월 10:30-22:00 / 화 10:30-22:00 / 수 10:30-22:00 / 목 10:30-22:00 / 금 10:30-22:00 / 토 10:30-22:00 / 일 10:30-22:00)',
  amenities = '주차',
  image_url = 'https://search.pstatic.net/common/?src=https%3A%2F%2Fldb-phinf.pstatic.net%2F20150901_21%2F1441044211731LPnW5_JPEG%2F116260566034254_5.jpg',
  overview = '2009년 문을 연 국내 최대급 복합쇼핑몰(경방). 신세계백화점, 이마트, 코트야드 바이 메리어트 호텔, CGV, 교보문고, 자라·H&M 등 200개 넘는 매장. 1층부터 옥상까지 뚫린 유리 지붕 아트리움에서 주말마다 공연이 열려요. 1호선 영등포역 6번 출구 217m(지하 연결), 2호선 문래역·5호선 영등포시장역도 가까워요. 주차 2,000대. 문화비 소득공제 적용 매장. 매일 10:30~22:00. 홈페이지 timessquare.co.kr'
WHERE id = 'demo-times-square';

UPDATE retrip_place SET
  biz_type = '호텔', tel = '0507-1448-2014', review_count = NULL, -- 네이버 리뷰 547개지만 별점이 없어서 비움 (별점 없이 리뷰 수만 있으면 보정 평점이 0.71로 낮게 나옴)
  use_time = '체크인 16:00 · 체크아웃 12:00',
  amenities = '예약, 주차, 발렛파킹, 방문접수/출장, 무선 인터넷, 남/녀 화장실 구분, 장애인 편의시설, 24시간',
  image_url = 'https://search.pstatic.net/common/?src=https%3A%2F%2Fldb-phinf.pstatic.net%2F20190714_107%2F1563093809044UoRWN_JPEG%2F1UnCDU8eFZ_Dv7sPVtzVD6qN.jpg',
  map_url = 'https://map.naver.com/p/search/호텔GMS',
  overview = '2017년 1월 9일 문을 연 영등포 관광호텔(3급). 영등포역 5번 출구에서 260m. 체크인 월~목 15:00, 금~일 17:00 · 체크아웃 12:00. 깔끔한 환경과 깨끗한 숙소. 프런트 24시간, 룸서비스, 세탁 시설, 보관함, 엘리베이터, 장애인 주차구역. 객실에 헤어드라이어·냉장고, 무료 와이파이. 여행사 평점: 호텔스컴바인 8.6/10, 트립어드바이저 3.6/5'
WHERE id = 'demo-hotel-gms';

-- 문래창작촌은 동네(거리)라 상시 개방 (영업시간 모르는 곳은 추천에서 빠지는 규칙 때문에, 11/06). 이미 09를 돌린 DB도 바뀌게 UPDATE로 한 번 더
UPDATE retrip_place SET use_time = '상시 개방' WHERE id = 'demo-mullae-art-village';

-- 63스퀘어 (네이버 이름: 63빌딩, 리뷰 3,240 · 별점 없음이라 리뷰 수는 안 넣음). 11/06 상재 캡처
--  사진: 상재가 준 주소는 구글 지도 사용자 사진이라(출처 주의, 주소가 바뀔 수 있음) 넣지 않고 네이버 사진으로 넣음. 구글 사진 주소(참고용):
--  https://lh3.googleusercontent.com/gps-cs-s/ANWiy9TWzRaJ_NlW5aQP1dEKvJQ7qK906m6SPI7z84JHBhcZoI9DQcRx13K4AvUReZsJ21UR1zQ99U5os0yRtZ-N0Xp795lBwh7VCkNsonNRgmooidNcKuSH74dEsFd_goonW7eZ8jSn=s680-w680-h510-rw
UPDATE retrip_place SET
  biz_type = '전망대·전시', tel = '02-789-6114', address = '서울 영등포구 63로 50 63빌딩',
  use_time = '10:00~22:00 (요일별: 월 10:00-22:00 / 화 10:00-22:00 / 수 10:00-22:00 / 목 10:00-22:00 / 금 10:00-22:00 / 토 10:00-22:00 / 일 10:00-22:00)',
  amenities = '주차',
  image_url = 'https://search.pstatic.net/common/?src=https%3A%2F%2Fldb-phinf.pstatic.net%2F20150831_204%2F1441014295119zNjgr_JPEG%2F12129728_0.jpg',
  map_url = 'https://map.naver.com/p/entry/place/12129728',
  overview = '여의도 한강변의 63빌딩(63스퀘어). 전시관과 전망·식당이 있어요. 매일 10:00~22:00. 홈페이지 63building.co.kr'
WHERE id = 'demo-63-square';

-- IFC몰 (네이버 지도 11/06 상재 캡처, 리뷰 1.2만은 방문자+블로그 합계라 안 넣음)
UPDATE retrip_place SET
  biz_type = '쇼핑센터,할인매장', tel = '02-6137-5000', rating = 4.38, address = '서울 영등포구 국제금융로 10 L1층',
  use_time = '10:00~22:00 (요일별: 월 10:00-22:00 / 화 10:00-22:00 / 수 10:00-22:00 / 목 10:00-22:00 / 금 10:00-22:00 / 토 10:00-22:00 / 일 10:00-22:00)',
  image_url = 'https://search.pstatic.net/common/?src=https%3A%2F%2Fldb-phinf.pstatic.net%2F20190819_219%2F1566193143811GRABu_JPEG%2F2kg_faxjKMhb87RYsH3BSUPN.jpg',
  map_url = 'https://map.naver.com/p/entry/place/21423942',
  overview = '여의도 국제금융센터(IFC) 지하의 멀티쇼핑센터. 여의도역(5·9호선) 3번 출구에서 512m. 테슬라 수퍼차저 5기 24시간 이용 가능. 매일 10:00~22:00. 홈페이지 ifcmallseoul.com'
WHERE id = 'demo-ifc-mall';

-- 롯데백화점 영등포점 (네이버 지도 11/06 상재 캡처, 리뷰 1.5만은 합계라 안 넣음)
UPDATE retrip_place SET
  biz_type = '백화점', rating = 4.38, address = '서울 영등포구 경인로 846 1층',
  use_time = '10:30~20:00 (요일별: 월 10:30-20:00 / 화 10:30-20:00 / 수 10:30-20:00 / 목 10:30-20:00 / 금 10:30-20:30 / 토 10:30-20:30 / 일 10:30-20:30)',
  amenities = '주차, 발렛파킹, 장애인 편의시설',
  image_url = 'https://search.pstatic.net/common/?src=https%3A%2F%2Fldb-phinf.pstatic.net%2F20220824_89%2F1661304795787oeKyF_JPEG%2F%25B9%25E9%25C8%25AD%25C1%25A1_%25BF%25B5%25B5%25EE%25C6%25F7%25C1%25A1_%25BA%25B8%25C1%25A4.jpg',
  overview = '영등포역과 붙어 있는 백화점. 1호선 영등포역 3번 출구에서 3m(지하로 바로 연결). 식당가·카페가 있어요. 장애인 주차구역 있음. 월~목 10:30~20:00, 금~일 10:30~20:30'
WHERE id = 'demo-lotte-yeongdeungpo';

-- 신세계백화점 타임스퀘어점 (네이버 지도 11/06 상재 캡처, 리뷰 2.3만은 합계라 안 넣음)
--  네이버에 "월(11/9) 휴무"가 있지만 그날 하루 휴무라 영업시간에는 월요일 시간을 화~목과 같다고 가정해서 넣고 소개에만 적음 (날짜 붙은 휴무를 넣으면 서버가 매주 월요일 휴무로 읽음)
UPDATE retrip_place SET
  biz_type = '백화점', tel = '1588-1234', rating = 4.43, address = '서울 영등포구 영중로 15 1층',
  use_time = '10:30~20:00 (요일별: 월 10:30-20:00 / 화 10:30-20:00 / 수 10:30-20:00 / 목 10:30-20:00 / 금 10:30-20:30 / 토 10:30-20:30 / 일 10:30-20:30)',
  amenities = '주차',
  image_url = 'https://search.pstatic.net/common/?src=https%3A%2F%2Fldb-phinf.pstatic.net%2F20260331_147%2F1774948061262huWGu_PNG%2Fnaver_20260331_00981.png',
  overview = '1984년 문을 연 신세계백화점 영등포점(타임스퀘어 안). 지하 2층에 젊은 층을 위한 영 웨이브 매장이 있어요. 영등포역 6번 출구 도보 2분(지하상가 연결). 월~목 10:30~20:00, 금~일 10:30~20:30. 11/9(월) 하루 휴무'
WHERE id = 'demo-shinsegae-times-square';

-- 선유도공원은 상시 개방이 아니라 매일 06:00~24:00 (11/06 상재 네이버 확인). 이미 09를 돌린 DB도 바뀌게 UPDATE로 한 번 더
UPDATE retrip_place SET use_time = '06:00~24:00 (요일별: 월 06:00-24:00 / 화 06:00-24:00 / 수 06:00-24:00 / 목 06:00-24:00 / 금 06:00-24:00 / 토 06:00-24:00 / 일 06:00-24:00)' WHERE id = 'demo-seonyudo-park';

-- 콘래드 서울 (네이버 지도 11/06 상재 캡처). 편의시설은 네이버 표준 이름만, 나머지 호텔 시설은 소개에
UPDATE retrip_place SET
  biz_type = '호텔', use_time = '체크인 16:00 · 체크아웃 11:00',
  amenities = '무선 인터넷, 주차, 발렛파킹, 반려동물 동반, 장애인 편의시설',
  image_url = 'https://search.pstatic.net/common/?src=https%3A%2F%2Fldb-phinf.pstatic.net%2F20151125_178%2F1448444907885zySVp_JPEG%2FConrad_Seoul_Exterior_%25284%2529.jpg',
  overview = '여의도 IFC(국제금융로 10)에 있는 호텔. 체크인 16:00 · 체크아웃 11:00. 수영장, 피트니스, 스파·웰니스 센터, 레스토랑, 바·라운지, 룸서비스, 세탁 서비스, 컨시어지, 비즈니스 센터, 공항 셔틀, 외국어 가능 직원. 반려동물 동반 가능.'
WHERE id = 'demo-conrad-seoul';

-- 페어몬트 앰배서더 서울 (네이버 지도 11/06 상재 캡처, 리뷰 5,844 · 별점 없음이라 리뷰 수는 안 넣음)
UPDATE retrip_place SET
  biz_type = '호텔', tel = '02-3395-6000', address = '서울 영등포구 여의대로 108 페어몬트 앰배서더 서울 호텔',
  use_time = '체크인 15:00 · 체크아웃 11:00',
  amenities = '단체 이용 가능, 예약, 무선 인터넷, 남/녀 화장실 구분, 장애인 편의시설, 주차, 발렛파킹',
  image_url = 'https://search.pstatic.net/common/?src=https%3A%2F%2Fldb-phinf.pstatic.net%2F20210624_177%2F1624525259555PRXgx_JPEG%2FSQte3x9DXsmmhn2twC8G2lMV.jpg',
  overview = '여의도 파크원에 있는 5성급 호텔(Travellers'' Choice 2025). 여의나루역(5호선) 1번 출구에서 367m. 체크인 15:00 · 체크아웃 11:00. 주차 유료(최초 30분 무료, 10분당 4,000원), 발렛 유료(40,000원). 장애인 주차구역 있음. 홈페이지 fairmont-seoul.com'
WHERE id = 'demo-fairmont-seoul';

-- 글래드 여의도 (네이버 지도 11/06 상재 캡처, 리뷰 4,939는 합계라 안 넣음)
UPDATE retrip_place SET
  biz_type = '호텔', tel = '02-6222-5000', rating = 4.46, address = '서울 영등포구 의사당대로 16',
  use_time = '체크인 15:00 · 체크아웃 11:00',
  amenities = '예약, 주차, 발렛파킹, 무선 인터넷, 남/녀 화장실 구분',
  image_url = 'https://search.pstatic.net/common/?src=https%3A%2F%2Fldb-phinf.pstatic.net%2F20211222_240%2F1640178271216vvPV7_JPEG%2F%25C8%25A3%25C5%25DA%25BF%25DC%25B0%25FC%2528%25BE%25DF%25B0%25A3%2529.jpg',
  overview = '여의도의 4성급 호텔(Travellers'' Choice 2026). 국회의사당역(9호선) 4번 출구에서 82m. 체크인 15:00 · 체크아웃 11:00. 레스토랑, 바·라운지, 룸서비스, 세탁 서비스, 컨시어지, 비즈니스 센터, 외국어 가능 직원, 무료 와이파이. 홈페이지 glad-hotels.com/yeouido'
WHERE id = 'demo-glad-yeouido';

-- 페어필드 바이 메리어트 서울 (네이버 지도 11/06 상재 캡처, 리뷰 2,812 · 별점 없음이라 리뷰 수는 안 넣음)
UPDATE retrip_place SET
  biz_type = '호텔', tel = '02-2108-9000', address = '서울 영등포구 경인로 870',
  use_time = '체크인 15:00 · 체크아웃 12:00',
  amenities = '예약, 단체 이용 가능, 남/녀 화장실 구분, 무선 인터넷, 장애인 편의시설, 주차',
  image_url = 'https://search.pstatic.net/common/?src=https%3A%2F%2Fldb-phinf.pstatic.net%2F20181127_122%2F1543286169567XdVJi_JPEG%2F6nvr-Jo1SE6RPWoyb13rQUCi.jpg',
  overview = '영등포역 근처 4급 호텔. 영등포역(1호선) 5번 출구에서 200m. 체크인 15:00 · 체크아웃 12:00. 주차 유료(1박 20,000원, 전기차 충전소 있음). 장애인 주차구역 있음. 접근성 좋고 합리적인 가격의 숙박. 홈페이지 litt.ly/fairfieldseoul'
WHERE id = 'demo-fairfield-seoul';

-- 코트야드 바이 메리어트 서울 타임스퀘어는 넣지 않기로 함 (11/06 상재). 예전 09로 이미 들어간 DB에서 지움
DELETE FROM retrip_place WHERE id = 'demo-courtyard-times-square';
