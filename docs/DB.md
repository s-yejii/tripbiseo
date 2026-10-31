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
| `db/11_member.sql` | ① 테이블 | ① |
| `db/12_trip.sql` | ② 테이블 (`trip_chat`) | ② |
| `db/13_plan.sql` | ③ 테이블 | ③ |
| `db/14_live.sql` | ④ 테이블 (`trip_track`) | ④ |
| `db/15_after.sql` | ⑤ 테이블 | ⑤ |
| `db/90_test_data.sql` | 테스트 계정(tripuser / tripuser1234)·샘플 여행 | DB 총괄 |
| `db/91_drop_old.sql` | 예전 DB에서 안 쓰는 테이블 정리 (예전 DB만 한 번) | DB 총괄 |

파일 번호 순서대로(01 → 02 → 03~05 → 11~15 → 90) 실행하면 내 PC DB에 모든 테이블이 생겨요. 모든 SQL은 다시 실행해도 오류가 나지 않게 써요 (`IF NOT EXISTS`, `ON CONFLICT DO NOTHING`).

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
| ① | `member_style` | 여행 스타일 | ③ 읽기 | 만들 예정 |
| ① | `notification` | 알림함 | ④⑤는 `NotificationService.send`로 | 만들 예정 |
| ① | `friend` | 친구 | | 만들 예정 (여유) |
| ② | `trip_chat` | 대화 기록 | ③④는 ② 메서드로 저장 | 있음 |
| ② | `pack_item` | 챙길 것 | | 만들 예정 (여유) |
| ③ | `theme`, `theme_stop` | 테마 코스 | ② 읽기 | 만들 예정 |
| ③ | `vector_store` | RAG 임베딩 (768차원) | | 만들 예정 (여유) |
| ④ | `trip_track` | GPS 기록 | ⑤① 읽기 | 있음 |
| ⑤ | `payment` | 결제 메모 | | 만들 예정 |
| ⑤ | `card` | 결제 카드 | | 만들 예정 |
| ⑤ | `photo` | 앨범 사진 | | 만들 예정 (여유) |
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

> 반경·근처 검색은 `geom`으로 해요: `WHERE ST_DWithin(geom, ST_MakePoint(lng, lat)::geography, 반경m) ORDER BY geom <-> ST_MakePoint(lng, lat)::geography`

### retrip_review · 장소 리뷰

| 컬럼 | 타입 | 설명 |
|---|---|---|
| id | TEXT PK | 리뷰 id |
| place_id | TEXT NOT NULL → retrip_place | 장소 |
| source | TEXT NOT NULL | 출처 |
| content | TEXT NOT NULL | 리뷰 본문 |
| created_at | TEXT NOT NULL | |

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

### ④ 여행 중 (신예지)
(trip_track은 위에 있음. 더 필요하면 여기에)

### ⑤ 여행 후 (박석진)
(payment, card, photo, extra_visit, amenity)
