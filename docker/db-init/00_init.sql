-- 00_init.sql — 도커 DB를 처음 만들 때 한 번 실행되는 순서 (11/12 ④ 신예지)
--  docs/DB.md의 실행 순서와 같아요. 새 SQL 파일이 생기면 여기에도 한 줄 추가해요
--  91_drop_old.sql은 예전 DB 정리용이라 뺐어요
\set ON_ERROR_STOP on

\echo '== 01~02 공통 테이블'
\i /db/01_common.sql
\i /db/02_retrip_schema.sql

\echo '== 03~06 장소·리뷰 데이터 (05번이 커서 시간이 걸려요)'
\i /db/03_retrip_seed_sample.sql
\i /db/04_retrip_seed_naver_places.sql
\i /db/05_retrip_seed_naver_reviews.sql
\i /db/06_retrip_seed_tour_seoul.sql

\echo '== 07~10 영등포 크롤링·정제·시연 장소'
\i /db/07_retrip_crawl_yeongdeungpo.sql
\i /db/08_retrip_cleanup.sql
\i /db/09_retrip_seed_yeongdeungpo.sql
\i /db/10_retrip_update_demo_places.sql

\echo '== 11~18 담당별 테이블'
\i /db/11_member.sql
\i /db/12_trip.sql
\i /db/13_plan.sql
\i /db/14_live.sql
\i /db/15_after.sql
\i /db/16_trash.sql
\i /db/17_place_area.sql
\i /db/18_ask_place.sql

\echo '== 90·95·96 테스트·시연 데이터'
\i /db/90_test_data.sql
\i /db/95_demo_seed.sql
\i /db/96_demo_account.sql

\echo '== 끝: 도커 DB 준비 완료'
