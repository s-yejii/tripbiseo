# 여행어때 (트립비서)

여행 중 일정이 틀어지면 먼저 알려 주고, 지금 위치에서 갈 수 있는 대안을 보여 주는 앱입니다.
화면은 Vue 3로 만든 스마트폰 모양 웹앱이고, 서버는 Spring Boot + MyBatis + PostgreSQL입니다.

```
tripbiseo/
├─ db/        PostgreSQL 스크립트 (번호 순서대로 실행)
├─ backend/   Spring Boot (Java 17) — controller / service / mapper / dto / config / common
└─ frontend/  Vue 3 + Vite — views(화면) / components(지도·시트·모달) / api(서버 호출) / stores(로그인·초안)
```

## 처음 실행

1. **DB**: `tripbiseo` 데이터베이스에 `db/01` ~ `db/06`을 순서대로 넣습니다. 01~05는 이미 넣었다면 06만 넣으면 됩니다.
   pgAdmin에서 DB를 우클릭하고 PSQL Tool을 연 뒤, 아래처럼 입력합니다.
   ```
   \encoding UTF8
   \i 'C:/경로/tripbiseo/db/06_tripbiseo.sql'
   ```
2. **백엔드**: 이클립스에서 `backend` 폴더를 Maven 프로젝트로 가져온 뒤 `TripbiseoApplication`을 실행합니다(포트 8080).
   DB 비밀번호가 postgres가 아니면 `application.properties`를 고칩니다.
3. **화면**: `frontend` 폴더에서 아래를 실행한 뒤 http://localhost:5173 을 엽니다.
   ```
   npm install
   npm run dev
   ```

## API 키

키는 각자 컴퓨터의 **환경변수**로 넣습니다. 코드나 `application.properties`에 키 값을 직접 적어서 커밋하지 않습니다. 환경변수 이름은 아래 표와 같습니다.

| 키 | 어디서 받나 | 없으면 |
|---|---|---|
| `GEMINI_API_KEY` | Google AI Studio | AI 일정 만들기와 채팅 이해가 안 되고, 대안은 규칙으로만 만듭니다 |
| `KAKAO_JS_KEY` | 기존 키를 06 SQL에 넣어 둠 (카카오 콘솔 → 플랫폼 Web에 `http://localhost:5173` 등록) | 지도가 오픈스트리트맵으로 바뀝니다 |
| `KAKAO_REST_API_KEY` | 같은 카카오 앱의 REST 키 | DB에 없는 장소의 위치를 못 찾고, 네이버 지도 링크만 줍니다 |
| `TMAP_APP_KEY` | SK open API (대중교통은 상품 신청이 필요) | ODsay로, 그것도 없으면 거리로 이동시간을 추정합니다 |
| `ODSAY_API_KEY` | ODsay LAB (서버 IP 등록) | TMAP이 실패했을 때 대신할 길찾기가 없습니다 |
| `KMA_SERVICE_KEY` | 공공데이터포털 '기상청_단기예보 조회서비스' | 제미나이가 구글 검색으로 날씨를 직접 찾습니다(유료 등급 필요) |

## 알아 둘 것

- 웹 위치 찾기(내 위치)는 `localhost`나 https 주소에서만 됩니다. PC에서는 와이파이 기준의 대략적인 위치가 잡힙니다.
- 앱이 꺼져 있을 때 오는 푸시 알림은 아직 없습니다. 여행 중 화면이 열려 있는 동안만 알림이 옵니다.
- CSRF 보호는 팀 설정대로 꺼 두었습니다. 배포하기 전에 켜는 것을 검토해야 합니다.
- 네이버 장소·리뷰 데이터는 서울 프로토타입용입니다(약관상 자동 수집은 금지). 최종 구조에는 공식 API를 적어야 합니다.
