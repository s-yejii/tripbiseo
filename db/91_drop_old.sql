-- 91_drop_old.sql — 예전 테이블 정리 (10/30 뼈대, DB 총괄 확인 후 실행)
--  예전 DB(01_member ~ 06_tripbiseo 시절)를 쓰던 PC에서만 한 번 실행하면 돼요. 새로 만든 DB에서는 지울 게 없어서 아무 일도 안 일어나요.
--  다시 실행해도 안전합니다(IF EXISTS).

-- 예전 관리자 API 키 테이블 (키는 환경변수로, 10/17·10/29 결정)
DROP TABLE IF EXISTS api_key;

-- 예전 retrip 회고 모듈 (trip·trip_stop·trip_track과 겹침). 참조하는 순서대로 함께 지움
DROP TABLE IF EXISTS retrip_stop_media, retrip_asset, retrip_track_point, retrip_track_batch,
                     retrip_stop, retrip_audit, retrip_job, retrip_import;

-- 예전 좌표 B-tree 인덱스 → 01_common.sql의 GIST(geom) 인덱스가 대신함
DROP INDEX IF EXISTS idx_retrip_place_lat;

-- retrip_trip은 retrip_expense가 참조(FK)해서 남겨 둠 (⑤가 retrip_expense를 정하면 정리)
