# docs/DB.md에 넣을 줄 초안 (retrip_place, 11/07 ① 이상재)

| 컬럼 | 타입 | 설명 |
|---|---|---|
| biz_group | VARCHAR(20) | 업종 큰 묶음. 음식점·카페는 biz_type(네이버 업종)을 13개로 묶음(한식·고기·구이·해산물·일식·중식·양식·아시아·외국·패스트푸드·분식·뷔페·카페·디저트·베이커리·술집), 그 밖은 content_type_id로 관광·문화·쇼핑·숙박. biz_type 원래 값은 그대로 둠. 넣는 SQL: biz-group-매핑.sql(다시 실행해도 같은 결과) |

- ALTER: `ALTER TABLE retrip_place ADD COLUMN IF NOT EXISTS biz_group VARCHAR(20);` (01_common.sql 끝, 날짜·담당 주석과 함께)
- 아직 이 칸을 쓰는 코드는 없음. 쓰게 되면(하루에 같은 묶음 두 번 안 넣기 등) ③ 주형·② 찬기와 맞추기
