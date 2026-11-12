package com.tripbiseo.plan.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import com.tripbiseo.common.dev.ScenarioSetup;
import com.tripbiseo.common.util.Geo;
import com.tripbiseo.plan.dto.DayWeather;
import com.tripbiseo.plan.dto.FixedVisit;
import com.tripbiseo.plan.dto.PlanConditions;
import com.tripbiseo.plan.dto.PlanGenerateResponse;
import com.tripbiseo.plan.dto.PlanNote;
import com.tripbiseo.trip.dto.Stop;

/**
 * 발표 시나리오 일정 (③, 11/08 주형: "시연 계정은 뭘 안 눌러도 우리 시나리오대로").
 * 시연 계정(DEMO_ACCOUNTS)이 AI 일정을 만들면 고른 조건과 상관없이 이 일정이 나와요. 날짜만 사용자가 고른 날.
 * 11/12 최종 촬영 시나리오(아카데미 동기 5명 서울 1박 2일)로 바꿈. 시나리오를 바꾸려면 아래 STOPS 표만 고치면 돼요.
 *   출발: 종로3가역 11:30 → 1일차: 남도분식 → 동백양과점 → 르메르디앙 명동 체크인 → 남산 → 19:00 산청숯불가든 을지로 회식(고정) → 숙소
 *   2일차: 숙소 11:00 체크아웃 → 12:30 스시도(고정) → 해산. 돌발: 늦잠 → 숙소에서 나갈 시각이 지나도 그대로 → 스시도 지각
 *   (예전 영등포 워크숍·KTX 시나리오는 docs/DEMO.md 11/06)
 */
@Component
public class DemoScenario {

    private static final String TITLE = "아카데미 동기 서울 1박 2일";
    private static final int DEFAULT_PEOPLE = 5;
    private static final int NIGHTS = 1;

    // 11/12 촬영 시나리오: 각자 집에서 와서 종로3가역 11:30 모임
    private static final String ORIGIN_NAME = "종로3가역";
    private static final String ORIGIN_ADDRESS = "서울 종로구 종로3가역";
    private static final double ORIGIN_LAT = 37.5704;
    private static final double ORIGIN_LNG = 126.9921;
    // 조금씩 어기기 (11/08 주형): 발표자가 [다음 장소로]만 눌러도 이 곳들에서는 계획보다 조금 더 머물다 떠남 → 뒤 일정 밀림·자동 조정 안내
    private static final Object[][] EXTRA_STAYS = {}; // 11/12 새 시나리오는 돌발이 늦잠(2일차 숙소)이라 조금씩 어기기는 안 씀
    private static final String DEMO_DATE = "2026-11-10"; // 시연 계정의 "오늘" = 발표일(화). 로그인·[시연 준비]하면 시계를 이 날 15:00으로 (11/09 주형)
    private static final String DINNER_NAME = "산청숯불가든 을지로";
    private static final String DINNER_TIME = "19:00"; // 1일차 회식 예약
    private static final String LUNCH_NAME = "스시도";
    private static final String LUNCH_TIME = "12:30"; // 2일차 점심 예약 (늦잠으로 지각하는 곳)
    private static final String STAY_NAME = "르메르디앙 서울 명동";
    private static final String STAY_ADDRESS = "서울 중구 명동8나길 38";
    private static final double STAY_LAT = 37.5616;   // DB에 숙소가 없어 같은 건물 식당 좌표 (11/12)
    private static final double STAY_LNG = 126.9827;
    private static final String STAY_CHECK_IN = "15:00";
    private static final String STAY_CHECK_OUT = "11:00"; // 늦잠 → 레이트 체크아웃 12:00 요청이 시나리오
    private static final int OVERSTAY_DAY = 2;          // 돌발: 2일차에
    private static final String OVERSTAY_NAME = STAY_NAME; // 11/12 돌발: 늦잠 → 숙소에서 나갈 시각이 지나도 그대로 → 스시도 지각 (앱은 이유를 모름)
    private static final int OVERSTAY_MIN = 0;          // 0 = 서울역 도착 목표보다 늦어지는 순간까지 머묾 (그때 시계 멈춤)
    private static final String START_TIME = "11:30"; // 시연 계정은 1일차 이 시각부터 (종로3가역 모임, 11/12)

