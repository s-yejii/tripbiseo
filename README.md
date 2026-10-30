# 여행어때 (트립비서)

여행 중 일정이 틀어지면 먼저 알려 주고, 지금 위치에서 갈 수 있는 대안을 보여 주는 앱입니다.
화면은 Vue 3로 만든 스마트폰 모양 웹앱이고, 서버는 Spring Boot + MyBatis + PostgreSQL(PostGIS)입니다.

## 폴더 구조

```
tripbiseo/
├─ db/        PostgreSQL 스크립트 (번호 순서대로 실행)
├─ docs/      API.md(API 기준) · DB.md(테이블 기준) · ROLES.md(담당별 할 일) · DECISIONS.md · DEMO.md
├─ backend/   Spring Boot (Java 17)
│  └─ src/main/java/com/tripbiseo/
│     ├─ member/  ① 회원·홈·알림      ─┐
│     ├─ trip/    ② 여행·일정·장소      │ 기능마다 controller / service / mapper / dto
│     ├─ plan/    ③ AI 일정            │ (Service는 인터페이스 + Impl)
│     ├─ live/    ④ 여행 중 판별        │
│     ├─ after/   ⑤ 여행 후·지도 부가  ─┘
│     ├─ common/  exception(오류) · auth(로그인 사용자) · ai(Gemini) · external(길찾기·날씨·장소 API) · util · dto
│     └─ config/  보안·Swagger·MyBatis 설정
│  └─ src/main/resources/mapper/{기능}/*.xml   SQL
└─ frontend/  Vue 3 + Vite — views(화면) / components(지도·시트·모달) / api(서버 호출) / router / data(가짜 데이터) / utils
```

## 처음 실행

### 1. DB

PostgreSQL에 `tripbiseo` 데이터베이스를 만들고(PostGIS 설치 필요), `db/` 파일을 **번호 순서대로** 실행합니다.

| 순서 | 파일 | 내용 |
|---|---|---|
| 1 | `01_common.sql` | PostGIS + 공통 테이블 (member, trip, trip_stop, retrip_place, retrip_review) |
| 2 | `02_retrip_schema.sql` | 예전 결제·테마 테이블 (retrip_expense, retrip_theme) |
| 3~5 | `03` ~ `05` | 장소·리뷰 데이터 (04·05는 큰 파일, 몇 분 걸림) |
| 6 | `11_member.sql` ~ `15_after.sql` | 담당별 테이블 |
| 7 | `90_test_data.sql` | 테스트 계정 **tripuser / tripuser1234** + 오늘 날짜 샘플 여행 |
| (예전 DB만) | `91_drop_old.sql` | 예전 `01_member`~`06_tripbiseo` 시절 DB의 안 쓰는 테이블 정리 |

pgAdmin에서 DB를 우클릭 → PSQL Tool을 열고, 저장소 폴더 기준 경로로 입력합니다.
```
\encoding UTF8
\i 'db/01_common.sql'
```
(PSQL Tool이 저장소 폴더에서 열리지 않으면 `\cd '저장소 폴더'`로 먼저 이동합니다.) 모든 SQL은 다시 실행해도 오류가 나지 않습니다.

### 2. 내 PC 설정 (DB 비밀번호·API 키)

`backend/src/main/resources/application-local.properties.example`을 같은 폴더에 **`application-local.properties`** 로 복사하고 값을 채웁니다.
- 이 파일은 `.gitignore`에 있어서 커밋되지 않습니다. 키·비밀번호는 여기(또는 환경변수)에만 적습니다.
- 메모장으로 저장할 때 인코딩은 **UTF-8(BOM 없음)** 으로 합니다. BOM이 붙으면 첫 줄을 못 읽습니다.
- 이름 뒤에 `.txt`가 붙지 않았는지 확인합니다 (파일 탐색기 → 보기 → 파일 확장명).
- 같은 이름의 환경변수가 있으면 환경변수가 먼저입니다.

### 3. 백엔드

