-- 시연용 샘플: 서울 장소 12곳(source='sample') + 테마 코스 예시 1개. 다시 실행해도 중복 없음.

INSERT INTO retrip_place (id,source,source_id,name,category,content_type_id,address,tel,longitude,latitude,use_time,rest_date,image_url,created_at,updated_at) VALUES
('e70caff5-27cf-5c76-a1f3-201b7032d672','sample','seoul-01','경복궁','PLAY','12','서울 종로구 사직로 161',NULL,126.977,37.5796,NULL,'매주 화요일',NULL,'2026-10-11T00:00:00.000Z','2026-10-11T00:00:00.000Z'),
('9b0d5521-9ff2-52ec-a1ea-36e9114f63ad','sample','seoul-02','광화문광장','PLAY','12','서울 종로구 세종대로 172',NULL,126.9769,37.5726,NULL,NULL,NULL,'2026-10-11T00:00:00.000Z','2026-10-11T00:00:00.000Z'),
('974f8535-3e99-5c14-8b8b-c4710c258589','sample','seoul-03','국립민속박물관','PLAY','14','서울 종로구 삼청로 37',NULL,126.9796,37.5818,NULL,NULL,NULL,'2026-10-11T00:00:00.000Z','2026-10-11T00:00:00.000Z'),
('2f00f159-cd75-56a1-b93f-d788fee4d396','sample','seoul-04','정독도서관','PLAY','14','서울 종로구 북촌로5길 48',NULL,126.9832,37.5813,NULL,NULL,NULL,'2026-10-11T00:00:00.000Z','2026-10-11T00:00:00.000Z'),
('7cfdfd7b-ea87-5ef5-ae6a-aa07397d0929','sample','seoul-05','북촌한옥마을','PLAY','12','서울 종로구 계동길 37',NULL,126.985,37.5826,NULL,NULL,NULL,'2026-10-11T00:00:00.000Z','2026-10-11T00:00:00.000Z'),
('d6ac6af0-6a82-5465-8fd9-9abb9d2078ea','sample','seoul-06','쌈지길','PLAY','38','서울 종로구 인사동길 44',NULL,126.9853,37.5742,NULL,NULL,NULL,'2026-10-11T00:00:00.000Z','2026-10-11T00:00:00.000Z'),
('ce57766d-bb3b-5cc4-aa49-c8aee11034aa','sample','seoul-07','인사동 문화의거리','PLAY','12','서울 종로구 인사동길',NULL,126.9858,37.5738,NULL,NULL,NULL,'2026-10-11T00:00:00.000Z','2026-10-11T00:00:00.000Z'),
('2a55962b-01e2-50c0-993d-f916018b73b2','sample','seoul-08','익선동 한옥거리','PLAY','12','서울 종로구 익선동',NULL,126.9898,37.5743,NULL,NULL,NULL,'2026-10-11T00:00:00.000Z','2026-10-11T00:00:00.000Z'),
('cf66f230-e665-57e2-a78a-6358da042de3','sample','seoul-09','성수동 카페거리','PLAY','12','서울 성동구 연무장길',NULL,127.0562,37.5432,NULL,NULL,NULL,'2026-10-11T00:00:00.000Z','2026-10-11T00:00:00.000Z'),
('042b46f0-9fc0-539b-b963-821cfc31bd31','sample','seoul-10','대림창고','EAT','39','서울 성동구 성수이로 78',NULL,127.0573,37.5414,NULL,NULL,NULL,'2026-10-11T00:00:00.000Z','2026-10-11T00:00:00.000Z'),
('85a7e4fd-aca7-549c-96d5-13c235acb390','sample','seoul-11','서울숲','PLAY','12','서울 성동구 뚝섬로 273',NULL,127.0374,37.5444,'상시 개방',NULL,NULL,'2026-10-11T00:00:00.000Z','2026-10-11T00:00:00.000Z'),
('418ec9f5-3dbe-5c06-8ac0-a86e62997068','sample','seoul-12','뚝섬한강공원','PLAY','12','서울 광진구 강변북로 139',NULL,127.067,37.5293,'상시 개방',NULL,NULL,'2026-10-11T00:00:00.000Z','2026-10-11T00:00:00.000Z')
ON CONFLICT DO NOTHING;

INSERT INTO retrip_theme (id,slug,title,theme_type,region,description,source_url,stops_json,is_example,created_at,updated_at) VALUES
('3d9a2b6e-5c1f-4c55-9a7e-1f0b6a0c7e01','example-palace-hanok','고궁·한옥 산책 (팀 예시 코스)','history','서울 종로','팀이 만든 예시입니다. 실제 테마 코스(연예인·유튜버 방문지 등)는 출처 URL과 함께 관리자 API로 넣으세요.',NULL,'["경복궁","국립민속박물관","정독도서관","북촌한옥마을","익선동 한옥거리"]',TRUE,'2026-10-11T00:00:00.000Z','2026-10-11T00:00:00.000Z')
ON CONFLICT DO NOTHING;
