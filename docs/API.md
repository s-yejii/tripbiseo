# 여행어때 API 명세

우리 팀 API의 **기준** 문서예요. API를 만들거나 고치기 전에 이 파일을 먼저 보고, 바꿀 때는 `CLAUDE.md`의 API 규칙을 따라요.

- **사람이 볼 곳:** 맨 위 [한눈에 보기](#한눈에-보기) 표만 보면 돼요.
- **Claude가 볼 곳:** 아래 상세. 요청·응답 필드 이름과 타입, 오류 문장은 이 문서와 똑같이 만들어요.
- 백엔드 뼈대에 Swagger(springdoc-openapi v3)를 붙이면, 실제 코드 기준 문서는 Swagger 화면(`/swagger-ui.html`)에서도 볼 수 있어요. 둘이 다르면 이 파일이 기준이에요.

목차: [상태 표시](#상태-표시) · [담당 번호](#담당-번호) · [한눈에 보기](#한눈에-보기) · [공통 규칙](#공통-규칙) · [공통 데이터](#공통-데이터) · [①](#-회원홈알림-member) · [②](#-여행일정점검장소-trip) · [③](#-ai-일정-plan) · [④](#-여행-중-판별-live) · [⑤](#-여행-후지도-부가-after) · [서로 맞춰야 하는 API](#서로-맞춰야-하는-api) · [정해야 할 것](#정해야-할-것)

## 상태 표시

| 상태 | 뜻 |
|---|---|
| 있음 | 요청·응답이 정해진 API. 이 모양대로 만들어요 |
| 확인 필요 | 화면은 있는데 API가 아직 안 정해졌어요. 주소는 **후보**일 뿐이에요. 담당자가 정한 뒤 이 파일에 상세를 채워요 |
| 삭제 예정 | 이제 안 쓰는 API예요. 새로 만들지 않아요 |

## 담당 번호

| 번호 | 담당 | 맡은 일 | 앞단 폴더 |
|---|---|---|---|
| ① | 이상재 | 회원·홈·알림 (+공통 UI) | `views/member`, `components/member` |
| ② | 이찬기 | 여행·일정 관리·점검·장소 검색·상세 | `views/trip`, `components/trip` |
| ③ | 김주형 | AI 일정 만들기·예약 캡처·숙소 핀·링크·테마 | `views/plan`, `components/plan` |
| ④ | 신예지 | 여행 중 판별 엔진 (도착·지연·대안) | `views/live`, `components/live` |
| ⑤ | 박석진 | 여행 후·지도 부가 (회고·앨범·결제 메모·길 안내·편의시설) | `views/after`, `components/after` |

담당은 10/30에 확정했어요. 아래 표와 문서에서는 짧게 번호(①~⑤)로 불러요.

## 한눈에 보기

### ① 회원·홈·알림 (member)

| 메서드 · 주소 | 하는 일 | 호출 화면 | 상태 |
|---|---|---|---|
| `POST /api/auth/login` | 로그인 (세션 쿠키 발급) | member/LoginView | 있음 |
| `GET /api/auth/me` | 새로고침 후 로그인 상태 확인 | router/index.js (화면 이동 전마다) | 있음 |
| `POST /api/auth/logout` | 로그아웃 | member/MyView | 있음 |
| `POST /api/auth/signup` | 회원가입 | member/SignupView | 있음 |
| `GET /api/auth/check-id` | 아이디 중복 확인 | member/SignupView | 있음 |
| `GET /api/config/public` | 카카오 지도 JS 키 | 지도를 그리는 모든 화면 | 있음 |
| `GET /api/members/me` | 내 정보 (이름·아이디) | member/ProfileEditView | 있음 |
| `PUT /api/members/me` | 이름(닉네임) 수정 (프로필 사진은 칸이 없어 아직 안 함) | member/ProfileEditView | 있음 |
| `PUT /api/members/me/password` | 비밀번호 바꾸기 (지금 비밀번호 확인) | member/ProfileEditView | 있음 |
| `GET·PUT /api/members/me/style` | 여행 스타일 (속도·좋아하는 곳·걷기·이동 수단·비 오면 실내) | member/StyleEditView, member/SignupView | 확인 필요 |
| `GET·PUT /api/members/me/alarms` | 알림 설정 (출발·지연·날씨 켜기/끄기, 출발 몇 분 전) | member/MyView | 확인 필요 |
| `GET /api/notifications` | 내 알림 최근 50개 + 안 읽은 수 (열 때 일정 알림을 만듦) | member/NotificationsView, member/NotiBell (홈·여행 중 종 아이콘) | 있음 |
| `POST /api/notifications/{id}/read` | 알림 하나 읽음 | member/NotificationsView | 있음 |
| `POST /api/notifications/read-all` | 모두 읽음 | member/NotificationsView | 있음 |
| `GET /api/friends` · `GET /api/members/search?q=` · `POST /api/friends/requests` | 친구 목록·아이디 검색·친구 요청 | member/FriendInviteView | 확인 필요 |
| `GET·PUT·DELETE /api/location-logs` | 내 위치 기록 보기·보관 기간·모두 지우기 | member/LocHistoryView | 확인 필요 |
| `POST /api/auth/email-code` | 이메일 인증번호 보내기 (회원가입·계정 찾기) | member/SignupView, member/FindAccountView | 있음 |
| `POST /api/auth/email-code/verify` | 인증번호 확인 (계정 찾기면 아이디도 알려 줌) | member/SignupView, member/FindAccountView | 있음 |
| `POST /api/auth/reset-password` | 계정 찾기: 새 비밀번호로 바꾸기 | member/FindAccountView | 있음 |
| (주소 미정) | 간편 로그인 (카카오·네이버·구글) | member/LoginView | 확인 필요 |

### ② 여행·일정·점검·장소 (trip)

| 메서드 · 주소 | 하는 일 | 호출 화면 | 상태 |
|---|---|---|---|
| `GET /api/trips` | 내 여행 목록 | trip/MainView, trip/TripsView | 있음 |
| `POST /api/trips` | 여행 저장 (일정 칸까지 한 번에) | trip/PlanCheckView | 있음 |
| `GET /api/trips/{id}` | 여행 상세 | trip/TripDetailView, trip/TripDetailTodayView, live/LiveView | 있음 |
| `PUT /api/trips/{id}` | 여행 정보 수정 (이름·날짜·인원·수단) | trip/PlanEditView | 있음 |
| `DELETE /api/trips/{id}` | 여행 삭제 | trip/TripsView | 있음 |
| `POST /api/trips/{id}/start` | 여행 시작 | trip/MainStartView, trip/TripDetailTodayView | 있음 |
| `POST /api/trips/{id}/finish` | 여행 마치기 | live/LiveView | 있음 |
| `POST /api/trips/{id}/stops` | 일정 칸 추가 | trip/PlanManualView, trip/PlanEditView | 있음 |
| `PUT /api/trips/{id}/stops/{stopId}` | 일정 칸 수정 | trip/StopEditView, live/PinEditView | 있음 |
| `DELETE /api/trips/{id}/stops/{stopId}` | 일정 칸 삭제 | trip/DeleteConfirmView | 있음 |
| `GET /api/trips/{id}/chats` | 이 여행의 대화 기록 | plan/PlanChatView, after/RetroView | 있음 |
| `POST /api/trips/{id}/chats` | 대화 기록 저장 | plan/PlanChatView, live/LiveView | 있음 |
| `POST /api/plans/check?day=1` | 하루 일정 점검 (이동시간·영업시간·휴무·고정 일정 여유) | trip/PlanCheckView | 있음 |
| `GET /api/places/search` | 장소 검색 (이름·분류) | plan/PlaceSearchView, trip/PlanManualView | 있음 |
| `GET /api/places/nearby` | 근처 장소 | trip/PlanManualView | 있음 |
| `GET /api/places/popular` | 요즘 인기 장소 (다른 회원들의 최근 30일 여행 기록) | trip/MainView | 있음 |
| `GET /api/places/areas` | 어디로 가요? 서울 구·동 찾기 (카카오) | plan/PlaceSearchView (`?mode=area`) | 있음 |
| `GET /api/places/locations` · `/locations/here` | 어디서 출발해요? 역·건물·주소 찾기 · 현재 위치 주소 (카카오) | plan/PlaceSearchView (`?mode=origin`) | 있음 |
| `GET /api/places/{id}` | 장소 상세 (영업시간·리뷰) | trip/PlaceView | 있음 |
| (주소 미정) | 챙길 것 목록 | trip/TripDetailView (PackList) | 확인 필요 |

### ③ AI 일정 (plan)

| 메서드 · 주소 | 하는 일 | 호출 화면 | 상태 |
|---|---|---|---|
| `POST /api/plans/chat` | 사용자 말 → 여행 조건 정리 (Gemini) | plan/PlanChatView | 있음 |
| `POST /api/plans/generate` | 조건 → 일정 초안 (저장 안 함) | plan/GeneratingView → trip/PlanCheckView | 있음 |
| `GET /api/themes` · `GET /api/themes/{id}` | 테마 코스 목록·상세 | plan/ThemeView, trip/MainView | 확인 필요 |
| `POST /api/plans/capture` | 예약 캡처 사진 → 장소 이름·시각 (고정 일정) | plan/ReserveCaptureView | 있음 |
| `POST /api/plans/links` | 인스타·릴스 링크 → 장소 추출 | plan/PlanAiView (WishLinks), plan/ShareAddView | 확인 필요 |
| (주소 미정) | 숙소 핀 | plan/StayPinView | 확인 필요 |

### ④ 여행 중 판별 (live)

| 메서드 · 주소 | 하는 일 | 호출 화면 | 상태 |
|---|---|---|---|
| `POST /api/trips/{id}/live/track` | GPS 한 점 저장 + 도착·출발 기록 | live/LiveView | 있음 |
| `POST /api/trips/{id}/live/status` | 지금 상황 다시 계산 (알림·여유·날씨) | live/LiveView, live/LiveDelayView | 있음 |
| `POST /api/trips/{id}/live/assist` | 여행 중 채팅 (Gemini가 말 분류) | live/LiveView (AI 채팅 AskPanel) | 있음 |
| `POST /api/trips/{id}/live/recommend` | 반경 안 평점 상위 3곳 추천 | live/LiveView (AI 채팅 AskPanel) | 있음 |
| `POST /api/trips/{id}/live/insert` | 추천 장소를 다음 일정으로 넣기 | live/LiveView (AI 채팅 AskPanel) | 있음 |
| `POST /api/trips/{id}/live/alternatives` | 대안 A·B·C 만들기 | live/AlternativesView, live/AltTrimView, live/AltDropView, live/AltSwapView | 있음 |
| `POST /api/trips/{id}/live/apply` | 고른 대안을 일정에 반영 | live/AltTrimView, live/AltDropView, live/AltSwapView → live/AppliedView | 있음 |
| `POST /api/trips/{id}/live/preview` | 직접 고친 남은 일정 계산 (저장 안 함) | live/AlternativesView, live/AltTrimView, live/AltDropView, live/AltSwapView | 있음 |
| `GET /api/trips/{id}/live/swap-candidates` | 바꿀 장소 후보 (바꿨을 때 여유 포함) | live/AltSwapView | 있음 |
| (주소 미정) | 줄서기 대기 시간 반영 | live/LiveView (TablingModal) | 확인 필요 |
| (주소 미정) | "그냥 갈게요" (늦어도 그대로 가기) | live/LateModalView, member/LockDelayView | 확인 필요 |

### ⑤ 여행 후·지도 부가 (after)

| 메서드 · 주소 | 하는 일 | 호출 화면 | 상태 |
|---|---|---|---|
| `GET /api/trips/{id}/retro` | 회고: 계획 vs 실제 + GPS 선 | after/RetroView | 있음 |
| `GET·POST /api/trips/{id}/payments` | 장소 핀에 결제 금액·누가 냈는지 적기 | after/PayMemoView, member/LockPayView | 확인 필요 |
| `GET·POST·PUT /api/cards` | 결제 카드 등록·기본 카드 | after/CardAddView, member/MyView | 확인 필요 |
| `GET·POST /api/trips/{id}/photos` | 여행 앨범 사진 | after/AlbumView | 확인 필요 |
| `GET /api/route` | 길 안내: 경로선 + 단계별 안내 (도보·버스·지하철·택시) | after/RouteGuideView | 있음 |
| `GET /api/amenities/nearby?kind=` | 근처 화장실·쓰레기통 | after/RouteGuideView, live/LiveView (AmenityChips) | 확인 필요 |
| `POST /api/trips/{id}/visits` | 일정에 없던 곳 "들렀어요" 기록 | live/LiveView (VisitedPin), member/LockStayView | 확인 필요 |

### 삭제 예정

| 메서드 · 주소 | 이유 |
|---|---|
| `POST /api/chat` | Gemini 테스트용. 화면에서 안 씀 |
| `GET /db-test` | DB 연결 확인용 |

---

## 공통 규칙

| 항목 | 규칙 |
|---|---|
| 주소 | 모두 `/api`로 시작 |
| 성공 응답 | HTTP 200 + 응답 본문 그대로. `{code, data}` 같은 봉투로 감싸지 않음 |
| 실패 응답 | HTTP 상태 코드 + `{"message": "화면에 그대로 보여 줄 한국어 문장"}` |
| 로그인 | `POST /api/auth/login`이 주는 세션 쿠키(JSESSIONID)로 유지. 화면은 `fetch(..., { credentials: 'include' })`로 쿠키를 같이 보냄 |
| 로그인 없이 되는 주소 | `/api/auth/**`, `/api/config/public`. 나머지는 전부 로그인 필요 → 안 했으면 401 "로그인이 필요해요." |
| 시간 | `"HH:mm"` 24시간제 (예: `"14:30"`), 한국 시간. 날씨 시각만 `"HH:00"` |
| 날짜 | `"yyyy-MM-dd"`. 날짜+시각은 도착·출발 기록이 `"yyyy-MM-ddTHH:mm:ss"`, 대화 기록이 `"yyyy-MM-ddTHH:mm"` |
| 단위 | 시간 길이는 모두 **분**, 거리는 모두 **m**. 초 단위 필드 없음 |
| 여유(slack) | `slackMin` = 계획 시각 − 예상 도착(분). **음수면 그만큼 늦음** |
| 좌표 | `lat`(위도), `lng`(경도), WGS84. 경로선 `coords`는 `[[위도, 경도], ...]` (GeoJSON의 [경도, 위도]와 반대) |
| 상태값 | 모두 영어 소문자. 여행 `planned / ongoing / done`, 일정 칸 `todo / done / skipped`, 구간 수단 `walk / transit / taxi` |
| null | 값이 없으면 키는 있고 값이 `null`. 표의 "(null 가능)"이 이 경우 |

**HTTP 상태 코드:** 400 입력 오류 · 401 로그인 필요 · 404 없음 · 409 충돌 · 422 일정 생성 실패 · 502·503·504 외부 API(Gemini 등) 문제 · 500 처리하지 못한 오류

**모든 API 공통 오류**

| 경우 | 응답 |
|---|---|
| JSON이 깨졌거나 타입이 틀림 | 400 "요청 형식이 올바르지 않아요." |
| 필수 쿼리 값이 없음 | 400 "{이름} 값이 필요해요." (예: "q 값이 필요해요.") |
| 그 밖의 예외 | 500 "서버 오류가 났어요. 잠시 후 다시 시도해 주세요." |

---

## 공통 데이터

여러 API가 같이 쓰는 모양이에요. 바꾸면 쓰는 쪽이 다 깨지니까, 바꾸기 전에 [서로 맞춰야 하는 API](#서로-맞춰야-하는-api)를 꼭 봐요.

### Trip (여행) · DB `trip`

| 필드 | 타입 | 설명 |
|---|---|---|
| id | number | 여행 번호 |
| memberId | number | 주인 회원 번호. 요청에 보내도 무시하고 로그인한 회원으로 채움 |
| title | string | 여행 이름 1~60자 (앞뒤 공백 제거) |
| startDate | string | 시작 날짜 |
| endDate | string | 끝 날짜. 기간 1~30일 (끝 ≥ 시작) |
| people | number | 동행 인원 1~20 |
| transports | string[] | 이동 수단 1개 이상: `bus`, `subway`, `walk`, `taxi`, `car` (DB에는 `"subway,bus,walk"` 문자열로 저장) |
| status | string | `planned` / `ongoing` / `done`. 요청 값은 무시하고 start·finish로만 바뀜 |
| stopCount | number (null 가능) | **목록(`GET /api/trips`)에서만** 값이 있음: `skipped`가 아닌 일정 칸 수. 다른 응답에서는 `null` |
| stops | Stop[] | 일정 칸 목록. 목록 응답에서는 항상 `[]`. 상세에서는 `day → time → id` 순 |

```json
{ "id": 12, "memberId": 3, "title": "서울 하루", "startDate": "2026-10-20", "endDate": "2026-10-20",
  "people": 2, "transports": ["subway", "bus", "walk"], "status": "planned", "stopCount": null, "stops": [] }
```

### Stop (일정 한 칸) · DB `trip_stop`

| 필드 | 타입 | 필수 | 설명 |
|---|---|---|---|
| id | number (null 가능) | | 일정 칸 번호. 저장 전 초안이면 `null` |
| tripId | number (null 가능) | | 속한 여행 번호. 초안이면 `null` |
| day | number | 필수 | 몇째 날 (1부터, 여행 일수 이하) |
| placeId | string (null 가능) | | 우리 DB 장소 id (`retrip_place.id`, UUID). DB에 없는 id를 보내면 서버가 `null`로 바꿈 |
| name | string | 필수 | 장소 이름 1~120자 |
| category | string (null 가능) | | 분류 (60자까지) |
| address | string (null 가능) | | 주소 (200자까지) |
| lat | number | 필수 | 위도 -90~90 |
| lng | number | 필수 | 경도 -180~180 |
| time | string | 필수 | 시작 시각 `HH:mm` |
| stay | number | 필수 | 머무는 시간(분) 0~720 |
| fixed | boolean | | 늦으면 안 되는 일정(공연·예약). 안 보내면 `false` |
| mode | string (null 가능) | | 앞 장소에서 오는 수단 `walk` / `transit` / `taxi`. 다른 값은 `null`로 바뀜. `null`이면 거리로 자동 (700m 미만 도보) |
| memo | string (null 가능) | | 메모 200자까지 |
| state | string | | `todo`(갈 곳) / `done`(다녀옴) / `skipped`(뺌). 기본 `todo`. 추가할 때는 보내도 `todo`로 저장 |
| importance | string | | 중요도 `must`(꼭) / `maybe`(가능하면) / `drop`(빼도 됨). 기본·이상한 값은 `maybe`. 수정 때 안 보내면 원래 값 (11/03 추가) |
| arrivedAt | string (null 가능) | | GPS로 150m 안에 들어온 시각. `live/track`이 기록하고 요청 값은 무시 |
| departedAt | string (null 가능) | | 출발한 시각. 200m 밖으로 두 번 연속 나갔거나, 다음 칸에 도착한 시각 |

```json
{ "id": 101, "tripId": 12, "day": 1, "placeId": "e70caff5-27cf-5c76-a1f3-201b7032d672",
  "name": "경복궁", "category": "관광지", "address": "서울 종로구 사직로 161", "lat": 37.5796, "lng": 126.977,
  "time": "10:00", "stay": 90, "fixed": false, "mode": null, "memo": "", "state": "todo", "importance": "maybe",
  "arrivedAt": null, "departedAt": null }
```

### 장소 카드 (Place card)

장소 검색·근처·상세·여행 중 추천이 모두 이 모양을 기본으로 써요.

| 필드 | 타입 | 설명 |
|---|---|---|
| id | string | 장소 id (`retrip_place.id`) |
| name | string | 이름 |
| category | string | 분류 (`biz_type`, 없으면 관광공사 분류: 관광지·문화시설·축제·행사·레포츠·숙박·쇼핑·음식점) |
| address | string (null 가능) | 주소 |
| lat, lng | number | 좌표 |
| rating | number (null 가능) | 평점 |
| reviewCount | number (null 가능) | 리뷰 수 |
| naverUrl | string | 네이버 지도 주소 (DB `map_url`, 없으면 네이버 지도 검색 링크) |
| distance | number (없을 수 있음) | 기준점에서 거리(m, 정수). **기준 좌표가 있을 때만 키가 생김** |

```json
{ "id": "e70caff5-27cf-5c76-a1f3-201b7032d672", "name": "경복궁", "category": "관광지", "address": "서울 종로구 사직로 161",
  "lat": 37.5796, "lng": 126.977, "rating": null, "reviewCount": null, "naverUrl": "https://map.naver.com/p/search/...", "distance": 240 }
```

### 영업 정보 (openInfo)

도착 예상 시각 기준 영업 상태예요. `/live/recommend`의 장소 카드에 합쳐지고, `/live/status`·`/live/alternatives`의 일정 칸에 일부(`closeAt`, `closedReason`, `closingSoon`)가 들어가요. **상황에 따라 키 자체가 없을 수 있어요.**

| 필드 | 타입 | 언제 있나 | 설명 |
|---|---|---|---|
| open | boolean (null 가능) | 항상 | 도착할 때 열려 있나. 영업시간 정보가 없으면 `null` |
| closedReason | string | 닫혀 있을 때만 | `"오늘 휴무 (매주 화요일)"`, `"11:00에 문을 열어요"`, `"22:00에 영업이 끝나요"` |
| closeAt | string | 영업시간을 알고 24시간 영업이 아닐 때 | 닫는 시각 `HH:mm` |
| minutesLeft | number | 열려 있을 때 | 도착 후 닫을 때까지 남은 분 |
| closingSoon | boolean | 열려 있을 때 | 남은 분 ≤ max(60, 머무는 분) |

### 구간 (Leg)

`/plans/check`의 `legs`에 나와요. `stops[].leg`에는 `coords`만 빠진 모양이 들어가요.

| 필드 | 타입 | 설명 |
|---|---|---|
| minutes | number | 이동 시간(분) |
| meters | number | 이동 거리(m) |
| mode | string | `walk` / `transit` / `car` (**Stop.mode의 `taxi`는 여기서 `car`로 바뀜**) |
| source | string | 계산 출처 `tmap` / `odsay` / `estimate`(키가 없거나 실패해 거리로 추정) |
| detail | string (null 가능) | 탈 노선 설명 (대중교통만). 예: `"버스 6513 구로디지털단지→대림 3정거장"` |
| coords | number[][] | 경로선 `[[위도, 경도], ...]` (실패하면 출발·도착 두 점) |

---

## ① 회원·홈·알림 (member)

### `POST /api/auth/login`
로그인하고 세션 쿠키를 받아요. · 로그인 불필요 · 화면: member/LoginView

**요청**

| 필드 | 타입 | 필수 | 설명 |
|---|---|---|---|
| username | string | 필수 | 아이디 (앞뒤 공백 제거) |
| password | string | 필수 | 비밀번호 |

**응답**
```json
{ "username": "tripuser", "nickname": "여행자", "admin": false }
```
`nickname`은 null 가능, `admin`은 관리자(`ROLE_ADMIN`)인지.

**오류** 400 "아이디와 비밀번호를 입력해 주세요." · 401 "아이디 또는 비밀번호가 맞지 않아요." · 401 "회원 정보를 찾을 수 없어요."

### `GET /api/auth/me`
새로고침한 뒤 로그인 상태를 확인해요. · 로그인 불필요(안 했으면 401로 알려 줌) · 화면: router/index.js (화면 이동 전마다)

**요청** 없음

**응답** `POST /api/auth/login`과 같은 `{username, nickname, admin}`

**오류** 401 "로그인이 필요해요." · 401 "회원 정보를 찾을 수 없어요."

### `POST /api/auth/logout`
로그아웃해요 (세션 삭제). · 로그인 불필요 · 화면: member/MyView

**요청** 없음 · **응답** `{ "ok": true }` · **오류** 없음

### `POST /api/auth/signup`
회원가입해요. 자동 로그인은 안 되니까 화면이 이어서 `login`을 불러요. · 로그인 불필요 · 화면: member/SignupView

**요청**

| 필드 | 타입 | 필수 | 설명 |
|---|---|---|---|
| username | string | 필수 | 영문·숫자·`_` 4~30자 |
| password | string | 필수 | 8~72자 (BCrypt로 저장) |
| nickname | string | | 20자까지. 비우면 아이디로 저장 |
| email | string | 필수 | 이 브라우저에서 `POST /api/auth/email-code/verify`(purpose signup)로 인증을 마친 이메일 (11/03 추가) |

**응답** `{ "ok": true }`

**오류** 400 "이메일 인증을 먼저 해 주세요." · 400 "아이디는 영문·숫자·_ 4~30자예요." · 400 "비밀번호는 8~72자예요." · 400 "닉네임은 20자까지예요." · 409 "이미 쓰는 아이디예요." · 409 "이미 가입된 이메일이에요."

### `POST /api/auth/email-code`
이메일로 6자리 인증번호를 보내요 (Gmail SMTP, 보내는 계정은 `MAIL_USERNAME`). 번호는 서버 메모리에 5분만 두고, 받은 브라우저(세션)만 확인할 수 있어요. · 로그인 불필요 · 화면: member/SignupView, member/FindAccountView (11/03 추가)

**요청** `{ "email": "trip@example.com", "purpose": "signup", "username": null }`
- `purpose`: `signup`(아직 가입 안 된 이메일만) / `find`(가입된 이메일만)
- `username`: find에서 비밀번호를 찾을 때만. 그 아이디의 이메일이 맞는지 확인

**응답** `{ "expiresInSec": 300, "resendAfterSec": 60 }`

**오류** 400 "이메일 형식이 맞지 않아요." · 400 "purpose는 signup·find 중 하나예요." · 409 "이미 가입된 이메일이에요." · 404 "이 이메일로 가입한 회원이 없어요." · 404 "아이디와 이메일이 맞지 않아요." · 429 "인증번호는 1분에 한 번 받을 수 있어요." · 503 "메일 계정이 설정되지 않았어요…" · 502 "메일 계정 로그인에 실패했어요…" · 502 "인증 메일을 보내지 못했어요…"

### `POST /api/auth/email-code/verify`
인증번호를 확인해요. 맞으면 이 브라우저에서 30분 동안 "인증됨"이라 가입·비밀번호 바꾸기를 할 수 있어요. 5번 틀리면 번호를 다시 받아야 해요. · 로그인 불필요 (11/03 추가)

**요청** `{ "email": "trip@example.com", "code": "482915", "purpose": "signup" }`

**응답** `{ "verified": true }` · purpose가 `find`면 `{ "verified": true, "username": "tripuser" }`

**오류** 400 "인증번호를 먼저 받아 주세요." · 400 "인증번호 시간이 지났어요. 다시 받아 주세요." · 400 "인증번호가 맞지 않아요. (n/5)" · 400 "5번 틀려서 인증번호를 지웠어요. 다시 받아 주세요."

### `POST /api/auth/reset-password`
계정 찾기에서 이메일 인증(purpose find)을 마친 브라우저만 새 비밀번호로 바꿔요. · 로그인 불필요 (11/03 추가)

**요청** `{ "email": "trip@example.com", "newPassword": "새 비밀번호" }` (8~72자)

**응답** `{ "ok": true }`

**오류** 400 "이메일 인증을 먼저 해 주세요." · 400 "비밀번호는 8~72자예요." · 404 "이 이메일로 가입한 회원이 없어요."

### `GET /api/auth/check-id`
가입 전에 아이디를 쓸 수 있는지 확인해요. · 로그인 불필요 · 화면: member/SignupView

**요청** 주소 뒤 `?username=아이디` (예: `/api/auth/check-id?username=sangjae`)

**응답**
```json
{ "available": true }
```
`available`이 false면 이미 쓰는 아이디예요.

**오류** 400 "아이디는 영문·숫자·_ 4~30자예요."

### `GET /api/members/me`
내 정보를 줘요. · 로그인 필요 · 화면: member/ProfileEditView

**요청** 없음 · **응답** `GET /api/auth/me`와 같은 `{username, nickname, admin}` · **오류** 401 "로그인이 필요해요." · 404 "회원 정보를 찾을 수 없어요."

### `PUT /api/members/me`
이름(닉네임)을 바꿔요. 아이디는 못 바꿔요. · 로그인 필요 · 화면: member/ProfileEditView (11/03 추가)

**요청** `{ "nickname": "새 이름" }` (앞뒤 공백 빼고 1~20자)

**응답** 바뀐 `{username, nickname, admin}`

**오류** 400 "이름을 1~20자로 입력해 주세요." · 401 "로그인이 필요해요."

### `PUT /api/members/me/password`
비밀번호를 바꿔요. 지금 비밀번호가 맞아야 해요. 로그인은 그대로 유지돼요. · 로그인 필요 · 화면: member/ProfileEditView (11/03 추가)

**요청** `{ "currentPassword": "지금 비밀번호", "newPassword": "새 비밀번호" }` (새 비밀번호 8~72자, BCrypt로 저장)

**응답** `{ "ok": true }`

**오류** 400 "지금 비밀번호가 맞지 않아요." · 400 "비밀번호는 8~72자예요." · 400 "지금 비밀번호와 다른 비밀번호로 정해 주세요." · 401 "로그인이 필요해요."

### `GET /api/notifications`
내 알림함. 최근 것부터 50개와 안 읽은 수를 줘요. · 로그인 필요 · 화면: member/NotificationsView, 홈·여행 중 종 아이콘(member/NotiBell) (11/03 추가)

부를 때 내 여행을 보고 일정 알림을 먼저 만들어 둬요. 같은 알림은 한 번만 쌓여요.
- 내일 시작하는 여행(planned) → "내일 ○○ 여행이에요" (link `/trips/detail?id=`)
- 오늘이 여행 기간인데 아직 시작 안 한 여행 → "오늘 ○○ 여행 날이에요" (날마다 한 번, link `/trips/today?id=`)
- 끝난 지 3일 안 여행(done) → "○○ 여행은 어땠나요?" (link `/retro?id=`)

그 밖의 알림은 쓰는 쪽이 `NotificationService.send`로 넣어요. 지금은 ④ 여행 중 맨 위 알림(`live/status`의 alert, 같은 칸·같은 종류는 하루 한 번)과 ④ 대안 적용(`live/apply`, 적용할 때마다)이에요.

**요청** 없음

**응답**
```json
{
  "unreadCount": 2,
  "notifications": [
    { "id": 12, "kind": "trip", "level": "warn", "title": "10분 뒤 출발해야 해요", "text": "카페 출발 15:52 → 소품샵",
      "link": "/live", "readAt": null, "createdAt": "2026-11-03T15:42:10" }
  ]
}
```

| 필드 | 타입 | 설명 |
|---|---|---|
| kind | string | `trip` 여행 중 / `plan` 일정(내일·오늘 여행, 돌아보기) / `info` 날씨(비 소식) |
| level | string | `ok` / `info` / `warn` / `danger` (아이콘 색) |
| text | string (null 가능) | 설명 |
| link | string (null 가능) | 누르면 갈 화면 주소. 화면은 `?back=/notifications`를 붙여 이동 |
| readAt | string (null 가능) | 읽은 시각 `yyyy-MM-ddTHH:mm:ss`. null이면 안 읽음 |
| createdAt | string | 만든 시각 `yyyy-MM-ddTHH:mm:ss` |

**오류** 401 "로그인이 필요해요."

### `POST /api/notifications/{id}/read`
알림 하나를 읽음으로 표시해요 (이미 읽었으면 그대로). · 로그인 필요 · 화면: member/NotificationsView (11/03 추가)

**요청** 없음 · **응답** `{ "ok": true }` · **오류** 401 "로그인이 필요해요." · 404 "알림을 찾을 수 없어요." (남의 알림 포함)

### `POST /api/notifications/read-all`
내 안 읽은 알림을 모두 읽음으로 표시해요. · 로그인 필요 · 화면: member/NotificationsView (11/03 추가)

**요청** 없음 · **응답** `{ "ok": true }` · **오류** 401 "로그인이 필요해요."

### `GET /api/config/public`
지도를 그릴 카카오 JavaScript 키를 줘요. · 로그인 불필요 · 화면: 지도를 그리는 모든 화면

**요청** 없음

**응답**
```json
{ "kakaoJsKey": "5586...2b31" }
```
키가 설정돼 있지 않으면 빈 객체 `{}`.

**오류** 없음

---

## ② 여행·일정·점검·장소 (trip)

여행 API는 모두 **로그인한 회원의 여행만** 찾아요. 남의 여행 id를 넣으면 404 "여행을 찾을 수 없어요."예요.

### `GET /api/trips`
내 여행 목록. · 로그인 필요 · 화면: trip/MainView, trip/TripsView

**요청** 없음

**응답** `Trip[]`. `stopCount`에 값이 있고 `stops`는 항상 `[]`. 시작 날짜 최신순 → id 큰 순.

**오류** 401 "로그인이 필요해요."

### `POST /api/trips`
여행을 일정 칸까지 한 번에 저장해요. · 로그인 필요 · 화면: trip/PlanCheckView

**요청** Trip 모양

| 필드 | 필수 | 설명 |
|---|---|---|
| title, startDate, endDate, people, transports | 필수 | Trip 표 참고 |
| stops | 필수 (1개 이상) | 각 칸에서 `day, placeId, name, category, address, lat, lng, time, stay, fixed, mode, memo`를 저장. `state`·`arrivedAt` 등은 무시 |

그 밖의 키(예: `notes`, `stopCount`)는 무시해요.

**응답** 저장된 `Trip` (stops 포함, `status: "planned"`)

**오류** 400 "여행 정보가 없어요." · 400 "여행 이름을 1~60자로 적어 주세요." · 400 "여행 날짜를 확인해 주세요." · 400 "여행 기간은 1~30일이에요." · 400 "동행 인원은 1~20명이에요." · 400 "이동 수단을 하나 이상 골라 주세요." · 400 "장소를 하나 이상 담아 주세요." · 400 "이미 지난 날짜로는 여행을 만들 수 없어요." · 일정 칸 검사 오류([일정 칸 추가](#post-apitripsidstops) 참고)

### `GET /api/trips/{id}`
여행 상세 (일정 칸 포함). · 로그인 필요 · 화면: trip/TripDetailView, trip/TripDetailTodayView, live/LiveView

**요청** 경로 `id` 여행 번호

**응답** `Trip` (`stopCount`는 `null`)

**오류** 404 "여행을 찾을 수 없어요."

### `PUT /api/trips/{id}`
여행 정보(이름·날짜·인원·수단)를 고쳐요. · 로그인 필요 · 화면: trip/PlanEditView

**요청** 경로 `id`. 본문은 Trip 모양이지만 **`title, startDate, endDate, people, transports`만 읽어요** (`stops`, `status`는 무시)

**응답** 수정된 `Trip`

**오류** 404 "여행을 찾을 수 없어요." · `POST /api/trips`의 여행 정보 오류(이름·날짜·기간·인원·수단) · 400 "줄인 날짜 밖에 일정이 있어요. 그 일정을 먼저 옮기거나 지워 주세요."

### `DELETE /api/trips/{id}`
여행을 지워요. 일정·GPS·대화 기록도 함께 지워져요. · 로그인 필요 · 화면: trip/TripsView

**요청** 경로 `id` · **응답** `{ "ok": true }` · **오류** 404 "여행을 찾을 수 없어요."

### `POST /api/trips/{id}/start`
여행을 시작해요 (status → `ongoing`). · 로그인 필요 · 화면: trip/MainStartView, trip/TripDetailTodayView

**요청** 경로 `id`, 본문 없음

**응답** `Trip` (`status: "ongoing"`)

**오류** 404 "여행을 찾을 수 없어요." · 400 "여행 기간({시작} ~ {끝})에만 시작할 수 있어요." · 409 "이미 진행 중인 여행({이름})이 있어요. 그 여행을 먼저 마쳐 주세요."

### `POST /api/trips/{id}/finish`
여행을 마쳐요 (status → `done`). 진행 중이 아니어도 막지 않아요. · 로그인 필요 · 화면: live/LiveView

**요청** 경로 `id`, 본문 없음 · **응답** `Trip` (`status: "done"`) · **오류** 404 "여행을 찾을 수 없어요."

### `POST /api/trips/{id}/stops`
일정 칸을 하나 추가해요. · 로그인 필요 · 화면: trip/PlanManualView, trip/PlanEditView

**요청** 경로 `id`. 본문은 Stop 모양: `day, name, lat, lng, time, stay` 필수, `placeId, category, address, fixed, mode, memo` 선택 (`state`는 무시되고 `todo`)

**응답** 추가 후 `Trip` 전체

**오류** 404 "여행을 찾을 수 없어요." · 400 "장소 이름을 확인해 주세요." · 400 "{이름}의 위치를 확인해 주세요." · 400 "{이름}의 날짜가 여행 기간 밖이에요." · 400 "{이름}의 시각을 HH:mm으로 적어 주세요." · 400 "{이름}의 머무는 시간은 0~720분이에요." · 400 "메모는 200자까지예요."

### `PUT /api/trips/{id}/stops/{stopId}`
일정 칸을 고쳐요. 이름·좌표·placeId는 못 바꿔요. · 로그인 필요 · 화면: trip/StopEditView, live/PinEditView

**요청** 경로 `id`, `stopId`. 본문은 Stop 모양 중 아래만 읽어요.

| 필드 | 안 보내면(null) |
|---|---|
| day, time, stay, fixed, state | 원래 값 유지 |
| mode, memo | **`null`로 지워짐** (항상 보낸 값으로 덮어씀) |

> 주의: `mode`·`memo`를 유지하려면 원래 값을 같이 보내야 해요. `state`는 `todo / done / skipped`만 보내요.

**응답** 수정 후 `Trip` 전체

**오류** 404 "여행을 찾을 수 없어요." · 404 "일정을 찾을 수 없어요." · 일정 칸 검사 오류(위와 같음)

### `DELETE /api/trips/{id}/stops/{stopId}`
일정 칸을 지워요. · 로그인 필요 · 화면: trip/DeleteConfirmView

**요청** 경로 `id`, `stopId` · **응답** 삭제 후 `Trip` 전체 · **오류** 404 "여행을 찾을 수 없어요." · 404 "일정을 찾을 수 없어요."

### `GET /api/trips/{id}/chats`
이 여행의 대화 기록 (AI 계획 대화 + 여행 중 대화, 시간순). · 로그인 필요 · 화면: plan/PlanChatView, after/RetroView

**요청** 경로 `id`

**응답**
```json
[ { "role": "me", "text": "근처 카페 추천해 줘", "at": "2026-10-20T14:02" } ]
```
`role`은 `me`(사용자) / `ai`, `text`는 2000자까지, `at`은 `yyyy-MM-ddTHH:mm`.

**오류** 404 "여행을 찾을 수 없어요."

### `POST /api/trips/{id}/chats`
대화 기록 여러 줄을 저장해요. · 로그인 필요 · 화면: plan/PlanChatView, live/LiveView

**요청** 경로 `id`. 본문은 **배열**

| 필드 | 타입 | 필수 | 설명 |
|---|---|---|---|
| role | string | 필수 | `me` / `ai`. 다른 값이면 그 줄은 건너뜀 |
| text | string | 필수 | 비어 있으면 건너뜀. 2000자 넘으면 잘라 저장 |
| at | string | | `yyyy-MM-ddTHH:mm` 또는 `yyyy-MM-ddTHH:mm:ss`. 형식이 다르거나 없으면 저장하는 지금 시각 |

**응답** `{ "ok": true }` · **오류** 404 "여행을 찾을 수 없어요."

### `POST /api/plans/check?day=1`
하루 일정을 점검해요 (실제 이동시간·영업시간·휴무·고정 일정 여유). · 로그인 필요 · 화면: trip/PlanCheckView

**요청** 쿼리 `day`(number, 기본 1). 본문은 Trip 모양(초안 또는 저장된 여행).

| 필드 | 필수 | 설명 |
|---|---|---|
| startDate | 필수 | 점검 날짜 = startDate + (day−1) |
| transports | | 비어 있으면 `["subway","bus","walk"]` |
| stops[] | 필수 | 그날(`day`) 칸만 **보낸 순서대로** 계산. 읽는 키: `id, day, placeId, name, category, lat, lng, time, stay, fixed, mode`. `time`이 `null`이면 앞 일정에 이어서 서버가 정함. `stay`는 필수 |

**응답**

| 필드 | 타입 | 설명 |
|---|---|---|
| day | number | 점검한 날 |
| date | string | 그 날짜 |
| ok | boolean | 이동·영업시간·휴무 검사 모두 통과 |
| fixed | object (null 가능) | 그날 첫 고정 일정 `{name, time, arrive, slack}`. `slack`은 여유(분, 음수=지각). **키 이름이 `slack`** |
| slackMin | number (null 가능) | `fixed.slack`과 같은 값. 고정 일정이 없으면 `null` |
| finalArrive | string (null 가능) | 마지막 장소 예상 도착. 그날 칸이 없으면 `null` |
| checks | object | `{ move, hours, closed }` 모두 boolean (지각 없음 / 영업시간 OK / 휴무 아님) |
| stops[] | object[] | 그날 칸, 보낸 순서. 보낸 값 + 아래 |
| stops[].time | string | 계획 시각 (비어 있었으면 서버가 채운 값) |
| stops[].arrive | string | 예상 도착 `HH:mm` |
| stops[].issue | string (null 가능) | 문제 한 줄: "계획보다 N분 늦게 도착해요", "이날 휴무예요 (...)", "HH:mm에는 영업 시간이 아니에요", "22:00에 문을 닫아 N분밖에 못 머물러요", "이미 지난 시각이에요 (지금 HH:mm)" |
| stops[].leg | object (null 가능) | 앞 장소에서 오는 구간 (Leg에서 `coords` 뺀 모양). 첫 칸은 `null` |
| legs | Leg[] | 구간 목록 (칸 수 − 1개, `coords` 포함) |

```json
{ "day": 1, "date": "2026-10-20", "ok": false,
  "fixed": { "name": "세종문화회관", "time": "19:00", "arrive": "19:12", "slack": -12 }, "slackMin": -12, "finalArrive": "19:12",
  "checks": { "move": false, "hours": true, "closed": true },
  "stops": [ { "id": null, "day": 1, "placeId": "e70caff5-...", "name": "경복궁", "category": "관광지", "lat": 37.5796, "lng": 126.977,
               "time": "10:00", "stay": 90, "fixed": false, "mode": null, "arrive": "10:00", "issue": null, "leg": null } ],
  "legs": [ { "minutes": 18, "meters": 2100, "mode": "transit", "source": "tmap", "detail": "버스 ...", "coords": [[37.57,126.97],[37.56,126.98]] } ] }
```

**오류** 400 "검증할 일정이 없어요."

### `GET /api/places/search`
이름·분류로 우리 DB 장소를 검색해요. · 로그인 필요 · 화면: plan/PlaceSearchView, trip/PlanManualView

**요청** 쿼리

| 이름 | 타입 | 필수 | 설명 |
|---|---|---|---|
| q | string | 필수 | 검색어 1~40자. 이름 또는 분류에 들어간 곳 ("분식집" → "분식") |
| lat, lng | number | | 주면 가까운 순 + `distance` 계산, 없으면 리뷰 많은 순 |

**응답** 장소 카드 배열 (최대 20개). `distance`는 lat·lng를 줬을 때만 있음

**오류** 400 "q 값이 필요해요." · 400 "검색어를 1~40자로 입력해 주세요."

### `GET /api/places/nearby`
지도에서 누른 곳 근처 장소. · 로그인 필요 · 화면: trip/PlanManualView

**요청** 쿼리

| 이름 | 타입 | 필수 | 설명 |
|---|---|---|---|
| lat, lng | number | 필수 | 기준 좌표 |
| radius | number | | 반경(m), 기본 200. 50~2000으로 잘림 |

**응답** 장소 카드 배열, 거리순 최대 10개 (`distance` 항상 있음)

**오류** 400 "lat 값이 필요해요." · 400 "lng 값이 필요해요."

### `GET /api/places/popular`
요즘 인기 장소예요. 신규 회원에게도 똑같이 보여요 (내 기록이 없으면 취향 반영만 빠짐). · 로그인 필요 · 화면: trip/MainView (11/03 추가)

**요청** 주소 뒤 `?lat=37.5&lng=127.0&limit=6&radius=800` (모두 선택)
- `lat`·`lng`가 없으면 서울 시범 지역 전체에서 골라요
- `radius`(200~5000m)를 주면 그 반경만, 없으면 3km → 인기 기록이 모자라면 5km → 그래도 모자라면 시범 지역 전체로 넓혀요
- `limit` 기본 6, 최대 20

**고르는 기준**
- 점수: 다른 회원들의 최근 30일(앞으로 30일 계획 포함) 일정 칸. 다녀옴 1점, 계획 0.3점, 뺌 0점, 최근 7일 여행은 2배. 같으면 보정 평점 순
- 빼는 곳: 평점 3.0 미만, 숙박, 오늘 쉬는 곳, 내가 최근 30일 안에 다녀온 곳
- 같은 분류("카페,디저트")는 2개까지, 같은 묶음(카페/식당/술집)은 절반까지

**응답**
```json
{
  "scope": "near",
  "places": [
    { "place": { /* 장소 카드 (distance 포함) */ }, "visitors": 12, "reason": "최근 한 달 12명이 다녀갔어요" },
    { "place": { /* … */ }, "visitors": null, "reason": "★4.6 · 리뷰 1,234" }
  ]
}
```
`scope`: `near`(기준점 반경) / `wide`(5km로 넓힘) / `seoul`(시범 지역 전체). `visitors`는 5명 이상일 때만 숫자예요(개인정보).

**오류** 401 "로그인이 필요해요."

### `GET /api/places/areas`
여행 정보의 "어디로 가요?"에서 서울 구·동을 이름으로 찾아요. 우리 장소 DB는 주소가 도로명이라 동 이름으로 못 찾아서 카카오 로컬에 물어봐요 (결과는 저장 안 함). · 로그인 필요 · 화면: plan/PlaceSearchView `?mode=area` (11/03 추가)

**요청** 쿼리 `q` (1~40자)

카카오 주소 검색에서 **지역(REGION)이면서 서울**인 것만 골라요. 3개보다 적으면 키워드 검색(서울시청 20km 안, 주소가 서울)으로 보충해요 → "홍대"처럼 동 이름이 아닌 동네도 역·장소 위치로 나와요.

**응답** 최대 10개
```json
[ { "name": "성수동1가", "fullName": "서울 성동구 성수동1가", "type": "dong", "lat": 37.54, "lng": 127.05 },
  { "name": "성동구", "fullName": "서울 성동구", "type": "gu", "lat": 37.56, "lng": 127.03 } ]
```
| 필드 | 설명 |
|---|---|
| type | `gu` 구 / `dong` 동 / `spot` 동네·역 (키워드로 찾은 것) |
| lat, lng | 중심 좌표 |

**오류** 400 "검색어를 1~40자로 입력해 주세요." · 502 "카카오 로컬 …" (키 거절·연결 실패) · 503 "위치 검색 키(KAKAO_REST_API_KEY)가 설정되지 않았어요."

### `GET /api/places/locations`
여행 정보의 "어디서 출발해요?"에서 역·출구·건물·가게·주소를 찾아요 (카카오). · 로그인 필요 · 화면: plan/PlaceSearchView `?mode=origin` (11/03 추가)

**요청** 쿼리 `q` (1~40자), `lat`·`lng` (없어도 됨, 있으면 `distance`가 붙음)

키워드 검색(최대 15개) → 결과가 없으면 주소 검색("천호대로 1005" 같은 도로명 주소). 서울만으로 거르지 않아요 (서울 밖에서 출발할 수 있으니까).

**응답**
```json
[ { "name": "천호역 5호선", "address": "서울 강동구 천호대로 1005", "category": "지하철역", "lat": 37.538, "lng": 127.123, "distance": 420 } ]
```
| 필드 | 설명 |
|---|---|
| address | 도로명 주소, 없으면 지번 주소 |
| category | 카카오 분류의 마지막 칸 (예: 지하철역). 주소 검색으로 찾았으면 `주소` |
| distance | 내 위치에서 거리(m). `lat`·`lng`를 안 보냈으면 키 없음 |

**오류** `/areas`와 같아요

### `GET /api/places/locations/here`
"현재 위치에서 출발": 내 좌표의 건물 이름·주소를 줘요 (카카오 좌표→주소). · 로그인 필요 · 화면: plan/PlaceSearchView `?mode=origin` (11/03 추가)

**요청** 쿼리 `lat`, `lng` (필수)

**응답** `{ "name": "현대백화점 천호점", "address": "서울 강동구 천호대로 1005", "category": "현재 위치", "lat": 37.538, "lng": 127.123 }` (건물 이름이 없으면 `name`도 주소)

**오류** 404 "이 위치의 주소를 찾지 못했어요." · 502·503 `/areas`와 같아요

### `GET /api/places/{id}`
장소 상세 (영업시간·리뷰). · 로그인 필요 · 화면: trip/PlaceView (일정 칸의 `placeId`로 이동)

**요청** 경로 `id`(string) 장소 id

**응답** 장소 카드(`distance` 없음) + 아래

| 필드 | 타입 | 설명 |
|---|---|---|
| tel | string (null 가능) | 전화번호 |
| imageUrl | string (null 가능) | 사진 주소 (`http://`는 `https://`로 바꿈) |
| useTime | string (null 가능) | 영업시간 원문 |
| restDate | string (null 가능) | 휴무 원문 |
| hours | object[] | 요일별 영업시간 `{day, text, today}`. `day`는 `월`~`일` 또는 `매일`. 정보 없으면 `[]` |
| openNow | boolean (null 가능) | 지금 영업 중. 오늘 휴무면 `false`, 정보 없으면 `null` |
| amenities | string[] | 편의시설 (없으면 `[]`) |
| reviews | string[] | 리뷰 본문, 긴 순 최대 10개 |

**오류** 404 "장소를 찾을 수 없어요."

---

## ③ AI 일정 (plan)

**Gemini를 부르는 API 공통 오류:** 503 "Gemini 키가 없어요. 환경변수 GEMINI_API_KEY를 확인해 주세요." · 503 "Gemini 사용량이 몰려 잠시 응답하지 않아요. 1~2분 뒤 다시 시도해 주세요." · 504 "Gemini가 응답하지 않아요(시간 초과). 1~2분 뒤 다시 시도해 주세요." · 502 "Gemini 오류 {상태}: {내용}" · 502 "Gemini 응답이 비어 있어요." · 502 "Gemini 답을 읽지 못했어요. 다시 시도해 주세요." (JSON으로 받을 때만)

### `POST /api/plans/chat`
사용자 말을 여행 조건으로 정리해요 (Gemini). · 로그인 필요 · 화면: plan/PlanChatView

**요청**

| 필드 | 타입 | 필수 | 설명 |
|---|---|---|---|
| message | string | 필수 | 사용자 말 1~300자 |
| conditions | object (null 가능) | | 지금까지의 조건. 처음이면 `null`. 보낸 키는 남고 Gemini가 새로 뽑은 값만 덮어씀 |

**응답**
```json
{ "reply": "10월 20일 종로 하루 여행으로 정리했어요. 맞나요?",
  "conditions": { "title": "종로 하루", "region": "서울 종로", "startDate": "2026-10-20", "endDate": "2026-10-20", "people": 2,
                  "transports": ["subway","bus","walk"], "mustVisit": ["경복궁"], "fixed": [{ "name": "세종문화회관", "time": "19:00" }], "style": "여유롭게" } }
```

| 필드 | 설명 |
|---|---|
| reply | AI 답 (없으면 "조건을 정리했어요. 확인해 주세요.") |
| conditions.region | 지역. "내 위치·여기·근처"라고 하면 `"내 위치"` |
| conditions.startDate / endDate | 없으면 오늘 / startDate |
| conditions.people | 없으면 1 |
| conditions.transports | 없으면 `["subway","bus","walk"]` |
| conditions.mustVisit | 꼭 갈 곳 이름 |
| conditions.fixed | 시간이 정해진 일정 `[{name, time}]` |
| conditions.style | 여행 스타일 한 줄 |

`title·region·mustVisit·fixed·style`은 Gemini가 말하지 않으면 키가 없을 수 있어요.

**오류** 400 "메시지를 1~300자로 입력해 주세요." · Gemini 공통 오류

### `POST /api/plans/capture`
예약 확인 화면 캡처를 읽어 고정 일정 후보로 돌려줘요 (Gemini가 사진을 읽음, OCR). **저장하지 않아요** — 화면에서 사용자가 확인·수정한 뒤 고정 일정으로 넣어요. · 로그인 필요 · 화면: plan/ReserveCaptureView

**요청** `multipart/form-data`, 파일 칸 이름 `image` (PNG·JPG·WEBP, 5MB까지)

**응답**
```json
{ "name": "잔나비 콘서트", "date": "2026-10-24", "time": "18:00", "placeId": null, "address": "서울 송파구 올림픽로 25", "lat": null, "lng": null }
```

| 필드 | 설명 |
|---|---|
| name | 장소·공연 이름 |
| date, time | `yyyy-MM-dd`, `HH:mm`. 형식이 틀리거나 안 보이면 `null` |
| placeId, lat, lng | 이름으로 우리 DB(없으면 TourAPI·카카오)에서 찾은 장소. 못 찾으면 `null` |
| address | 사진의 주소, 없으면 찾은 장소 주소 |

> 예약 번호·전화번호·이름·결제 정보는 Gemini에게 읽지 말라고 하고 받지도 않아요. 사진은 서버에 남기지 않아요.

**오류** 400 "사진을 올려 주세요." · 400 "사진은 5MB까지예요." · 400 "PNG·JPG 사진만 올릴 수 있어요." · 422 "예약 정보를 읽지 못했어요. 장소 이름과 시간이 보이게 다시 찍어 주세요." · Gemini 공통 오류

### `POST /api/plans/generate`
조건으로 일정 초안을 만들어요. 저장은 안 해요. · 로그인 필요 · 화면: plan/GeneratingView → trip/PlanCheckView

**요청**

| 필드 | 타입 | 필수 | 설명 |
|---|---|---|---|
| conditions | object | 필수 | 읽는 키: `title`(기본 "새 여행"), `startDate`(기본 오늘), `endDate`(기본 startDate), `people`(기본 1), `transports`, `region`, `mustVisit`, `fixed`(AI 실패 때만) |
| messages | string[] | | 사용자가 한 말 목록 (Gemini 참고용) |
| lat, lng | number | | 지금 GPS. region이 "내 위치·여기·근처"이거나 비어 있으면 그 주변 2km에서 후보를 고름 |

**응답**

| 필드 | 설명 |
|---|---|
| title | 여행 이름 (Gemini가 다시 지으면 그 이름, 60자까지) |
| startDate, endDate, people | 조건 값 |
| transports | 조건의 수단. **조건에 없으면 `[]`** (그대로 저장하면 400) |
| stops | 초안 `Stop[]` (`day → time` 순). `id`·`tripId`·`mode`·`memo`·`arrivedAt`·`departedAt`는 `null`, `state`는 `"todo"`, `stay` 10~600, DB 밖 장소면 `placeId`가 `null` |
| notes | 안내 문구 `[{text, url}]` (없으면 `[]`). 예: "{이름}의 위치를 찾지 못해 뺐어요", "AI 연결이 안 돼서 꼭 갈 곳만 순서대로 담았어요 (...)". `url`은 네이버 지도 검색 링크, AI 실패 안내는 `null` |

**오류** 400 "여행 조건이 없어요." · 422 "내 위치를 받지 못했어요. 브라우저 위치 권한을 허용하거나 지역 이름을 말해 주세요." · 422 "일정에 넣을 장소를 찾지 못했어요. 가고 싶은 곳을 조금 더 자세히 말해 주세요."

> Gemini가 실패해도 오류를 내지 않고, `notes`에 적은 뒤 꼭 갈 곳만으로 초안을 만들어요.

---

## ④ 여행 중 판별 (live)

`/live/` API 7개는 모두 경로 `{id}`가 여행 번호이고, 본문에 **지금 위치 `lat`, `lng`(number)가 필수**예요.

- "오늘" = 시작 날짜부터 센 오늘의 day
- "남은 일정" = 오늘 칸 중 `state = todo`이면서 지금 있는 곳이 아닌 칸

**공통 오류 (검사 순서대로)**
1. 400 "내 위치를 먼저 찾아 주세요." (lat·lng가 숫자가 아니거나 없음)
2. 404 "여행을 찾을 수 없어요."
3. 400 "진행 중인 여행이 아니에요. 여행 화면에서 '여행 시작'을 눌러 주세요." (status ≠ ongoing)

### `POST /api/trips/{id}/live/track`
GPS 한 점을 저장하고 도착·출발을 기록해요. · 로그인 필요 · 화면: live/LiveView

**요청** `lat`, `lng`, `accuracy`(선택, GPS 정확도 m)

**동작** "지금 있는 칸"(도착했고 아직 출발 안 함)은 한 번에 하나만 둬요.
- 오늘 `todo` 칸 중 제일 가까운 칸이 지금 칸이 아니고, 지금 점·바로 앞 점 둘 다 150m 안이면 그 칸 `arrivedAt` 기록. 지금 칸이 있었으면 그 칸은 `departedAt` 기록 + `state = done`
- 그게 아니고 지금 칸에서 지금 점·바로 앞 점 둘 다 200m 밖이면 지금 칸 `departedAt` 기록 + `state = done`
- 여행의 첫 점이거나 `accuracy`가 100m보다 크면 저장만 하고 판정하지 않아요

**응답** `{ "ok": true }` · **오류** 공통 오류

### `POST /api/trips/{id}/live/status`
지금 상황을 다시 계산해요 (알림·여유·날씨). · 로그인 필요 · 화면: live/LiveView, live/LiveDelayView

**요청** `lat`, `lng`

**응답**

| 필드 | 타입 | 설명 |
|---|---|---|
| now | string | 지금 시각 |
| currentStopId | number (null 가능) | 지금 있는 일정 칸 id (도착했고 아직 출발 안 함, 또는 150m 안) |
| alert | object (null 가능) | 화면 맨 위 알림 1개 `{key, level, title, text, repeat}` |
| alert.key | string | 중복 알림 방지 키. 앞부분: `overdue-` 더 머묾 / `closed-` 영업 끝·휴무 (danger), 열려 있지만 닫을 때까지 남은 분이 머무는 시간보다 짧음 (warn) / `late-` 고정 일정 지각 / `leave-` 출발 알림 / `missed-` 지난 일정 / `tight-` 고정 여유 10분 이하 / `next-` 다음 장소 지각 / `rain-` 다음 경로 비 소식 |
| alert.level | string | `danger` / `warn` |
| alert.repeat | boolean (없을 수 있음) | `leave-` 알림에만 `true` |
| leave | object (null 가능) | 출발 시각 ±10분 안일 때만 `{stopId, name, leaveAt, minutesLeft, to, atStop}`. `stopId`는 지금 있는 칸 또는 다음 칸 id. `minutesLeft`는 −10~10(음수=지남), `to`는 다음 장소 이름(null 가능), `atStop`이 `true`면 지금 있는 곳에서 출발 |
| fixed | object (null 가능) | 남은 일정 중 첫 고정 일정 `{id, name, time, lat, lng, eta, slackMin}` |
| hint | string (null 가능) | 고정 일정에 늦을 때 조언 한 줄. 예: "OO은(는) 그대로 갈 수 있어요..." |
| replan | boolean | 다시 짜는 게 좋은가 (지각·영업 끝·지난 일정·더 머묾·다음 장소 10분 넘게 늦음) |
| area | object (null 가능) | '지금 갈 수 있는 구역' 기준 `{slackMin, basis, basisTime, lat, lng}`. `basis`·`basisTime`은 기준 일정(고정 일정, 없으면 다음 일정), `lat`·`lng`는 보낸 내 위치. 남은 일정이 없으면 `null` |
| weather | object (null 가능) | **다음 칸 위치**(남은 칸이 없으면 내 위치) 예보에서 오늘 지금 이후 비·눈(또는 강수확률 60% 이상) 첫 시간 `{time, pop, snow, outdoor}`. `outdoor`는 지금 칸·남은 칸 중 야외이고 비 시작 뒤에도 머무는(떠날 시각 > 비 시작) 일정 이름. 예보가 없거나 실패하면 `null` |
| stops[] | object[] | **오늘의 모든 칸** (done·skipped 포함): `id, placeId, name, category, lat, lng, time, stay, fixed` + 아래 |
| stops[].eta | string | 남은 일정이면 예상 도착, 아니면 계획 `time` |
| stops[].reach | string | `done`(done·skipped) / `here`(지금 있는 곳) / `closed` / `missed`(계획 시간 지남) / `fixed` / `ok` / `tight`(시간 줄이면 OK) / `no`(고정 일정에 못 돌아옴) |
| stops[].closeAt, closedReason, closingSoon | | 영업 정보 일부 |
| stops[].importance | string | 중요도 `must`/`maybe`/`drop` (11/03 추가) |
| stops[].moveMin, moveMode, moveMeters | number, string, number | 남은 칸만: 앞(지금 위치·앞 칸)에서 오는 이동 분·수단(`walk`/`transit`/`car`)·거리(m). 아니면 `null`. 여행 중 화면 카드 사이 이동 칩 (11/03 추가) |
| next | object (null 가능) | 내 위치 → 다음 남은 일정 `{id, minutes, coords, source}` |

```json
{ "now": "14:20", "currentStopId": 102,
  "alert": { "key": "leave-102-5", "level": "warn", "title": "5분 뒤 출발해야 해요", "text": "카페OO 출발 14:25 → 청계천", "repeat": true },
  "leave": { "stopId": 102, "name": "카페OO", "leaveAt": "14:25", "minutesLeft": 5, "to": "청계천", "atStop": true },
  "fixed": { "id": 105, "name": "세종문화회관", "time": "19:00", "eta": "18:40", "slackMin": 20, "lat": 37.5725, "lng": 126.9759 },
  "hint": null, "replan": false,
  "area": { "slackMin": 20, "basis": "세종문화회관", "basisTime": "19:00", "lat": 37.57, "lng": 126.98 },
  "weather": { "time": "16:00", "pop": 70, "snow": false, "outdoor": ["청계천"] },
  "stops": [ { "id": 102, "placeId": null, "name": "카페OO", "category": "카페", "lat": 37.57, "lng": 126.98, "time": "13:30", "stay": 55,
               "fixed": false, "eta": "13:30", "reach": "here", "closeAt": null, "closedReason": null, "closingSoon": false } ],
  "next": { "id": 103, "minutes": 12, "coords": [[37.57,126.98],[37.569,126.978]], "source": "estimate" } }
```

**`alert` 고르는 순서** — 여러 개가 맞아도 위에서부터 먼저 맞는 하나만 보내요.

| 순서 | key | 언제 | level |
|---|---|---|---|
| 1 | `overdue-{칸}-{더 머문 분÷5}` | 지금 칸에서 떠날 시각(도착 + 머묾)이 지남 | danger |
| 2 | `closed-{칸}` / `closed-{칸}-short` | 남은 칸이 도착할 때 닫힘·휴무 / 열려 있지만 닫을 때까지 남은 분 < 머무는 시간 | danger / warn |
| 3 | `late-{고정 칸}-{늦는 분÷10}` | 고정 일정 여유(`fixed.slackMin`) < 0 | danger |
| 4 | `leave-{칸}-{남은 분}` | `leave`가 있음 (출발 ±10분). `repeat: true` | 남은 분 > 0이면 warn, 아니면 danger |
| 5 | `missed-{칸}` | 계획 시각 + 머묾이 지났는데 도착 기록이 없는 칸 (고정 일정 제외) | warn |
| 6 | `tight-{고정 칸}` | 고정 일정 여유 ≤ 10분 | warn |
| 7 | `next-{칸}-{늦는 분÷10}` | 다음 칸에 10분 넘게 늦게 도착 | warn |
| 8 | `rain-{비 시작 HHmm}` | 다음 경로에 비·눈 소식이 있고 그때 야외에 있을 칸이 있음 (`weather.outdoor`). 예보가 바뀌어 비 시작 시각이 달라지면 다시 울림 | warn |

key 끝의 숫자가 바뀔 때만 화면이 같은 알림을 다시 띄워요.

**오류** 공통 오류

### `POST /api/trips/{id}/live/assist`
여행 중 채팅이에요. Gemini가 사용자 말을 분류해요. · 로그인 필요 · 화면: live/LiveView (지도 아래 "AI에게 묻기" → `components/live/AskPanel.vue`)

**요청** `message`(1~300자), `lat`, `lng`

**응답** `kind`에 따라 세 가지 중 하나

| kind | 같이 오는 필드 | 뜻 |
|---|---|---|
| `"ask-radius"` | `category`(기본 "카페"), `tags`(원하는 분위기, 없으면 `[]`) | 추천 요청 → 화면이 반경을 묻고 `/live/recommend`에 `category`·`tags`를 그대로 넘겨 호출 |
| `"alternatives"` | `data` | 일정 바꾸기 요청 → `data`는 `/live/alternatives` 응답 전체 (reason `"request"`) |
| `"text"` | `text` | 그 밖의 질문에 대한 답 |

**오류** 400 "메시지를 1~300자로 입력해 주세요." · 공통 오류 · (alternatives로 가면) 400 "오늘 남은 일정이 없어요."

> 분류는 낱말 규칙이 먼저예요. 한 종류만 걸리면 Gemini를 부르지 않고, 애매할 때만 Gemini(JSON, 정해진 값 중에서만)를 불러요. Gemini가 실패하면 질문(text)으로 보고 오류는 내지 않아요.

> `text` 답: 자주 하는 질문(다음 일정·여유·날씨)은 정해진 문장으로, 그 밖은 Gemini가 **서버가 계산한 오늘 상황(live/status)만 근거로** 답해요. 답에 근거에 없는 시각·분이 있으면 버리고 기본 문장으로 바꿔요. 좌표는 Gemini에 보내지 않아요.

> 일정 바꾸기는 대안 A·B·C 화면(`/live/alt`)으로 가요. 비 올 때 실내로 바꾸는 대안은 만들지 않아요 (11/02 결정: 비는 `rain-` 알림만).

### `POST /api/trips/{id}/live/recommend`
반경 안에서 평점 상위 3곳을 추천해요. · 로그인 필요 · 화면: live/LiveView (AskPanel)

**요청**

| 필드 | 타입 | 필수 | 설명 |
|---|---|---|---|
| lat, lng | number | 필수 | 지금 위치 |
| radius | number | 필수 | 반경(m) 100~3000 |
| category | string | | 장소 종류. 식당·맛집·밥 등이면 음식점 전체(카페·디저트 제외), 술집이면 주점·호프·이자카야 등, 그 밖은 분류에 그 말이 들어간 곳 |
| tags | string[] | | 원하는 분위기 `혼밥` / `조용` / `분위기` / `데이트` / `가성비` / `웨이팅없음` (assist의 `tags`를 그대로). 있는 곳에 점수를 더함 |

**응답**
```json
{ "places": [ { "id": "…", "name": "카페OO", "category": "카페", "address": "…", "lat": 37.57, "lng": 126.98, "rating": 4.7, "reviewCount": 320,
                "naverUrl": "…", "distance": 180, "open": true, "closeAt": "22:00", "minutesLeft": 455, "closingSoon": false,
                "reasons": ["혼밥하기 좋아요 · 리뷰 15개 중 5개"] } ],
  "closedSkipped": 1 }
```
`places`는 최대 3개, 점수 순 (리뷰 수로 보정한 평점 + 원하는 `tags`가 리뷰에 있으면 더함). 각 항목은 장소 카드(`distance` 항상 있음) + 영업 정보 + `reasons`(추천 이유 칩, 리뷰 근거. 없으면 `[]`). 평점 없는 곳·도착 때 닫힌 곳은 빼고, 도착 때 닫혀서 뺀 수는 `closedSkipped`.

> 리뷰 태그 인정: 그 장소 리뷰 중 2개 이상이면서 (3개 이상 또는 20% 이상). 원하는 `tags`가 없으면 그 장소에 있는 태그를 2개까지 보여 줘요.

**오류** 400 "radius 값이 필요해요." · 공통 오류 · 400 "반경은 100m~3km예요."

### `POST /api/trips/{id}/live/insert`
추천 장소를 바로 다음 일정으로 넣어요. · 로그인 필요 · 화면: live/LiveView (AskPanel의 "일정에 넣기")

**요청** `placeId`(장소 카드의 `id`), `lat`, `lng`

**동작** 새 칸(카페면 40분, 아니면 60분)을 넣고 남은 일정 시각을 다시 계산해 저장. 고정 일정에 늦으면 그 앞 칸 머무는 시간을 최대 절반까지 줄임.

**응답** `{ "trip": Trip, "message": "카페OO을(를) 다음 일정으로 넣었어요. 세종문화회관에는 12분 여유가 있어요." }`

**오류** 공통 오류 · 404 "장소를 찾을 수 없어요."

### `POST /api/trips/{id}/live/alternatives`
대안 A·B·C를 만들어요. · 로그인 필요 · 화면: live/AlternativesView, live/AltTrimView, live/AltDropView, live/AltSwapView

**요청**

| 필드 | 타입 | 필수 | 설명 |
|---|---|---|---|
| lat, lng | number | 필수 | 지금 위치 |
| reason | string | | 이유, 기본 `"delay"` (Gemini에게 그대로 전달) |
| request | string | | 사용자 요청 (채팅에서 넘어올 때). 비 올 때 실내 대안은 만들지 않음 (11/02) |

**응답**

| 필드 | 타입 | 설명 |
|---|---|---|
| title | string | 화면 제목 (기본 "{고정 일정}에 늦지 않는 대안" 또는 "남은 일정 대안") |
| subtitle | string | 한 줄 설명 (기본 "지금 위치·시각에서 다시 계산했어요") |
| weather | string (null 가능) | Gemini가 날씨를 봤을 때 한 줄 |
| source | string | `rule` / `gemini` / `gemini+rule` |
| alternatives[] | object[] | 대안 1~3개 (규칙: A 시간 단축 / B 한 곳 빼기 / C 장소 변경) |
| ┗ key, short, title, reason | string | `"A"`·`"B"`·`"C"`(순서대로 다시 붙임), 탭 이름, 대안 이름, 이유 한 문장 |
| ┗ recommended | boolean | 추천 대안 (정확히 하나만 `true`) |
| ┗ finalArrive, slackMin | | 고정 일정(없으면 마지막 일정) 예상 도착과 여유 |
| ┗ closedCount, changes | number | 도착 때 닫힌 곳 수, 바뀌는 칸 수 |
| ┗ coords | number[][] | 전체 경로선 |
| ┗ stops[] | object[] | `id`(새 장소면 `null`), `placeId`, `name, category, lat, lng, stay, fixed, importance`, `eta`(고정 일정은 계획 시각), `moveMin`(앞에서 오는 이동 분), `change`("60분 → 30분" 또는 "새로 추가", 안 바뀌면 `null`), `closedReason`, `closeAt`, `closingSoon`, `naverUrl`(DB에 없는 새 장소일 때만) |

```json
{ "title": "세종문화회관에 늦지 않는 대안", "subtitle": "지금 위치·시각에서 다시 계산했어요", "weather": null, "source": "rule",
  "alternatives": [ { "key": "A", "short": "시간 단축", "title": "머무는 시간 줄이기", "reason": "가는 곳은 그대로, 머무는 시간만 줄여요.",
      "recommended": true, "finalArrive": "18:55", "slackMin": 5, "closedCount": 0, "coords": [[37.57,126.98],[37.5725,126.9759]], "changes": 1,
      "stops": [ { "id": 103, "placeId": "…", "name": "청계천", "category": "관광지", "lat": 37.569, "lng": 126.978, "stay": 30, "fixed": false,
                   "eta": "14:37", "moveMin": 12, "change": "60분 → 30분", "closedReason": null, "closeAt": null, "closingSoon": false, "naverUrl": null } ] } ] }
```

**오류** 공통 오류 · 400 "오늘 남은 일정이 없어요."

> Gemini가 실패하면 규칙 대안만 보내고 오류는 내지 않아요.

### `POST /api/trips/{id}/live/apply`
고른 대안을 일정에 반영해요. · 로그인 필요 · 화면: live/AltTrimView, live/AltDropView, live/AltSwapView → live/AppliedView

**요청** `/live/alternatives`의 **대안 하나를 그대로** + `lat`, `lng` (화면은 `{ ...alternative, lat, lng }`로 보냄)

| 필드 | 필수 | 설명 |
|---|---|---|
| key | | 응답에 그대로 돌려줌 |
| stops | 필수 (1개 이상) | `id`가 남은 일정 칸이면 그 칸을 `eta`·`stay`로 수정 (고정 일정 시각은 안 바꿈, `stay` 기본 60). `id`가 없으면 `lat, lng, placeId, name, category`로 새 칸 추가 (lat·lng 없으면 건너뜀) |
| lat, lng | 필수 | 지금 위치 |

**동작** 대안에 없는 남은 일정 칸은 `state = skipped`로 바뀜.

**응답**
```json
{ "key": "A", "message": "세종문화회관에 늦지 않게 일정을 바꿨어요!",
  "nextAlert": "청계천에서 출발할 시각(15:07) 10분 전부터 1분마다 알려드릴게요. 늦게 도착하면 도착한 시각 기준으로 다시 맞춰요.",
  "stops": [ { "id": 103, "time": "14:37", "name": "청계천", "category": "관광지", "stay": 30, "tag": "changed", "note": "60분 → 30분" } ] }
```
`message`는 "일정을 바꿨어요!" 또는 "{고정 일정}에 늦지 않게 일정을 바꿨어요!". `nextAlert`는 null 가능. `stops`는 오늘 칸(skipped 제외)이고 `tag`는 `done`(다녀옴·지금 있는 곳) / `kept` / `changed`(머무는 시간이 바뀜) / `added`.

**오류** 공통 오류 · 400 "적용할 대안이 없어요."

### `POST /api/trips/{id}/live/preview`
사용자가 고친 남은 일정을 계산만 해요 (저장 안 함). 시간 줄이기 슬라이더·한 곳 빼기·장소 바꾸기 화면이 고를 때마다 불러요. · 로그인 필요 · 화면: live/AlternativesView, live/AltTrimView, live/AltDropView, live/AltSwapView · 11/03 추가

**요청**

| 필드 | 필수 | 설명 |
|---|---|---|
| lat, lng | 필수 | 지금 위치 |
| stops | 필수 (1개 이상) | 갈 순서대로. `id`가 남은 일정 칸이면 그 칸을 `stay`로 계산(고정 일정 머묾은 안 바꿈, `stay`가 없으면 원래 값). `id`가 없으면 `lat, lng, placeId, name, category, stay`로 새 장소(좌표 없으면 건너뜀). **목록에 없는 남은 칸은 뺀 것으로 봄** |

**응답** `/live/alternatives`의 대안 하나와 같은 모양 (`key`는 `null`, `short` "직접 고르기"). `stops[].change`에 "60분 → 30분"·"새로 추가", `changes`는 뺀 칸까지 센 수

> 이동 시간은 `/live/status`와 같은 방식(앞 3구간은 길찾기 API, 나머지는 거리로 추정)이라 적용 뒤 여행 중 화면 숫자와 맞아요. `/live/alternatives`·`/live/swap-candidates`는 후보를 여러 개 비교해서 전부 거리로 추정해요.

**오류** 공통 오류 · 400 "계산할 일정이 없어요."

### `GET /api/trips/{id}/live/swap-candidates`
남은 칸 하나를 바꿀 장소 후보예요. · 로그인 필요 · 화면: live/AltSwapView · 11/03 추가

**요청** 쿼리 `stopId`(필수, 바꿀 남은 칸), `lat`, `lng`(필수, 지금 위치)

**응답** 배열, 최대 10개, 바꿀 칸에서 가까운 순. 바꿀 칸 1.5km 안 · 그 칸 시각에 영업 중(영업시간 모르면 포함) · 평점 있음 · 남은 일정에 없는 곳

| 필드 | 타입 | 설명 |
|---|---|---|
| id, name, category, address, lat, lng, rating, reviewCount, naverUrl | | 장소 카드와 같음 |
| distance | number | 바꿀 칸에서 거리(m) |
| moveMin, eta | number, string | 앞 장소에서 이 장소까지 이동 분, 예상 도착 |
| finalArrive, slackMin | string, number | 이 장소로 바꾸면 고정 일정(없으면 마지막 칸) 예상 도착과 여유 |

**오류** 공통 오류 · 404 "남은 일정에서 찾을 수 없어요." · 400 "고정 일정은 바꿀 수 없어요."

---

## ⑤ 여행 후·지도 부가 (after)

### `GET /api/route`
길 안내: 지금 위치에서 도착지까지 경로선과 단계별 안내. · 로그인 필요 · 화면: after/RouteGuideView · 11/03 추가

**요청** 쿼리 `fromLat, fromLng, toLat, toLng`(필수), `mode`(`walk` 기본 / `bus` / `subway` / `car`)

**응답**

| 필드 | 타입 | 설명 |
|---|---|---|
| mode | string | 요청한 수단 |
| available | boolean | 이 수단으로 길을 찾았나. `false`면 `message`에 이유 (키 없음, 대중교통 상품 미신청, 버스로만 가는 길 없음 등) |
| message | string (null 가능) | 못 찾은 이유 |
| source | string | `tmap` / `odsay` |
| minutes, meters | number | 걸리는 시간·거리 |
| fare | number (null 가능) | 택시는 예상 택시비, 대중교통은 요금 |
| coords | number[][] | 경로선 `[[위도, 경도], ...]` |
| steps[] | object[] | `lat, lng`(없을 수 있음), `kind`(`start`/`straight`/`left`/`right`/`crosswalk`/`stairs`/`walk`/`bus`/`subway`/`getoff`/`car`/`end`), `text`("좌회전 후 50m 이동", "버스 470 타기"), `detail`("광화문 → 서울역 · 4정거장 · 8분 · ○○ 방면", 없으면 `null`), `key`(놓치면 안 되는 단계) |

> 도보 TMAP 보행자, 택시 TMAP 자동차(예상 택시비), 버스·지하철은 TMAP 대중교통(상품 신청 필요) → 실패하면 ODsay. 버스는 버스만 타는 경로, 지하철은 지하철이 들어간 경로를 골라요. 같은 요청은 1분 동안 다시 부르지 않아요.

**오류** 400 "출발·도착 좌표가 필요해요." · 400 "이동 수단은 walk·bus·subway·car 중 하나예요."

### `GET /api/trips/{id}/retro`
회고: 계획과 실제를 비교하고 GPS 선을 줘요. · 로그인 필요 · 화면: after/RetroView

**요청** 경로 `id`

**응답**

| 필드 | 타입 | 설명 |
|---|---|---|
| id, title, startDate, endDate | | 여행 정보 |
| visited | number | 실제 도착 기록(`arrivedAt`)이 있는 일정 칸 수 |
| stops[] | object[] | 모든 일정 칸 (skipped 포함): `id, day, name, category, lat, lng, time, stay, state` + 아래 |
| stops[].actualArrive | string (null 가능) | 실제 도착 시각 `HH:mm`. 안 갔으면 `null` |
| stops[].actualStay | number (null 가능) | 실제 머문 분. 출발 기록이 없으면 `null` |
| track[] | object[] | GPS 점 (기록 순) `{lat, lng, day, time}`. `day` = 기록 날짜 − 시작 날짜 + 1 |

```json
{ "id": 12, "title": "서울 하루", "startDate": "2026-10-20", "endDate": "2026-10-20", "visited": 1,
  "stops": [ { "id": 101, "day": 1, "name": "경복궁", "category": "관광지", "lat": 37.5796, "lng": 126.977,
               "time": "10:00", "stay": 90, "state": "done", "actualArrive": "10:03", "actualStay": 112 } ],
  "track": [ { "lat": 37.5794, "lng": 126.9768, "day": 1, "time": "10:03" } ] }
```

**오류** 404 "여행을 찾을 수 없어요."

---

## 서로 맞춰야 하는 API

내 API를 다른 번호가 쓰고 있으면, 바꾸기 전에 그 번호 담당자에게 꼭 확인해요.

| API (주인) | 쓰는 곳 | 맞춰야 하는 이유 |
|---|---|---|
| `GET /api/auth/me` (①) | `router/index.js` → 모든 화면 | 응답 `{username, nickname, admin}`이 바뀌면 로그인 판정이 전부 깨짐 |
| `GET /api/config/public` (①) | ②③④⑤ 지도 화면 전부 | `kakaoJsKey` 이름·빈 객체 규칙이 바뀌면 모든 지도가 안 뜸 |
| Trip·Stop·장소 카드 모양 (②) | ①~⑤ 거의 모든 API | 필드 이름·타입이 바뀌면 쓰는 곳이 다 깨짐 |
| `POST /api/trips` (②) | ③ `/plans/generate` 결과를 그대로 저장 | 초안 Stop 모양과 ②의 Stop 검사 규칙(time HH:mm, stay 0~720, transports 1개 이상)이 맞아야 저장됨 |
| `GET /api/trips/{id}` (②) | ④ LiveView, ⑤ RetroView | Trip·Stop 필드(`status`, `stops[].id/placeId/fixed/time`)를 직접 씀 |
| `POST·PUT·DELETE /api/trips/{id}/stops...` (②) | ④ PinEditView | 수정 때 `mode`·`memo`를 안 보내면 지워지는 규칙, 응답이 Trip 전체라는 점을 같이 알아야 함 |
| `POST /api/trips/{id}/finish` (②) | ④ LiveView | 여행 끝내기 버튼이 ④ 화면에 있음 |
| `POST /api/trips/{id}/chats` (②) | ③ PlanChatView, ④ LiveView | `{role, text, at}` 형식으로 저장. `at` 형식이 다르면 저장 시각이 바뀜 |
| `GET /api/places/search`·`/nearby`·`/{id}` (②) | ③ PlaceSearchView | 장소 카드 모양을 같이 씀. `distance`는 lat·lng를 보냈을 때만 있음 |
| `POST /api/plans/generate` (③) | ② PlanCheckView → `POST /api/trips` | 초안 Stop·`transports`가 ②의 저장 규칙과 맞아야 함 (조건에 transports가 없으면 `[]`로 와서 저장 400) |
| `POST /api/plans/check` (②) | ③ 일정 생성 후 점검 | 응답 `legs[].mode`는 `walk/transit/car`라 Stop.mode(`taxi`)와 다름 |
| `POST /api/trips/{id}/live/recommend` (④) | 응답 항목이 ②의 장소 카드 + 영업 정보 | ②가 장소 카드·영업 정보 모양을 바꾸면 ④ 화면이 바뀜 |
| `POST /api/trips/{id}/live/insert`·`/status`·`/alternatives`·`/apply` (④) | 안에서 ②의 Trip·Stop을 읽고 고침 | Stop `state`·`fixed`·`arrivedAt` 뜻이 바뀌면 알림·대안이 달라짐 |

---

## 정해야 할 것

예전 코드에서 확인된 문제예요. 고치려면 API 규칙대로 담당자가 확인한 뒤 이 파일도 같이 고쳐요.

| 담당 | 내용 |
|---|---|
| 공통 | 경로의 `{id}`에 숫자가 아닌 값을 넣으면 500이 남. 400으로 바꿀지 |
| ② | `PUT /stops/{stopId}`에 `state`로 `todo/done/skipped` 밖의 값을 보내면 500이 남. 400 검사를 넣을지 |
| ② | `POST /api/plans/check`에 `startDate`나 `stay`가 없으면 500이 남. 400으로 바꿀지 |
| ③ | `POST /api/plans/generate`에 날짜 형식이 틀리면 500이 남. 400으로 바꿀지 |
| ③ | `POST /api/plans/chat`의 `conditions` 값 타입을 서버가 다시 검사하지 않음 |
