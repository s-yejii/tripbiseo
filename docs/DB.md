# DB 테이블 (DB)

테이블의 **기준** 문서예요. 테이블을 만들거나 컬럼을 바꾸기 전에 여기를 먼저 봐요. 같은 테이블을 두 번 만들지 않으려고, **테이블 목록(이름표)을 먼저 정하고 각자 자기 테이블의 컬럼을 채우는** 방식이에요.

- DB: PostgreSQL + PostGIS, 이름 `tripbiseo`
- 규칙은 `CLAUDE.md`의 "DB 규칙"을 따라요. 요약: 주인만 만들고 고친다 / 남의 테이블은 읽기만, 쓰기는 주인의 Service 메서드로 / 컬럼을 바꾸면 쓰는 번호에게 확인 / 반경·근처로 검색하는 테이블만 `lat`·`lng` + `geom` + GIST, 검색하지 않는 좌표(`trip_stop`·`trip_track`)는 `lat`·`lng`만 / 이미 만든 테이블은 파일 끝에 `ALTER … IF NOT EXISTS`로 바꿈

## SQL 파일

| 파일 | 내용 | 누가 |
|---|---|---|
| `db/01_common.sql` | PostGIS + 공통 테이블 5개 (member, trip, trip_stop, retrip_place, retrip_review) | DB 총괄 |
| `db/02_retrip_schema.sql` | 예전 결제·테마 `retrip_expense`, `retrip_theme` (+ FK 때문에 `retrip_trip`) | DB 총괄 |
| `db/03~05_…` | 장소·리뷰 데이터 넣기 (04·05는 고치지 않음) | DB 총괄 |
| `db/06_retrip_seed_tour_seoul.sql` | 서울 관광지·문화시설·쇼핑 (TourAPI, `source='tour'`). `db/tour/make_tour_seed.mjs`로 만들어요 (11/03) | DB 총괄 |
| `db/07_retrip_crawl_yeongdeungpo.sql` | 영등포구 네이버 크롤링 (11/07): 새 테이블 `place_hours`·`place_menu`·`place_keyword`·`place_image` + `retrip_place` 새 칸 5개 + 영등포 기존 가게 444곳 갱신·새 가게 917곳. 크롤러(저장소 밖 `retrip_crawler`) build 결과. 06 다음, 08 전에 실행 (08·09·10을 꼭 다시 실행) | DB 총괄 |
| `db/08_retrip_cleanup.sql` | 여행 갈 곳이 아닌 관광공사 장소 22곳 지우기 (동상·기념비·어린이공원·~터, KBS온·국회의사당, 가게·방탈출·끝난 행사) + 편의시설 값을 네이버 표준 18개만 남기기 + 폐업 16곳·크롤링 확인 3곳·중복 9곳 삭제, 좌표 3곳 + 노트북 정제(예전 관광공사 176곳·관광공사 153곳·스터디카페 34곳·애매 업종 12곳 삭제, 날짜·격주 휴무 조각, 휴무일 글 통일, 주소 "서울특별시", 사진 https). 07 다음에 실행 (11/06~07) | DB 총괄 |
| `db/09_retrip_seed_yeongdeungpo.sql` | 시연 경로(문래·영등포·여의도) 주변 쇼핑 5·들를 곳 4·숙소 5곳(호텔 4, 비즈니스호텔 1) (`source='demo'`, 좌표 OpenStreetMap). 08 다음에 실행 (11/06) | DB 총괄 |
| `db/10_retrip_update_demo_places.sql` | 시연 장소 평점·영업시간·소개를 네이버 지도 화면대로 고치기. 09 다음에 실행 (11/06) | DB 총괄 |
| `db/11_member.sql` | ① 테이블 | ① |
| `db/12_trip.sql` | ② 테이블 (`trip_chat`) | ② |
| `db/13_plan.sql` | ③ 테이블 | ③ |
| `db/16_trash.sql` | 휴지통: `trip.deleted_at` 추가 (11/04, 이상재 확인 필요) | ③ |
| `db/14_live.sql` | ④ 테이블 (`trip_track`) | ④ |
| `db/17_place_area.sql` | 넓은 장소 경계 `place_area` + 여의도한강공원 1줄 (OSM, 11/06, 이상재 확인 필요) | ④ |
| `db/18_ask_place.sql` | AI에게 묻기(Text-to-SQL)용 뷰 `ask_place`·`ask_review`, 함수 `detour_minutes`, 읽기 전용 계정 `ask_reader`, 리뷰 검색 인덱스 (11/06, 이상재 확인 필요) | ④ |
| `db/15_after.sql` | ⑤ 테이블 | ⑤ |
| `db/90_test_data.sql` | 테스트 계정(tripuser / tripuser1234)·샘플 여행 | DB 총괄 |
| `db/91_drop_old.sql` | 예전 DB에서 안 쓰는 테이블 정리 (예전 DB만 한 번) | DB 총괄 |
| `db/96_demo_account.sql` | 발표 시연 계정 `qwer1234` 한 명 (이미 있으면 아무것도 안 함, 비밀번호는 팀 카톡). 01·11 다음에 실행 (11/09) | ③ |
| `db/95_demo_seed.sql` | 시연용 가상 사용자 데이터 (회원 `demo_001`~`demo_300` · 여행 · 일정 칸, 비밀번호 demo1234!). `db/demo/make_demo_seed.py`로 만듦. 지우기: `DELETE FROM member WHERE username LIKE 'demo\_%';` | DB 총괄 |

