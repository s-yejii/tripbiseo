-- 07_retrip_seed_demo_places.sql — 시연 시나리오(docs/DEMO.md)에 필요한데 04·06 데이터에 없는 쇼핑 장소 (11/06 DB 총괄). 06 다음에 실행
--  좌표·영업시간은 손으로 넣은 값이라 시연 전에 카카오맵으로 한 번 더 확인해요
--  다시 실행해도 중복 없음 (id가 이미 있으면 건너뜀)

INSERT INTO retrip_place (id,source,source_id,name,category,content_type_id,address,tel,longitude,latitude,use_time,rest_date,created_at,updated_at) VALUES
('demo-thehyundai-seoul','demo','thehyundai-seoul','더현대 서울','PLAY','38','서울 영등포구 여의대로 108',NULL,126.9284,37.5259,'10:30~20:00 (금·토·일 10:30~20:30)','백화점 정기 휴무일','2026-11-06T00:00:00.000Z','2026-11-06T00:00:00.000Z'),
('demo-times-square','demo','times-square','영등포 타임스퀘어','PLAY','38','서울 영등포구 영중로 15',NULL,126.9035,37.5172,'10:30~22:00',NULL,'2026-11-06T00:00:00.000Z','2026-11-06T00:00:00.000Z')
ON CONFLICT (id) DO NOTHING;