    // 일차, 시각, 이름, 장소 번호(retrip_place.id, 없으면 null), 종류, 위도, 경도, 머무는 분, 고정, 메모
    // DEMO.md(11/06 팀 시나리오)대로 + 출발만 가마산로 242 15:00 (11/08 주형 확정)
    // 일차, 시각, 이름, 장소 번호, 종류, 위도, 경도, 머무는 분, 고정, 메모, 중요도(must면 줄이기·빼기 대상 아님)
    // 11/12 촬영 시나리오(아카데미 동기 5명 서울 1박 2일) 시간표 그대로 (주형: "촬영 시나리오에 맞춰서")
    //   1일차: 11:30 종로3가역 모임 → 남도분식 → 동백양과점 → 15:00 숙소 체크인 → 남산순환나들길 → 팔각정(17:30까지)
    //          → 17:30~19:00은 일부러 비워 둠 (대중교통으로 내려와 시간이 남아 AI로 카페 찾는 장면) → 19:00 회식 → 숙소
    //          (21:00 편의점은 DB에 편의점이 없어 안 넣음, 지도 "근처 찾기"로 보여 줄 장면)
    //   2일차: 11:00 체크아웃 예정 → 늦잠·레이트 체크아웃 12:00 → 12:30 스시도 지각 (B안은 여행 중 화면에서). 점심 뒤 해산
    //   르메르디앙 서울 명동은 DB에 없어 장소 번호 없이 같은 건물(라팔레트 파리, 명동8나길 38) 좌표를 씀
    //   이동(PlanTravelTime 어림): 역→남도분식 7분, 동백양과점→숙소 17분, 숙소→나들길 15분, 나들길→팔각정 7분, 팔각정→을지로 18분, 회식→숙소 15분, 숙소→스시도 65분
    private static final Object[][] STOPS = {
        { 1, "12:00", "남도분식 익선점", "naver-1129603411", "분식", 37.5731697, 126.9901014, 80, false, "점심 · 종로3가역에서 걸어서 7분", null },
        { 1, "13:30", "동백양과점 익선점", "naver-1387480043", "카페,디저트", 37.5734323, 126.9895996, 60, false, "같은 익선동 골목 · 걸어서 5분", null },
        { 1, "15:00", STAY_NAME, null, "숙소", STAY_LAT, STAY_LNG, 45, false, "체크인 15:00 · 짐 풀고 쉬기", null },
        { 1, "16:00", "남산순환나들길", "tour-2783557", "관광지", 37.5549112997, 126.9866844934, 50, false, "숙소에서 15분 · 걸어서 팔각정까지", null },
        { 1, "17:00", "남산 팔각정", "tour-1603336", "관광지", 37.5516394747, 126.9876206116, 30, false, "내려올 때는 걸어서 내려올 계획", "must" },
        { 1, "19:00", "산청숯불가든 을지로", "naver-1850187827", "육류,고기요리", 37.5658389, 126.9906186, 120, true, "회식 예약 5명", null },
        { 1, "21:30", STAY_NAME, null, "숙소", STAY_LAT, STAY_LNG, 30, false, "숙소 · 다음 날 여기서 출발", null },
        { 2, "10:00", STAY_NAME, null, "숙소", STAY_LAT, STAY_LNG, 60, false, "체크아웃 11:00 · 지하철로 강남", null },
        { 2, "12:30", "스시도", "naver-1008521969", "일식당", 37.4952278, 127.0850148, 90, true, "점심 예약 5명 · 숙소에서 대중교통 약 65분 · 점심 뒤 해산", null },
    };

    // 비 예보(시연 고정): 2일차 13:00부터 비 → 일정 짤 때 알려 주고 [반영하기]를 누르면 2일차를 이 순서로 (11/08 주형: 날씨는 돌발 아님)
    private static final boolean IS_RAIN_NOTICE_ON = false; // 11/08: DEMO.md는 한강이 오후라 "비 → 오전으로" 안내는 꺼 둠 (켜면 일정 짤 때 2일차 비 예보·반영하기)
    private static final int RAIN_DAY = 2;
    private static final String CLEAR_SKY = "맑음";
    private static final int CLEAR_POP = 10;
    private static final String RAIN_SKY = "비";
    private static final int RAIN_POP = 80;
    private static final String UMBRELLA_NOTE = "13:00부터 비 · 우산 챙기기";
    private static final String RAIN_FROM = "13:00";
    private static final Object[][] RAIN_DAY_STOPS = {
        { 2, "10:00", "문래창작촌", "demo-mullae-art-village", "관광지", 37.5141903, 126.8974312, 80, false, "철공소 골목 사이 예술 공방·벽화 · 숙소에서 15분" },
        { 2, "11:40", "여의도한강공원", "tour-1059479", "시민공원", 37.5263997727, 126.9336095794, 60, false, "비 오기 전 오전에 한강" },
        { 2, "13:00", "이관복명장냉면 타임스퀘어점", "naver-2090669039", "냉면", 37.5169546, 126.9042079, 90, false, "리뷰에 단체석 넓다는 말이 많아요" },
        { 2, "14:35", "영등포 타임스퀘어", "demo-times-square", "쇼핑센터,할인매장", 37.5172094, 126.9040371, 180, false, "냉면집과 같은 건물 · 비 오는 오후는 실내 쇼핑몰" }, // 같은 건물이라 이동 5분 (11/08)
        { 2, "18:45", "서울역", null, "KTX정차역", 37.55406888733184, 126.97070335253385, 30, true, null },
    };