파일 번호 순서대로(01 → 02 → 03~06 → 11~15 → 90 → 95) 실행하면 내 PC DB에 모든 테이블이 생겨요. 모든 SQL은 다시 실행해도 오류가 나지 않게 써요 (`IF NOT EXISTS`, `ON CONFLICT DO NOTHING`).

## 테이블 목록 (이름표)

| 상태 | 뜻 |
|---|---|
| 있음 | 지금 DB에 있고 그대로 씀 |
| 만들 예정 | 주인이 컬럼을 채우고 만들 테이블 |
| 정리 예정 | 지금 DB에 있지만 안 씀 |
| 정리함 | 뼈대에서 SQL에서 뺐고, 예전 DB는 `91_drop_old.sql`로 지움 |

| 담당 | 테이블 | 용도 | 같이 쓰는 번호 | 상태 |
|---|---|---|---|---|
| 공통 | `member` | 회원 | 전원 | 있음 |
| 공통 | `trip` | 여행 | 전원 | 있음 |
| 공통 | `trip_stop` | 일정 한 칸 (도착·출발 시각 포함) | ②③④⑤ | 있음 |
| 공통 | `retrip_place` | 장소 (네이버 카페·음식점 11,496곳 + 종로 TourAPI 225곳) | ②③④⑤ | 있음 |
| 공통 | `retrip_review` | 장소 리뷰 | ② | 있음 |
| 공통 | `place_hours` | 요일별 영업시간·브레이크·라스트오더 (네이버 크롤링) | ②③④ 읽기 | 있음 (11/07, 영등포만) |
| 공통 | `place_menu` | 메뉴·가격 (네이버 크롤링) | ② 읽기 | 있음 (11/07, 영등포만) |
| 공통 | `place_keyword` | 네이버 방문자 키워드 통계 ("가성비가 좋아요" 표 수) | ③④ 읽기 (태그·조건 점수) | 있음 (11/07, 영등포만) |
| 공통 | `place_image` | 사진 여러 장 주소 (대표 사진은 `retrip_place.image_url`) | ② 읽기 | 있음 (11/07, 영등포만) |
| ① | `member_style` | 여행 스타일 | ③ 읽기 | 만들 예정 |
| ① | `notification` | 알림함 | ④⑤는 `NotificationService.send`로 | 있음 (11/03) |
| ① | `friend` | 친구 | | 만들 예정 (여유) |
| ② | `trip_chat` | 대화 기록 | ③④는 ② 메서드로 저장 | 있음 |
| ② | `pack_item` | 챙길 것 | | 만들 예정 (여유) |
| ③ | `theme`, `theme_stop` | 테마 코스 | ② 읽기 | 만들 예정 |
| ③ | `vector_store` | RAG 임베딩 (768차원) | | 만들 예정 (여유) |
| ③ | `my_place` | 내 장소 (친구 소식·장소 상세에서 저장한 곳) | ① 마이 화면에서 읽기 | 만들 예정 (11/06, 확인 필요) |
| ④ | `trip_track` | GPS 기록 | ⑤① 읽기 | 있음 |
| ⑤ | `payment` | 결제 내역 (직접 입력·카드 내역) | | 있음 (11/07) |
| ⑤ | `card` | 카드사 연결 (CODEF connected_id) | | 있음 (11/07, 쓰는 건 다음) |
| ⑤ | `trip_companion` | 같이 간 사람 (정산) | | 있음 (11/07) |
| ⑤ | `photo` | 사진·영상 (앨범·회고) | | 있음 (11/06) |
| ⑤ | `extra_visit` | 일정에 없던 곳 "들렀어요" | | 만들 예정 (여유) |
| ⑤ | `amenity` | 화장실·쓰레기통 (공공데이터) | | 만들 예정 (여유) |
| - | `api_key` | 예전 관리자 API 키 | | 정리함 (키는 환경변수, 10/29 결정) |
| - | `retrip_stop`, `retrip_asset`, `retrip_stop_media`, `retrip_track_batch`, `retrip_track_point`, `retrip_audit`, `retrip_job`, `retrip_import` | 예전 retrip 회고 모듈 (trip·trip_stop·trip_track과 겹침) | | 정리함 |
| - | `retrip_trip` | 예전 retrip 여행 | | 정리 예정: `retrip_expense`가 참조(FK)해서 남김. ⑤가 결제 테이블을 정하면 정리 |
| - | `retrip_expense`, `retrip_theme` | 예전 결제·테마 | | **확인 필요**: ⑤·③이 이어서 쓸지, 지우고 새로 만들지 |

