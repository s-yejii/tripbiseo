# 담당별 할 일 (ROLES)

"나 N번 담당이야, 뭐부터 해?"의 답이 있는 문서예요. 뼈대를 받은 다음부터 이 순서대로 하나씩 하면 돼요.

담당(①~⑤)은 아직 사람이 정해지지 않았어요. 번호로만 불러요.

목차: [시작하기](#시작하기) · [개발 순서](#api-하나를-만드는-순서-모두-같음) · [①](#-회원홈알림-member) · [②](#-여행일정점검장소-trip) · [③](#-ai-일정-plan) · [④](#-여행-중-판별-live) · [⑤](#-여행-후지도-부가-after) · [서로 부르는 메서드](#서로-부르는-메서드) · [매주 합쳐 보기](#매주-금요일-합쳐-보기)

## 시작하기

**뼈대에 이미 있는 것** (develop에 들어간 뒤 기준)
- 화면 57개 (프로토타입 그대로, `frontend/src/data`의 가짜 데이터로 돌아감)
- 로그인·회원가입 (진짜로 동작)
- API 31개: Controller → Service 인터페이스 → ServiceImpl까지 연결돼 있고, **Impl은 지금 명세 예시 데이터(가짜)를 돌려줌**
- API 31개의 요청·응답 DTO (명세 필드 그대로), 공통 DTO (Trip, Stop, 장소 카드)
- 공통 테이블 4개 + 이미 모은 장소·리뷰 데이터, 테스트 계정·샘플 여행
- 외부 API 호출부 (Gemini, TMAP, ODsay, 기상청)
- **완성 예시 1개**: `GET /api/trips` (테이블 → DTO → Mapper → Service → Controller → 화면). 막히면 이걸 보고 똑같이 따라 하기

**각자 할 것**: ServiceImpl 안의 진짜 로직, Mapper 인터페이스 + XML, 자기 테이블, 화면 연결, "확인 필요" API

**첫날 할 일 (모두 같음)**
1. `git switch develop` → `git pull` → `git switch -c feature/○○`
2. README대로 DB·서버·화면 켜기
3. 자기 화면들을 보고 필요한 테이블·컬럼을 `docs/DB.md`의 자기 칸에 적고 PR → 리뷰에서 다른 테이블과 겹치지 않는지 확인 (DB 총괄이 정해지면 DB 총괄이 확인)

**표시**: ★ = 12/4까지 반드시 (10/15 팀 결정). ★가 다 끝나기 전에는 여유 기능과 "확인 필요" API를 시작하지 않아요.

## API 하나를 만드는 순서 (모두 같음)

```
① 테이블: docs/DB.md에 등록 → db/1N_담당.sql에 CREATE TABLE
② DTO: 테이블 한 줄을 담을 클래스 (API 요청·응답 DTO는 뼈대에 있음)
③ Mapper: 인터페이스 + resources/mapper/{기능}/*.xml 에 SQL
④ ServiceImpl: 가짜 값을 지우고 진짜 로직 (Mapper 호출, 계산)
⑤ 확인: 서버 켜고 API 호출해 보기 (응답이 docs/API.md와 같은지)
⑥ 화면: src/data 가짜 데이터 대신 api/http.js로 API 호출
⑦ PR (develop ← 내 브랜치), PR 템플릿 체크
```

Controller와 Service 인터페이스는 뼈대에 있으니 손대지 않아요. 바꿔야 하면 CLAUDE.md의 구조 변경 규칙을 따라요.

---

## ① 회원·홈·알림 (member)

- **화면** `views/member`: 온보딩, 로그인, 회원가입, 계정 찾기, 권한(2), 홈(로그인 전), 알림 센터, 마이, 정보 수정, 여행 스타일, 친구 초대, 위치 기록, 잠금화면 알림(5)
- **내 테이블**: `member`(공통, 이미 있음), `member_style`, `notification`, `friend`
- **다른 번호가 기다리는 것**: `NotificationService.send` (④⑤), `MemberService.getStyle` (③)

| 순서 | 할 일 | API | 비고 |
|---|---|---|---|
| 1 ★ | 로그인·회원가입·내 상태·로그아웃 다듬기 | `POST /api/auth/login`, `GET /api/auth/me`, `POST /api/auth/logout`, `POST /api/auth/signup` | 뼈대에서 이미 동작. 화면(Login, Signup)에 API 연결 |
| 2 | 알림 테이블 + **`NotificationService.send` 진짜로** | (내부 메서드) | ④⑤가 부름. 먼저 끝내면 다른 사람이 편해요 |
| 3 | 알림 목록·모두 읽음 | `GET /api/notifications`, `POST /api/notifications/read-all` | 확인 필요 → API.md에 먼저 확정 |
| 4 | 내 정보 보기·수정 | `GET·PUT /api/members/me` | 확인 필요 |
| 5 | 여행 스타일 + `MemberService.getStyle` | `GET·PUT /api/members/me/style` | 확인 필요. ③이 AI 일정에 씀 |
| 여유 | 알림 설정, 친구, 위치 기록, 계정 찾기, 간편 로그인 | API.md ① "확인 필요" 참고 | 위치 기록은 ④의 `TrackService` 부름 |

## ② 여행·일정·점검·장소 (trip)

- **화면** `views/trip`: 홈(여행 날), 여행 시간 확인, 내 여행(+빈 상태), 지도에서 직접 고르기, 일정 점검, 일정 편집, 일정 한 칸 수정, 빼기 확인, 여행 상세(여행 전·여행 날), 장소 상세
- **내 테이블**: `trip`, `trip_stop`, `retrip_place`(공통, 이미 있음), `trip_chat`(이미 있음), `pack_item`(여유)
- **다른 번호가 기다리는 것**: `TripService` 공유 메서드 (③④⑤), `PlaceService.card·openInfo` (④)

| 순서 | 할 일 | API | 비고 |
|---|---|---|---|
| 1 ★ | **`TripService` 공유 메서드 진짜로**: getTrip, createTrip, updateStop, insertStop, skipStops | (내부 메서드) | ③④가 기다려요. 제일 먼저 |
| 2 ★ | 여행 목록·저장·상세·수정·삭제 | `GET·POST /api/trips`, `GET·PUT·DELETE /api/trips/{id}` | 목록은 뼈대 완성 예시. 나머지를 같은 방식으로 |
| 3 ★ | 일정 칸 추가·수정·삭제 | `POST /api/trips/{id}/stops`, `PUT·DELETE /api/trips/{id}/stops/{stopId}` | `mode`·`memo` null 규칙 주의 (API.md) |
| 4 ★ | 여행 시작·마치기 | `POST /api/trips/{id}/start`, `.../finish` | |
| 5 ★ | 일정 점검 (이동시간·영업시간·휴무·고정 일정 여유) | `POST /api/plans/check` | 공통 외부 API(TMAP·ODsay) 사용 |
| 6 | 장소 검색·근처·상세 + `PlaceService.card·openInfo` | `GET /api/places/search`, `/nearby`, `/{id}` | 직접 고르기 화면에 필요 |
| 7 | 대화 기록 | `GET·POST /api/trips/{id}/chats` | ③④가 저장 |
| 여유 | 챙길 것 목록 | 확인 필요 | |

## ③ AI 일정 (plan)

- **화면** `views/plan`: 테마 코스, 새 여행(방법 고르기), 여행 정보 입력, 장소 검색·시간 고정, 숙소 핀, 예약 캡처, 원하는 것 말하기, 만드는 중, 생성 실패, 한 곳 빼고 다시, 릴스에서 담기
- **내 테이블**: `theme`, `theme_stop`, `vector_store`(RAG)
- **기다리는 것**: ②의 `TripService.createTrip` (저장은 ②의 `POST /api/trips`로), ①의 `MemberService.getStyle`

| 순서 | 할 일 | API | 비고 |
|---|---|---|---|
| 1 | 사용자 말 → 여행 조건 정리 | `POST /api/plans/chat` | 공통 GeminiService 사용 |
| 2 | 조건 → 일정 초안 | `POST /api/plans/generate` | 결과는 ② 일정 점검 화면으로 넘어감. 초안 Stop이 ②의 저장 규칙과 맞아야 함 |
| 3 | 예약 캡처 → 고정 일정 | `POST /api/plans/capture` | 확인 필요 |
| 4 | 테마 코스 목록·상세 | `GET /api/themes`, `/{id}` | 확인 필요. 홈(②)에도 보여 줌 |
| 여유 | 링크(인스타·릴스) 장소 추출, 숙소 핀, RAG | 확인 필요 | 링크 추출은 외부 사이트 막힘으로 안 될 수 있음 (10/21 리스크) |

## ④ 여행 중 판별 (live)

- **화면** `views/live`: 여행 중(+지연 배너), 핀 눌러 미리 바꾸기, GPS 끊김, 지연 감지, 대안(시간 줄이기·한 곳 빼기·장소 바꾸기), 바뀐 일정, 예약에 늦어요
- **내 테이블**: `trip_track`(GPS, 이미 있음)
- **기다리는 것**: ②의 `TripService`(getTrip, updateStop, insertStop, skipStops), `PlaceService`, ①의 `NotificationService.send`
- **다른 번호가 기다리는 것**: `TrackService.getTrack·deleteAll` (⑤①)

| 순서 | 할 일 | API | 비고 |
|---|---|---|---|
| 1 ★ | GPS 한 점 저장 + 도착·출발 판정 | `POST /api/trips/{id}/live/track` | 150m 안 도착, 200m 밖 2번 출발 |
| 2 ★ | 지금 상황 다시 계산 (출발·지각 알림, 여유, 날씨) | `POST /api/trips/{id}/live/status` | 가장 큰 기능. 예전 LiveService 참고 |
| 3 ★ | 대안 A·B·C 만들기 | `POST /api/trips/{id}/live/alternatives` | |
| 4 ★ | 고른 대안 적용 | `POST /api/trips/{id}/live/apply` | ②의 updateStop·insertStop·skipStops 사용 |
| 5 | `TrackService.getTrack·deleteAll` 진짜로 | (내부 메서드) | ⑤ 회고, ① 위치 기록이 부름 |
| 6 | 시연용 가짜 GPS 재생 | (시연 모드) | 2주차 안에 (10/21 리스크) |
| 여유 | 근처 추천·넣기, 여행 중 채팅, 줄서기 대기, "그냥 갈게요" | `/live/recommend`, `/live/insert`, `/live/assist`, 확인 필요 | |

## ⑤ 여행 후·지도 부가 (after)

- **화면** `views/after`: 길 안내, 결제 메모, 지난 여행 다시보기(회고), 앨범, 결제 카드 등록
- **내 테이블**: `payment`, `card`, `photo`, `extra_visit`, `amenity`
- **기다리는 것**: ④의 `TrackService.getTrack`, ②의 `TripService.getTrip`

| 순서 | 할 일 | API | 비고 |
|---|---|---|---|
| 1 | 회고: 계획 vs 실제 + GPS 선 | `GET /api/trips/{id}/retro` | ④ 전이면 가짜 GPS 값으로 먼저 |
| 2 | 결제 메모 | `GET·POST /api/trips/{id}/payments` | 확인 필요 |
| 3 | 결제 카드 | `GET·POST·PUT /api/cards` | 확인 필요 |
| 4 | 길 안내 | `GET /api/route` | 확인 필요. 공통 TMAP·ODsay 사용 |
| 여유 | 편의시설(화장실·쓰레기통), 앨범, 들른 곳 | 확인 필요 | 편의시설은 공공데이터 수집 필요 |

⑤는 ★가 적어서, 회고·결제를 끝내면 ④(가짜 GPS 재생)나 ②(장소 검색)를 도울 수 있어요. 2주차에 진행 상황을 보고 정해요.

---

## 서로 부르는 메서드

다른 번호가 부르는 Service 메서드예요. **이름·파라미터·반환 타입은 뼈대에서 확정**하고, 바꿀 때는 CLAUDE.md의 구조 변경 규칙을 따라요. 주인이 아직 못 만들었으면 뼈대의 가짜 값이 돌아와요.

| 주인 | 메서드 | 하는 일 | 부르는 번호 |
|---|---|---|---|
| 공통 | `LoginUser.id()` | 지금 로그인한 회원 번호 | 전원 |
| 공통 | `GeminiService`, `RouteService`, `WeatherService` | 외부 API 호출 | ②③④⑤ |
| ① | `NotificationService.send(memberId, title, text)` | 알림 보내기 | ④⑤ |
| ① | `MemberService.getStyle(memberId)` | 여행 스타일 | ③ |
| ② | `TripService.getTrip(memberId, tripId)` | 여행 + 일정 칸 | ③④⑤ |
| ② | `TripService.createTrip(memberId, trip)` | 여행 저장 | ③ |
| ② | `TripService.updateStop(…)`, `insertStop(…)`, `skipStops(…)` | 일정 칸 바꾸기 | ④ |
| ② | `PlaceService.card(…)`, `PlaceService.openInfo(…)` | 장소 카드, 영업 정보 | ④ |
| ④ | `TrackService.getTrack(tripId)` | GPS 기록 | ⑤ |
| ④ | `TrackService.deleteAll(memberId)` | 위치 기록 지우기 | ① |

정확한 파라미터·반환 타입은 각 Service 인터페이스 파일을 봐요. 새로 추가하면 이 표도 같은 커밋에서 고쳐요.

## 매주 금요일 합쳐 보기

마지막에 한꺼번에 합치면 그 주가 통째로 디버깅이 돼요. 매주 금요일 develop을 받아서 아래 흐름을 **진짜 데이터로** 끝까지 돌려 봐요.

| 주 | 금요일까지 이어져야 하는 흐름 |
|---|---|
| 1주 | 로그인 → 내 여행 목록 → 여행 상세 (② 진짜, 나머지 가짜여도 OK) |
| 2주 | 로그인 → 직접 일정 만들기 → 점검 → 저장 → 여행 시작 → GPS 기록·도착 판정 |
| 3주 | 여행 중 지금 상황 → 지연 감지·알림 → 대안 → 적용 → 바뀐 일정 (★ 전부) |
| 4주 | `docs/DEMO.md` 시연 시나리오 전체 |
| 5주 | 여유 기능, 버그 |
