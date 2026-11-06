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
  amenities = '남/녀 화장실 구분, 무선 인터넷, 단체 이용 가능, 예약, 콜키지 가능(유료), 주차 불가',
  overview = '문래동 골목 속 일본 현지 분위기의 야키토리 이자카야. 비장탄 화로에 직접 구운 꼬치, 대표 메뉴 야키토리 5종·오뎅 5종과 생맥주·사케. 오뎅탕과 삿포로 생맥주가 잘 어울려요. 신도림역 6번 출구에서 375m. 네이버 예약 가능. 인스타그램 @hanamato_mullae',
  updated_at = '2026-11-06T00:00:00.000Z'
WHERE id = 'naver-2085802165';

-- 무탄 여의도점 (네이버 지도 11/06 캡처). 평점·주소·전화·영업시간은 원래 DB와 같아서 그대로 둠
--  사진은 칸이 하나라 첫 장만 넣음. 나머지 두 장(나중에 사진 여러 장을 보여 주게 되면 쓰기):
--  https://search.pstatic.net/common/?src=https%3A%2F%2Fldb-phinf.pstatic.net%2F20240919_102%2F1726727898792bwPgn_JPEG%2F66.jpg
--  https://search.pstatic.net/common/?src=https%3A%2F%2Fldb-phinf.pstatic.net%2F20240919_132%2F1726727892763ygucV_JPEG%2F88.jpg
UPDATE retrip_place SET
  image_url = 'https://search.pstatic.net/common/?src=https%3A%2F%2Fldb-phinf.pstatic.net%2F20240919_124%2F1726727906988fH0id_JPEG%2F33.jpg',
  amenities = '단체 이용 가능, 포장, 배달, 예약, 남/녀 화장실 구분, 유아의자, 대기공간, 간편결제, 생일/기념일 혜택, 글루텐프리 메뉴, 콜키지 가능(유료), 반려동물 동반 불가, 주차(유료, 최초 10분 1,500원)',
  overview = '흑백요리사2 윤진원 셰프의 차이니즈 다이닝. 트러플 풍미 가득한 스테이크 짜장면이 대표 메뉴. 런치 특선 19,000원(2026.11.04~2027.10.31). 라스트오더 20:50. 여의도역(5·9호선) 1번 출구에서 51m. 네이버 예약 가능. 인스타그램 @mutandining',
  updated_at = '2026-11-06T00:00:00.000Z'
WHERE id = 'naver-1971705948';

-- 테디뵈르하우스 더현대서울점 (네이버 지도 11/06 캡처). 평점·주소·전화·영업시간은 원래 DB와 같아서 그대로 둠
--  사진은 칸이 하나라 첫 장만 넣음. 나머지 두 장:
--  https://search.pstatic.net/common/?src=https%3A%2F%2Fldb-phinf.pstatic.net%2F20260926_209%2F17904252764853W7oh_JPEG%2F10.jpg
--  https://search.pstatic.net/common/?src=https%3A%2F%2Fldb-phinf.pstatic.net%2F20260926_218%2F1790425280061BB76W_JPEG%2FDSCF1273.jpg
UPDATE retrip_place SET
  image_url = 'https://search.pstatic.net/common/?src=https%3A%2F%2Fldb-phinf.pstatic.net%2F20260926_253%2F1790425279881wqbAK_JPEG%2FKakaoTalk_Photo_2024-06-07-01-13-21_025.jpg',
  amenities = '단체 이용 가능, 포장, 주차 불가(더현대서울 주차 안내 확인), 1인석, 바테이블, 단체석(3~10명)',
  overview = '서울 3대 크로아상. 흑백요리사2 김훈 셰프, tvN 스트릿 레스토랑 파이터 우승. 프랑스 AOP 인증 레스큐어 버터 사용. 퀸아망과 마르게리타가 특별한 맛. 더현대서울 지하 1층 파이브가이즈 옆 (1층 4-5번 게이트 앞 에스컬레이터). 여의나루역 1번 출구에서 384m. 라스트오더 20:25. 인스타그램 @we.love_teddy',
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