새 테이블이 필요하면 **이 표에 먼저 한 줄 추가**하고(PR), 비슷한 테이블이 이미 있는지 확인해요.

---

## 공통 테이블 (컬럼)

`db/01_common.sql` 기준이에요. 공통 테이블은 DB 총괄(**① 이상재**)만 고쳐요. 바꿔야 하면 이상재에게 요청해요.

### member · 회원

| 컬럼 | 타입 | 설명 |
|---|---|---|
| member_id | BIGSERIAL PK | 회원 번호 |
| username | VARCHAR(50) NOT NULL UNIQUE | 아이디 |
| password | VARCHAR(100) NOT NULL | BCrypt 암호문 |
| nickname | VARCHAR(50) | 닉네임 |
| email | VARCHAR(100) UNIQUE | 가입할 때 인증한 이메일 (11/03 추가, 예전 회원은 NULL) |
| role | VARCHAR(20) NOT NULL DEFAULT 'ROLE_USER' | 역할 |
| created_at | TIMESTAMP NOT NULL DEFAULT NOW() | 가입 시각 |

### trip · 여행

| 컬럼 | 타입 | 설명 |
|---|---|---|
| trip_id | BIGSERIAL PK | 여행 번호 |
| member_id | BIGINT NOT NULL → member | 주인 회원 (회원 삭제 시 같이 삭제) |
| title | VARCHAR(60) NOT NULL | 여행 이름 |
| start_date, end_date | DATE NOT NULL | 기간 (끝 ≥ 시작, 30일 미만) |
| people | INT NOT NULL DEFAULT 1 | 인원 1~20 |
| transports | VARCHAR(60) NOT NULL DEFAULT 'subway,bus,walk' | 이동 수단, 쉼표로 (bus, subway, walk, taxi, car) |
| status | VARCHAR(10) NOT NULL DEFAULT 'planned' | planned / ongoing / done |
| started_at, finished_at | TIMESTAMP | 시작·마친 시각 |
| created_at, updated_at | TIMESTAMP NOT NULL DEFAULT NOW() | |

### trip_stop · 일정 한 칸

| 컬럼 | 타입 | 설명 |
|---|---|---|
| stop_id | BIGSERIAL PK | 일정 칸 번호 |
| trip_id | BIGINT NOT NULL → trip | 여행 (여행 삭제 시 같이 삭제) |
| day_no | INT NOT NULL | 몇째 날 1~30 |
| place_id | TEXT → retrip_place | 우리 DB 장소. DB에 없는 장소면 NULL |
| name | VARCHAR(120) NOT NULL | 장소 이름 |
| category | VARCHAR(60) | 분류 |
| address | VARCHAR(200) | 주소 |
| lat, lng | DOUBLE PRECISION NOT NULL | 좌표 |
| start_time | VARCHAR(5) NOT NULL | 시작 시각 `HH:mm` |
| stay_min | INT NOT NULL DEFAULT 60 | 머무는 시간(분) 0~720 |
| fixed | BOOLEAN NOT NULL DEFAULT FALSE | 늦으면 안 되는 일정(공연·예약) |
| move_mode | VARCHAR(10) | 앞 장소에서 오는 수단 walk / transit / taxi, NULL이면 자동 |
| memo | VARCHAR(200) | 메모 |
| state | VARCHAR(10) NOT NULL DEFAULT 'todo' | todo / done / skipped |
| importance | VARCHAR(10) NOT NULL DEFAULT 'maybe' | 중요도 must(꼭) / maybe(가능하면) / drop(빼도 됨). 11/03 추가 |
| arrived_at, departed_at | TIMESTAMP | GPS 도착(150m 안)·출발(200m 밖) 시각 |

