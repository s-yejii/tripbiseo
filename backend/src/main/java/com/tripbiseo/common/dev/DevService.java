package com.tripbiseo.common.dev;

/**
 * 개발자 모드 (③, 11/06): 발표 시연 계정만 쓰는 시연 시계.
 * 시연 계정은 환경변수 DEMO_ACCOUNTS(아이디를 쉼표로)로 정하고 코드·DB에는 적지 않습니다.
 */
public interface DevService {

    /** 이 회원이 시연 계정인지 + 지금 시연 시계 상태 */
    DevStatus getStatus(long memberId);

    /** 시연 시계를 그 날짜·시각으로 맞춤 (시연 계정만) */
    DevStatus setClock(long memberId, ClockRequest request);

    /** 시연 시계를 몇 분 빨리 감기 (시연 계정만) */
    DevStatus forwardClock(long memberId, int minutes);

    /** 시연 시계를 끄고 진짜 시각으로 */
    DevStatus resetClock(long memberId);

    /** 시연 비 예보 넣기: 오늘 rainFrom("13:00")부터 비 (시연 계정만) */
    DevStatus setRain(long memberId, String rainFrom);

    /** 시연 비 예보 빼기 (진짜 기상청 예보로) */
    DevStatus clearRain(long memberId);

    /** 요청마다 부름: 이 회원의 시연 비 시작 시각. 안 넣었으면 null */
    String getRainFrom(long memberId);

    /** 요청마다 부름: 이 회원의 시연 시계 차이(초). 안 맞췄으면 0 */
    long getOffsetSeconds(long memberId);
}
