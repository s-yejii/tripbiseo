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
('demo-seonyudo-park','demo','seonyudo-park','선유도공원','PLAY','12','서울 영등포구 선유로 343',NULL,126.9003166,37.5434158,'상시 개방',NULL,'2026-11-06T00:00:00.000Z','2026-11-06T00:00:00.000Z'),
('demo-yeongdeungpo-park','demo','yeongdeungpo-park','영등포공원','PLAY','12','서울 영등포구 영등포본동',NULL,126.9107969,37.5150168,'상시 개방',NULL,'2026-11-06T00:00:00.000Z','2026-11-06T00:00:00.000Z'),
('demo-mullae-art-village','demo','mullae-art-village','문래창작촌','PLAY','12','서울 영등포구 문래동1가',NULL,126.8974312,37.5141903,NULL,NULL,'2026-11-06T00:00:00.000Z','2026-11-06T00:00:00.000Z'),
-- 숙소 (32)
('demo-conrad-seoul','demo','conrad-seoul','콘래드 서울','STAY','32','서울 영등포구 국제금융로 10',NULL,126.9266335,37.5253892,NULL,NULL,'2026-11-06T00:00:00.000Z','2026-11-06T00:00:00.000Z'),
('demo-fairmont-seoul','demo','fairmont-seoul','페어몬트 앰배서더 서울','STAY','32','서울 영등포구 여의대로 108',NULL,126.9290253,37.5251614,NULL,NULL,'2026-11-06T00:00:00.000Z','2026-11-06T00:00:00.000Z'),
('demo-glad-yeouido','demo','glad-yeouido','글래드 여의도','STAY','32','서울 영등포구 의사당대로 16',NULL,126.9172850,37.5274485,NULL,NULL,'2026-11-06T00:00:00.000Z','2026-11-06T00:00:00.000Z'),
('demo-fairfield-seoul','demo','fairfield-seoul','페어필드 바이 메리어트 서울','STAY','32','서울 영등포구 경인로 870',NULL,126.9095753,37.5171665,NULL,NULL,'2026-11-06T00:00:00.000Z','2026-11-06T00:00:00.000Z'),
('demo-courtyard-times-square','demo','courtyard-times-square','코트야드 바이 메리어트 서울 타임스퀘어','STAY','32','서울 영등포구 영중로 15',NULL,126.9038110,37.5171619,NULL,NULL,'2026-11-06T00:00:00.000Z','2026-11-06T00:00:00.000Z'),
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
  amenities = '무선 인터넷, 남/녀 화장실 구분, 제로페이, 문화비 소득공제',
  image_url = 'https://search.pstatic.net/common/?src=https%3A%2F%2Fldb-phinf.pstatic.net%2F20210527_297%2F16220986701968f1N7_JPEG%2F1.jpg',
  overview = '현대백화점 더현대 서울. 여의나루역 1번 출구에서 466m. 6층 식당가는 10:30~22:00. 지하 1층에 테디뵈르하우스 등 맛집이 모여 있어요. 홈페이지 thehyundaiseoul.ehyundai.com'
WHERE id = 'demo-thehyundai-seoul';

-- 영등포 타임스퀘어 (네이버 이름: 타임스퀘어, 리뷰 3.1만). 복합쇼핑몰이라 리뷰는 안 넣음. 사진은 첫 장만, 나머지 두 장(블로그 사진이라 쓸 때 출처 주의):
--  https://search.pstatic.net/common/?src=https%3A%2F%2Fblogfiles.pstatic.net%2FMjAyNjExMDRfNTEg%2FMDAxNzkzNzc3MjgyMzk4.JoV790EGiR1O_1smO7YnBqm5Qud8Tga6ZZQLsn_3ek8g.jIw4jmb6qXRz3MI1XQugp-nPecz51oaxP14_wGuTT-0g.JPEG%2F20261104_134827.jpg%2F2992x2992
--  https://search.pstatic.net/common/?src=https%3A%2F%2Fblogfiles.pstatic.net%2FMjAyNjExMDRfMjcw%2FMDAxNzkzNzIwNzM5OTc3.gUfWY1kusTI0qcYsqXUayJo3QKcSVNdV_e-azd6ME3og.zPCUIoM3lbyyx6aCvyJwrK-0v3ilUo4jHw-S4MWZvVEg.JPEG%2FIMG%EF%BC%BF9316.jpg%2F3024x4032
UPDATE retrip_place SET
  biz_type = '쇼핑센터,할인매장', tel = '02-2638-2000', rating = 4.43, address = '서울 영등포구 영중로 15 타임스퀘어',
  longitude = 126.9040371, latitude = 37.5172094, -- 07을 먼저 실행한 DB의 근삿값 좌표도 고치려고
  use_time = '10:30~22:00 (요일별: 월 10:30-22:00 / 화 10:30-22:00 / 수 10:30-22:00 / 목 10:30-22:00 / 금 10:30-22:00 / 토 10:30-22:00 / 일 10:30-22:00)',
  amenities = '주차, 문화비 소득공제',
  image_url = 'https://search.pstatic.net/common/?src=https%3A%2F%2Fldb-phinf.pstatic.net%2F20150901_21%2F1441044211731LPnW5_JPEG%2F116260566034254_5.jpg',
  overview = '2009년 문을 연 국내 최대급 복합쇼핑몰(경방). 신세계백화점, 이마트, 코트야드 바이 메리어트 호텔, CGV, 교보문고, 자라·H&M 등 200개 넘는 매장. 1층부터 옥상까지 뚫린 유리 지붕 아트리움에서 주말마다 공연이 열려요. 1호선 영등포역 6번 출구 217m(지하 연결), 2호선 문래역·5호선 영등포시장역도 가까워요. 주차 2,000대. 매일 10:30~22:00. 홈페이지 timessquare.co.kr'
WHERE id = 'demo-times-square';

UPDATE retrip_place SET
  biz_type = '호텔', tel = '0507-1448-2014', review_count = 547,
  use_time = '체크인 16:00 · 체크아웃 12:00',
  amenities = '예약, 주차, 발렛파킹, 방문접수/출장, 무선 인터넷, 남/녀 화장실 구분, 장애인 주차구역, 프런트 24시간, 룸서비스, 세탁 시설, 보관함, 엘리베이터',
  image_url = 'https://search.pstatic.net/common/?src=https%3A%2F%2Fldb-phinf.pstatic.net%2F20190714_107%2F1563093809044UoRWN_JPEG%2F1UnCDU8eFZ_Dv7sPVtzVD6qN.jpg',
  map_url = 'https://map.naver.com/p/search/호텔GMS',
  overview = '2017년 1월 9일 문을 연 영등포 관광호텔(3급). 영등포역 5번 출구에서 260m. 체크인 월~목 15:00, 금~일 17:00 · 체크아웃 12:00. 깔끔한 환경과 깨끗한 숙소. 객실에 헤어드라이어·냉장고, 무료 와이파이. 여행사 평점: 호텔스컴바인 8.6/10, 트립어드바이저 3.6/5'
WHERE id = 'demo-hotel-gms';