> API의 Stop 필드 이름과 컬럼 이름이 조금 달라요: `day` ↔ `day_no`, `time` ↔ `start_time`, `stay` ↔ `stay_min`, `mode` ↔ `move_mode`. Mapper XML에서 맞춰요.

### retrip_place · 장소

| 컬럼 | 타입 | 설명 |
|---|---|---|
| id | TEXT PK | 장소 id (예: `naver-2093701183`) |
| source, source_id | TEXT NOT NULL | 출처(예: naver)와 원래 id (둘을 합쳐 UNIQUE) |
| name | TEXT NOT NULL | 이름 |
| category, content_type_id, biz_type | TEXT | 분류 (biz_type 예: "카페,디저트") |
| address, tel | TEXT | 주소, 전화 |
| longitude, latitude | DOUBLE PRECISION NOT NULL | 좌표 (**이 테이블만 이름이 latitude·longitude**) |
| use_time, rest_date | TEXT | 영업시간·휴무 원문 |
| image_url, map_url, overview | TEXT | 사진, 네이버 지도 주소, 소개 |
| rating | DOUBLE PRECISION | 평점 |
| review_count, blog_review_count | INTEGER | 리뷰 수 |
| amenities | TEXT | 편의시설 |
| created_at, updated_at | TEXT NOT NULL | 등록·수정 시각 (문자열) |
| geom | geography(Point, 4326), 자동 생성 | `longitude`·`latitude`로 자동 계산 (10/30 뼈대에서 추가). GIST 인덱스 `idx_retrip_place_geom` |
| way_text | TEXT | 찾아가는 길 (예: "문래역 7번 출구에서 484m"). 11/07 크롤링(07)으로 추가 |
| place_group | TEXT | 분류: 음식점·카페·술집·편의점·쇼핑·체험·놀거리·생활편의·숙박·관광·문화·기타 (네이버 업종 기준). 인덱스 `idx_retrip_place_group`. 11/07 추가 |
| biz_status | TEXT | 영업 / 폐업 / 휴업 / 이전 / 확인 필요(네이버에서 사라짐). 빼려면 `COALESCE(biz_status, '영업') NOT IN ('폐업', '확인 필요')`. 11/07 추가 |
| status_checked_at, last_review_date | TEXT | 영업 상태 확인 시각, 가장 최근 방문자 리뷰 날짜 (오래 끊기면 폐업 의심). 11/07 추가 |
| biz_group | VARCHAR(20) | 업종 큰 묶음. 음식점·카페는 biz_type(네이버 업종)을 13개로 묶음(한식·고기·구이·해산물·일식·중식·양식·아시아·외국·패스트푸드·분식·뷔페·카페·디저트·베이커리·술집), 그 밖은 content_type_id로 관광·문화·쇼핑·숙박. 안 맞으면 NULL. biz_type 원래 값은 그대로. 칸은 `01_common.sql` 끝, 채우기는 `db/10` 맨 끝 (11/07). 아직 쓰는 코드 없음 → 쓰게 되면 ③·②와 맞추기 |

> 반경·근처 검색은 `geom`으로 해요: `WHERE ST_DWithin(geom, ST_MakePoint(lng, lat)::geography, 반경m) ORDER BY geom <-> ST_MakePoint(lng, lat)::geography`

### retrip_review · 장소 리뷰

| 컬럼 | 타입 | 설명 |
|---|---|---|
| id | TEXT PK | 리뷰 id |
| place_id | TEXT NOT NULL → retrip_place | 장소 |
| source | TEXT NOT NULL | 출처 |
| content | TEXT NOT NULL | 리뷰 본문 |
| created_at | TEXT NOT NULL | |