    /** 시나리오 일정 초안: 날짜는 고른 시작일(없으면 오늘)부터 1박 2일, 인원은 고른 인원(없으면 4명) */
    public PlanGenerateResponse makeDraft(PlanConditions conditions) {
        String startDate = hasText(conditions.getStartDate()) ? conditions.getStartDate() : Geo.now().toLocalDate().toString();
        PlanGenerateResponse response = new PlanGenerateResponse();
        response.setTitle(TITLE);
        response.setStartDate(startDate);
        response.setEndDate(LocalDate.parse(startDate).plusDays(NIGHTS).toString());
        response.setPeople(conditions.getPeople() == null ? DEFAULT_PEOPLE : conditions.getPeople());
        response.setTransports(new ArrayList<>(List.of("subway", "walk", "bus")));
        for (Object[] row : STOPS) {
            response.getStops().add(toStop(row));
        }
        response.getNotes().add(makeNote("1일차 19:00 산청숯불가든 을지로 회식은 예약 시각 그대로 고정했어요"));
        response.getNotes().add(makeNote("2일차 12:30 스시도 점심 예약 시각을 고정했어요"));
        return response;
    }

    /** AI 일정 화면에 미리 채워 둘 출발지·예약(KTX)·숙소 */
    public ScenarioSetup getSetup() {
        ScenarioSetup setup = new ScenarioSetup();
        setup.setOriginName(ORIGIN_NAME);
        setup.setOriginAddress(ORIGIN_ADDRESS);
        setup.setOriginLat(ORIGIN_LAT);
        setup.setOriginLng(ORIGIN_LNG);
        FixedVisit lunch = new FixedVisit(); // 2일차 점심 예약 (11/12 새 시나리오, KTX 대신)
        lunch.setName(LUNCH_NAME);
        lunch.setTime(LUNCH_TIME);
        lunch.setDay(NIGHTS + 1); // 마지막 날
        setup.getFixed().add(lunch);
        FixedVisit dinner = new FixedVisit(); // 1일차 회식 예약 (예약한 곳에 미리 넣어 둠)
        dinner.setName(DINNER_NAME);
        dinner.setTime(DINNER_TIME);
        dinner.setDay(1);
        setup.getFixed().add(dinner);
        setup.setStayName(STAY_NAME);
        setup.setStayAddress(STAY_ADDRESS);
        setup.setStayLat(STAY_LAT);
        setup.setStayLng(STAY_LNG);
        setup.setStayCheckInTime(STAY_CHECK_IN);
        setup.setStayCheckOutTime(STAY_CHECK_OUT);
        setup.setStartTime(START_TIME);
        setup.setDemoDate(DEMO_DATE);
        for (Object[] row : EXTRA_STAYS) {
            Stop extra = new Stop();
            extra.setDay((Integer) row[0]);
            extra.setName((String) row[1]);
            extra.setStay((Integer) row[2]);
            setup.getExtraStays().add(extra);
        }
        setup.setOverstayDay(OVERSTAY_DAY);
        setup.setOverstayName(OVERSTAY_NAME);
        setup.setOverstayMin(OVERSTAY_MIN);
        setup.setRainDay(IS_RAIN_NOTICE_ON ? RAIN_DAY : null);
        setup.setRainFrom(RAIN_FROM);
        for (Object[] row : RAIN_DAY_STOPS) {
            setup.getRainDayStops().add(toStop(row));
        }
        return setup;
    }

    /**
     * 시연 계정 날씨 카드: 진짜 오늘 = 1일차 맑음, 내일 = 2일차 13:00부터 비 (기온은 진짜 예보 그대로).
     * 예보가 없는 날(3일 뒤보다 먼 날)은 그대로 둠
     */
    public void applyWeather(List<DayWeather> list) {
        LocalDate today = Geo.now().toLocalDate();
        for (DayWeather day : list) {
            if (!day.isAvailable()) {
                continue;
            }
            LocalDate date = LocalDate.parse(day.getDate());
            if (date.equals(today) || (!IS_RAIN_NOTICE_ON && date.equals(today.plusDays(1)))) { // 비 안내를 끄면 이틀 다 맑음
                day.setSky(CLEAR_SKY);
                day.setMaxPop(CLEAR_POP);
                day.setRainFrom(null);
            } else if (IS_RAIN_NOTICE_ON && date.equals(today.plusDays(RAIN_DAY - 1))) {
                day.setSky(RAIN_SKY);
                day.setMaxPop(RAIN_POP);
                day.setRainFrom(RAIN_FROM);
                day.setOutfitNote(UMBRELLA_NOTE);
            }
        }
    }

    private Stop toStop(Object[] row) {
        Stop stop = new Stop();
        stop.setDay((Integer) row[0]);
        stop.setTime((String) row[1]);
        stop.setName((String) row[2]);
        stop.setPlaceId((String) row[3]);
        stop.setCategory((String) row[4]);
        stop.setLat((Double) row[5]);
        stop.setLng((Double) row[6]);
        stop.setStay((Integer) row[7]);
        stop.setFixed((Boolean) row[8]);
        stop.setMemo((String) row[9]);
        if (row.length > 10) {
            stop.setImportance((String) row[10]);
        }
        return stop;
    }

    private PlanNote makeNote(String text) {
        PlanNote note = new PlanNote();
        note.setText(text);
        return note;
    }

    private boolean hasText(String text) {
        return text != null && !text.isBlank();
    }
}