이클립스에서 `backend` 폴더를 Maven 프로젝트로 가져와 `TripbiseoApplication`을 실행하거나, `backend` 폴더에서:
```
.\mvnw.cmd spring-boot:run
```
포트는 8080입니다. API 키가 하나도 없어도 서버는 켜집니다 (AI·길찾기는 규칙·추정 값으로 대신 동작).

- **Swagger (API 문서·시험 호출)**: http://localhost:8080/swagger-ui.html (로그인 없이 열림)
  로그인이 필요한 API는 Swagger에서 먼저 `POST /api/auth/login`을 실행하면 같은 브라우저 쿠키로 불러집니다.
  기준은 `docs/API.md`이고, Swagger와 다르면 API.md가 맞습니다.
- 지금은 `GET /api/trips`(완성 예시)와 로그인만 진짜 DB 값이고, 나머지 API는 가짜 값을 돌려줍니다. 각자 `ServiceImpl`의 `// TODO`를 채웁니다.

### 4. 화면

`frontend` 폴더에서 아래를 실행한 뒤 http://localhost:5173 을 엽니다. `/api` 요청은 8080 서버로 넘어갑니다.
```
npm install
npm run dev
```

## API 키

`application-local.properties`(또는 환경변수)에 아래 이름으로 넣습니다.

| 키 | 어디서 받나 | 없으면 |
|---|---|---|
| `DB_USERNAME`, `DB_PASSWORD` | 내 PC PostgreSQL 계정 | 사용자 이름은 `postgres`, 비밀번호는 비어 있어 DB에 연결되지 않습니다 |
| `GEMINI_API_KEY` | Google AI Studio | AI 일정 만들기·채팅 이해가 안 되고, 대안은 규칙으로만 만듭니다 |
| `GEMINI_MODEL` | (선택) | `gemini-3.8-flash` |
| `KAKAO_JS_KEY` | 카카오 콘솔 → 플랫폼 Web에 `http://localhost:5173` 등록 | 지도가 오픈스트리트맵으로 바뀝니다 |
| `KAKAO_REST_API_KEY` | 같은 카카오 앱의 REST 키 | DB에 없는 장소의 위치를 못 찾고, 네이버 지도 링크만 줍니다 |
| `TMAP_APP_KEY` | SK open API (대중교통은 상품 신청이 필요) | ODsay로, 그것도 없으면 거리로 이동시간을 추정합니다 |
| `ODSAY_API_KEY` | ODsay LAB (서버 IP 등록) | TMAP이 실패했을 때 대신할 길찾기가 없습니다 |
| `TOUR_API_KEY` | 공공데이터포털 '한국관광공사_국문 관광정보 서비스_GW' (Decoding 키) | 기상청 키로 대신 부릅니다 |
| `KMA_SERVICE_KEY` | 공공데이터포털 '기상청_단기예보 조회서비스' (Decoding 키) | 날씨 알림·날씨 판단이 빠집니다 |

## 예전 코드 보기

뼈대 전의 백엔드 코드(로직 참고용)는 `backend-v0` 태그에 있습니다. 예: `git show backend-v0:backend/src/main/java/com/tripbiseo/service/LiveService.java`

## 알아 둘 것

- 웹 위치 찾기(내 위치)는 `localhost`나 https 주소에서만 됩니다. PC에서는 와이파이 기준의 대략적인 위치가 잡힙니다.
- 앱이 꺼져 있을 때 오는 푸시 알림은 아직 없습니다. 여행 중 화면이 열려 있는 동안만 알림이 옵니다.
- CSRF 보호는 팀 설정대로 꺼 두었습니다. 배포하기 전에 켜는 것을 검토해야 합니다.
- 네이버 장소·리뷰 데이터는 서울 프로토타입용입니다(약관상 자동 수집은 금지). 최종 구조에는 공식 API를 적어야 합니다.
- Gemini에는 개인정보·위치(좌표) 기록을 보내지 않습니다 (무료 등급).