### place_hours · 요일별 영업시간 (07, 11/07 크롤링)

| 컬럼 | 타입 | 설명 |
|---|---|---|
| place_id | TEXT → retrip_place (ON DELETE CASCADE) | 장소 |
| weekday | SMALLINT NOT NULL | 0=월 … 6=일 |
| open_min, close_min | INT | 자정부터 분. 새벽 2시 마감이면 close_min = 1560 |
| break_start, break_end, last_order | INT | 브레이크 타임·라스트오더 (분) |
| closed | BOOLEAN NOT NULL DEFAULT FALSE | 그 요일 정기휴무 |
| PK | (place_id, weekday) | |

> 날짜 붙은 하루 휴무("금(10/9) 휴무")는 임시 휴무로 보고 시각을 NULL로 둬요. `use_time` 원문은 retrip_place에 그대로 있어요.

### place_menu · 메뉴 (07)

| 컬럼 | 타입 | 설명 |
|---|---|---|
| place_id | TEXT → retrip_place (ON DELETE CASCADE) | 장소 |
| seq | INT | 순서 |
| name | TEXT NOT NULL | 메뉴 이름 |
| price | INT | 가격(원) |
| is_main | BOOLEAN DEFAULT FALSE | 대표 메뉴 |
| PK | (place_id, seq) | |

### place_keyword · 방문자 키워드 통계 (07)

| 컬럼 | 타입 | 설명 |
|---|---|---|
| place_id | TEXT → retrip_place (ON DELETE CASCADE) | 장소 |
| keyword | TEXT NOT NULL | 네이버 키워드 (예: "가성비가 좋아요", "차분한 분위기예요". 56가지) |
| cnt | INT NOT NULL | 그 키워드를 고른 표 수 |
| PK | (place_id, keyword) | |

> 인기 가게일수록 모든 키워드 표가 많아서, 비교할 때는 "그 가게 전체 표 중 비율"이나 "같은 종류 중 순위"로 써요. 단점(좁음·웨이팅) 키워드는 없어서 리뷰 본문으로 봐요.

### place_image · 사진 여러 장 (07)

| 컬럼 | 타입 | 설명 |
|---|---|---|
| place_id | TEXT → retrip_place (ON DELETE CASCADE) | 장소 |
| seq | INT | 순서 (1이 대표) |
| url | TEXT NOT NULL | 사진 주소 (파일은 저장 안 함, 화면에 출처 네이버 표시) |
| PK | (place_id, seq) | |

---

## 이미 있는 담당 테이블

### trip_chat · 대화 기록 (②)

| 컬럼 | 타입 | 설명 |
|---|---|---|
| chat_id | BIGSERIAL PK | |
| trip_id | BIGINT NOT NULL → trip | |
| role | VARCHAR(2) NOT NULL | me / ai |
| text | VARCHAR(2000) NOT NULL | 내용 |
| created_at | TIMESTAMP NOT NULL DEFAULT NOW() | |

### trip_track · GPS 기록 (④)

| 컬럼 | 타입 | 설명 |
|---|---|---|
| track_id | BIGSERIAL PK | |
| trip_id | BIGINT NOT NULL → trip | |
| lat, lng | DOUBLE PRECISION NOT NULL | 좌표 |
| accuracy | DOUBLE PRECISION | GPS 정확도(m) |
| recorded_at | TIMESTAMP NOT NULL DEFAULT NOW() | 기록 시각 |

### notification · 알림함 (①, `11_member.sql`, 11/03)

| 컬럼 | 타입 | 설명 |
|---|---|---|
| notification_id | BIGSERIAL PK | |
| member_id | BIGINT NOT NULL → member (ON DELETE CASCADE) | 받는 사람 |
| kind | VARCHAR(10) NOT NULL | trip(여행 중) / plan(일정) / info(날씨·안내) |
| level | VARCHAR(10) NOT NULL DEFAULT 'info' | ok / info / warn / danger (아이콘 색) |
| title | VARCHAR(100) NOT NULL | 제목 |
| text | VARCHAR(300) | 설명 |
| link | VARCHAR(200) | 누르면 갈 화면 주소 |
| notice_key | VARCHAR(100) | 같은 알림 한 번만 쌓기. `(member_id, notice_key)` UNIQUE, NULL이면 매번 쌓임 |
| read_at | TIMESTAMP | 읽은 시각 (NULL이면 안 읽음) |
| created_at | TIMESTAMP NOT NULL DEFAULT NOW() | |

