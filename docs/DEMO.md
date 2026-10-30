# 시연 시나리오 (DEMO) — 초안

최종 시연에서 보여 줄 흐름이에요. **여기 나오는 API가 진짜로 돌아가야 하는 것**이고, 나머지 화면은 가짜 데이터여도 시연은 돼요. 그래서 이 문서가 개발 우선순위의 기준이 돼요.

> **초안이에요.** 프로토타입 흐름(잔나비 공연 데이트)을 그대로 옮겼어요. 시연할 서울 3개 구가 정해지면 장소를 바꾸고, 팀이 확정하면 이 줄을 지워요.

## 시연 한 줄 요약

"공연 보러 가는 날, 오후 일정이 밀려서 공연에 늦을 것 같아지자 앱이 먼저 알려 주고, 대안을 골라 적용하니 공연에 늦지 않게 일정이 다시 맞춰진다."

## 배경

| 항목 | 내용 |
|---|---|
| 날짜 | 토요일 하루 |
| 지역 | 천호·성내동 (강동구) → 잠실실내체육관 (송파구) |
| 고정 일정 | 잔나비 콘서트 18:30 입장 (예약, 늦으면 안 됨) |
| 일정 | ① 천호 쭈꾸미골목(점심) → ② 강풀만화거리 산책 → ③ 성내동 카페 → ④ 성내동 소품샵 → ⑤ 천호 로데오거리 → 🔒 잔나비 콘서트 |
| GPS | 시연용 가짜 GPS 재생 (④ 담당) |

## 흐름

| # | 장면 | 화면 | 진짜로 돌아가야 하는 API | 담당 |
|---|---|---|---|---|
| 1 | 로그인 | member/LoginView | `POST /api/auth/login` | ① |
| 2 | 새 여행 → 장소를 고르고 공연을 고정 일정으로 넣기 | plan/NewTripView, trip/PlanManualView | `GET /api/places/search` | ② |
| 3 | 일정 점검: 이동시간·영업시간·브레이크 타임 확인 | trip/PlanCheckView | `POST /api/plans/check` | ② |
| 4 | 저장 | trip/PlanCheckView | `POST /api/trips` | ② |
| 5 | 당일, 여행 시작 | trip/MainStartView | `POST /api/trips/{id}/start` | ② |
| 6 | 여행 중: 장소에 들어가면 자동 도착 기록 | live/LiveView | `POST /api/trips/{id}/live/track` | ④ |
| 7 | 카페에 오래 머묾 → "공연 입장에 14분 늦을 것 같아요" 알림 | live/LiveView → live/LiveDelayView | `POST /api/trips/{id}/live/status` | ④ |
| 8 | 대안 3개 보기 (시간 줄이기 / 한 곳 빼기 / 장소 바꾸기) | live/AlternativesView | `POST /api/trips/{id}/live/alternatives` | ④ |
| 9 | "소품샵 30분 줄이기" 적용 → 공연 16분 여유 | live/AltTrimView → live/AppliedView | `POST /api/trips/{id}/live/apply` | ④ (② 메서드 사용) |
| 10 | (여유) 결제 메모, 여행 후 회고 | after/PayMemoView, after/RetroView | 결제 API, `GET /api/trips/{id}/retro` | ⑤ |

1~9번이 ★ 기능이에요. 10번은 시간이 되면 붙여요.

## 시연에 필요한 데이터

- [ ] 시연 지역 장소가 `retrip_place`에 있는지 확인 (지금 데이터: 네이버 카페·음식점 11,496곳 + 종로 TourAPI 225곳. 천호·성내동·잠실이 들어 있는지는 아직 확인 안 함)
- [ ] 3개 구 확정 → 그 구 장소 수집 (10/29 결정)
- [ ] 공연장(고정 일정) 장소 데이터
- [ ] 시연용 GPS 경로 (장소 순서대로 찍힌 좌표 + 시간)
- [ ] 시연 계정, 시연 여행 데이터 (`db/90_test_data.sql`)

## 정해야 할 것

- 시연할 서울 3개 구 (프로토타입 흐름이면 강동구·송파구가 들어가요)
- 2번에서 일정을 직접 만들지, AI(③)로 만들지. AI로 하면 ③의 `plans/chat`·`plans/generate`도 진짜여야 해요
- 시연을 폰으로 할지, PC 화면으로 할지