인덱스 `idx_notification_member (member_id, created_at DESC)`

---

## 각자 채울 칸

자기 테이블의 컬럼을 아래 형식으로 적어요. **SQL을 쓰기 전에 먼저 여기에 적고 PR로 올려서 확인**을 받아요. 리뷰에서 다른 테이블과 겹치지 않는지, 이름 규칙이 맞는지 봐요 (DB 총괄 이상재가 확인).

### 작성 예시

```
### notification · 알림함 (①)
| 컬럼 | 타입 | 설명 |
|---|---|---|
| notification_id | BIGSERIAL PK | |
| member_id | BIGINT NOT NULL → member | 받는 사람 |
| kind | VARCHAR(10) NOT NULL | trip / pay / info |
| title | VARCHAR(100) NOT NULL | 제목 |
| text | VARCHAR(300) | 설명 |
| link | VARCHAR(200) | 누르면 갈 화면 주소 |
| read_at | TIMESTAMP | 읽은 시각 (NULL이면 안 읽음) |
| created_at | TIMESTAMP NOT NULL DEFAULT NOW() | |
```

### ① 회원·알림 (이상재)
(member_style, notification, friend)

### ② 여행·일정 (이찬기)
(pack_item — 여유)

### ③ AI 일정 (김주형)
(theme, theme_stop, vector_store)

### my_place · 내 장소 (③, `13_plan.sql`, 11/06)
친구 소식에서 "저장"을 누른 곳. 마이 > 내 장소에서 식당 · 카페 · 놀거리로 나눠 보여요. 나누기는 **저장하지 않고** 읽을 때 `retrip_place` 분류(② `PlaceMapper`의 종류 코드)로 정해요.

| 컬럼 | 타입 | 설명 |
|---|---|---|
| my_place_id | BIGSERIAL PK | |
| member_id | BIGINT NOT NULL → member (ON DELETE CASCADE) | 저장한 사람 |
| place_id | TEXT NOT NULL → retrip_place (ON DELETE CASCADE) | 장소 |
| from_name | VARCHAR(50) | 누구 소식에서 저장했는지 (예: 민지). 직접 저장이면 NULL |
| created_at | TIMESTAMP NOT NULL DEFAULT NOW() | |

- UNIQUE (member_id, place_id): 같은 곳은 한 번만
- 인덱스: (member_id, created_at DESC)
- 비슷한 테이블 확인: `friend`(① 여유)와 겹치지 않아요. 친구가 생기면 `from_name` 대신 친구 번호를 넣을지 정해요

### ④ 여행 중 (신예지)
(trip_track은 위에 있음. 더 필요하면 여기에)

### place_area · 넓은 장소 경계 (④, `17_place_area.sql`, 11/06)

공원처럼 넓은 곳은 대표 점 하나 + 150m로는 도착이 안 잡혀서(여의도한강공원은 공원 면적의 약 3%만 150m 안), 경계가 있는 장소는 경계로 도착을 판정해요.

| 컬럼 | 타입 | 설명 |
|---|---|---|
| area_id | BIGSERIAL PK | |
| place_id | TEXT → retrip_place (ON DELETE SET NULL) | DB에 없는 장소면 NULL |
| name | VARCHAR(60) NOT NULL UNIQUE | 칸 이름과 맞춰볼 이름 |
| source | VARCHAR(40) NOT NULL | 출처 (예: `osm:relation/7550666`) |
| boundary | geography(Geometry, 4326) NOT NULL, GIST 인덱스, CHECK `ST_IsValid(boundary::geometry)` | 경계. 공원 같은 면, 둘레길 같은 선 둘 다 (11/12: MultiPolygon → Geometry). 모양이 깨진 경계는 못 들어와요 (제약 `place_area_boundary_valid`, 11/12) |
| created_at | TIMESTAMP NOT NULL DEFAULT NOW() | |

- 칸과 잇는 법: `place_id`가 같거나 이름이 같을 때만(띄어쓰기 무시). 좌표로만 이으면 안 돼요 (여의도샛강생태공원도 여의도한강공원 경계 안에 있음)
- 도착: 지금 점·앞 점 둘 다 경계 30m 안. 출발: 둘 다 경계 80m 밖. 경계 없는 칸은 예전처럼 150m/200m
- 선(길)이면 길에서 30m 안 = 도착, 80m 밖 = 출발 (판정 쿼리는 같은 `ST_Distance`)
- 지금 들어 있는 곳: 여의도한강공원(면), 남산순환나들길(선, 11/12: OSM 남산북측·남측순환로·남산공원길 8조각을 합침). 출처 © OpenStreetMap contributors (ODbL), 앱에 경계를 보여주면 출처 표기 필요
### ask_place · ask_review · ask_reader · AI에게 묻기 (④, `18_ask_place.sql`, 11/06)

여행 중 "가는 길에 5분 안 카페"처럼 물으면 AI(Gemini)가 SQL을 써 주고, 서버가 검사한 뒤 실행해요. AI는 SQL을 글자로 쓰기만 하고 DB에 붙지 않아요.

| 이름 | 종류 | 내용 |
|---|---|---|
| `ask_place` | 뷰 (retrip_place) | id, name, kind(cafe/food/bar/tour/culture/shop/stay/etc), biz_type, rating_adj(보정 평점), review_count, amenities, use_time, rest_date, lat, lng, geom |
| `ask_review` | 뷰 (retrip_review) | place_id, content |
| `detour_minutes(place, route)` | 함수 | 경로선까지 거리 × 2 ÷ 75m/분, 올림 = 가는 길에서 들르면 몇 분 더 |
| `ask_reader` | 계정(롤, 로그인 불가) | 위 뷰 2개 읽기 + 함수만. 서버가 AI SQL을 실행할 때만 `SET LOCAL ROLE ask_reader` |
| `idx_retrip_review_content_trgm` | 인덱스 (pg_trgm) | 리뷰 글 ILIKE 검색용 |

- kind·rating_adj 계산은 `PlaceMapper.xml`의 kindCase·adjustedRating과 같아요. 한쪽을 고치면 같이 고쳐요
- 확인: `SET ROLE ask_reader; SELECT * FROM member;` → permission denied가 나와야 정상 (`RESET ROLE;`로 돌아오기)

### ⑤ 여행 후 (박석진)
(payment, card, photo, extra_visit, amenity)

### photo · 사진·영상 (⑤, `15_after.sql`, 11/06)

| 컬럼 | 타입 | 설명 |
|---|---|---|
| photo_id | BIGSERIAL PK | |
| trip_id | BIGINT NOT NULL → trip (ON DELETE CASCADE) | 여행 |
| stop_id | BIGINT → trip_stop (ON DELETE SET NULL) | 찍은 시각으로 분류한 장소. NULL이면 이동 중(장소와 장소 사이) |
| media_type | VARCHAR(10) NOT NULL DEFAULT 'photo' | photo / video |
| file_name | VARCHAR(100) NOT NULL | 서버 폴더(`PHOTO_DIR`)에 저장한 파일 이름 (겹치지 않게 새로 지음) |
| original_name | VARCHAR(200) | 올릴 때 원래 파일 이름 |
| duration_sec | INT | 영상 길이(초). 사진이면 NULL |
| taken_at | TIMESTAMP NOT NULL | 찍은 시각 (분류 기준) |
| created_at | TIMESTAMP NOT NULL DEFAULT NOW() | 올린 시각 |

인덱스 `idx_photo_trip (trip_id, taken_at)`. 파일 자체는 DB에 넣지 않고 서버 폴더에 둬요.

### payment · 결제 내역 (⑤, `15_after.sql`, 11/07, 이상재 확인)

| 컬럼 | 타입 | 설명 |
|---|---|---|
| payment_id | BIGSERIAL PK | |
| trip_id | BIGINT NOT NULL → trip (ON DELETE CASCADE) | 여행 |
| stop_id | BIGINT → trip_stop (ON DELETE SET NULL) | 결제 시각에 있던 일정 칸 (실제 도착 ~ 떠남). NULL이면 이동 중·계획 밖 |
| paid_at | TIMESTAMP NOT NULL | 결제 시각 |
| store_name | VARCHAR(100) NOT NULL | 가게 이름 |
| amount | INT NOT NULL CHECK (amount > 0) | 금액(원) |
| method | VARCHAR(10) NOT NULL DEFAULT 'card' | card / cash |
| source | VARCHAR(10) NOT NULL DEFAULT 'manual' | manual(직접 입력) / card(카드 내역 가져옴) / capture(결제 캡처, 나중에) |
| memo | VARCHAR(200) | 메모 |
| payer | VARCHAR(20) NOT NULL DEFAULT '나' | 낸 사람: 나 / 동행 1 / 동행 2 … (정산용, 11/07 ALTER로 추가) |
| created_at | TIMESTAMP NOT NULL DEFAULT NOW() | 저장 시각 |

인덱스 `idx_payment_trip (trip_id, paid_at)`.

### trip_companion · 같이 간 사람 (⑤, `15_after.sql`, 11/07, 이상재 확인)

| 컬럼 | 타입 | 설명 |
|---|---|---|
| companion_id | BIGSERIAL PK | |
| trip_id | BIGINT NOT NULL → trip (ON DELETE CASCADE) | 여행 |
| name | VARCHAR(20) NOT NULL | 이름. `UNIQUE (trip_id, name)` — 정산의 "낸 사람"(`payment.payer`)과 같은 글자 |
| friend_member_id | BIGINT → member (ON DELETE SET NULL) | 친구 기능(①)이 생기면 친구 회원 번호. 지금은 NULL |
| sort_no | INT NOT NULL DEFAULT 0 | 보이는 순서. 0은 '나' |
| share_amount | INT (>= 0) | 더치페이 낼 돈을 직접 정한 금액. NULL이면 자동 (합계 − 정한 금액들을 나머지 사람이 똑같이 나눔). 11/07 ALTER로 추가 |
| created_at | TIMESTAMP NOT NULL DEFAULT NOW() | |

처음 열 때 여행 인원(`trip.people`)만큼 '나', '동행 1' … 을 만들어 둬요. 이름을 바꾸면 그 여행 결제의 `payer`도 같이 바꿔요.

### card · 카드사 연결 (⑤, `15_after.sql`, 11/07, 이상재 확인)

| 컬럼 | 타입 | 설명 |
|---|---|---|
| card_id | BIGSERIAL PK | |
| member_id | BIGINT NOT NULL → member (ON DELETE CASCADE) | 회원 |
| organization | VARCHAR(4) NOT NULL | CODEF 카드사 기관코드 |
| organization_name | VARCHAR(20) NOT NULL | 카드사 이름 (화면용) |
| connected_id | VARCHAR(100) NOT NULL | CODEF 카드사 연결 번호 |
| created_at | TIMESTAMP NOT NULL DEFAULT NOW() | |

`UNIQUE (member_id, organization)`. **카드사 아이디·비밀번호·카드 번호는 저장하지 않아요** (연결할 때 암호화해서 CODEF로만 보냄).


### crowd_cell (② 이찬기, 19_crowd.sql) — 250m 칸
| 컬럼 | 타입 | 설명 |
|---|---|---|
| cell_id | VARCHAR(20) PK | 격자 ID (예: 다사47254650) |
| dong_code | VARCHAR(10) | 행정동 코드 (여러 동에 걸치면 인원이 가장 많은 동) |
| x5179 | INTEGER | 칸 왼쪽 아래 X (EPSG:5179) |
| y5179 | INTEGER | 칸 왼쪽 아래 Y |
| geom | geometry(Polygon, 4326) | 250m 네모, GIST 인덱스 |

### crowd_hour (② 이찬기, 19_crowd.sql) — 칸 × 요일 × 시각 평소 인원
| 컬럼 | 타입 | 설명 |
|---|---|---|
| cell_id | VARCHAR(20) FK → crowd_cell | |
| day_kind | SMALLINT | 1=월 … 7=일, 8=공휴일 |
| hour | SMALLINT | 7~23시 |
| people | INTEGER | 평균 인원 (내국인+단기외국인+장기외국인) |
| foreign_short | INTEGER | 그중 단기외국인 |
| seoul_pct | SMALLINT | 같은 요일·시각 서울 칸 중 순위 0~100 |
| self_pct | SMALLINT | 이 칸 가장 붐빌 때 대비 % |

PK (cell_id, day_kind, hour). 지금은 중구·강남구·종로구 1,076칸, 146,135줄. 출처: 서울 열린데이터광장 생활인구(250m), 최근 8주(2026-09-05~10-31) 평균, 공휴일만 1년치.