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
 * 장소·일차·시각은 docs/DEMO.md(11/06 팀 시나리오)와 같아요. 시나리오를 바꾸려면 아래 STOPS 표만 고치면 돼요.
 *   출발: 서울 구로구 가마산로 242 3층 → 1일차: 호텔 GMS 체크인 → 18:30 문래 골목집 회식(고정) → 하나마토 문래 → 호텔
 *   2일차: 무탄 11:00 → 더현대 서울 → 테디뵈르하우스(15:30 출발 계획, 돌발: 24분 더 머묾) → 한강 → 타임스퀘어(꼭) → 서울역 18:30(KTX 19:00)
 */
@Component
public class DemoScenario {

    private static final String TITLE = "영등포 워크숍 1박 2일";
    private static final int DEFAULT_PEOPLE = 4;
    private static final int NIGHTS = 1;

    // 이야기: 구로구 가마산로 242 3층에서 출발. 이틀 와야 했는데 1일차만 오게 돼서, 기차·숙소를 잡아 두고 그동안 여행해요
    private static final String ORIGIN_NAME = "가마산로 242 3층 (공간정보아카데미)";
    private static final String ORIGIN_ADDRESS = "서울 구로구 가마산로 242";
    private static final double ORIGIN_LAT = 37.494361055919896; // 카카오 주소 검색 (11/08)
    private static final double ORIGIN_LNG = 126.88768106730573;
    private static final String TRAIN_NAME = "서울역";
    private static final String TRAIN_TIME = "19:00"; // KTX 출발. 시나리오 일정엔 30분 전 18:30 도착 (DEMO.md)
    private static final String STAY_NAME = "호텔 GMS";
    private static final String STAY_ADDRESS = "서울 영등포구 경인로 867";
    private static final double STAY_LAT = 37.5174808;
    private static final double STAY_LNG = 126.9089452;
    private static final String STAY_CHECK_IN = "16:00"; // 호텔 GMS 체크인 16:00 · 체크아웃 12:00 (DB)
    private static final String STAY_CHECK_OUT = "12:00";
    private static final int OVERSTAY_DAY = 2;          // 돌발: 2일차 테디뵈르하우스에서
    private static final String OVERSTAY_NAME = "테디뵈르하우스 더현대서울점";
    private static final int OVERSTAY_MIN = 24;         // 계획보다 24분 더 머묾 → 15:54 출발 → KTX에 늦음
    private static final String START_TIME = "15:00"; // 시연 계정은 1일차 이 시각부터 (가마산로 242에서 출발, 11/08 주형)

    // 일차, 시각, 이름, 장소 번호(retrip_place.id, 없으면 null), 종류, 위도, 경도, 머무는 분, 고정, 메모
    // DEMO.md(11/06 팀 시나리오)대로 + 출발만 가마산로 242 15:00 (11/08 주형 확정)
    // 일차, 시각, 이름, 장소 번호, 종류, 위도, 경도, 머무는 분, 고정, 메모, 중요도(must면 줄이기·빼기 대상 아님)
    private static final Object[][] STOPS = {
        { 1, "16:00", "호텔 GMS", "demo-hotel-gms", "숙소", 37.5174808, 126.9089452, 120, false, "체크인 16:00 · 짐 풀고 쉬기 · 영등포역 5번 출구 4분", null },
        { 1, "18:30", "문래 골목집", "naver-597569164", "오리요리", 37.5133865, 126.8931051, 100, true, null, null },
        { 1, "20:20", "하나마토 문래", "naver-2085802165", "이자카야", 37.5117226, 126.8923321, 70, false, "2차 · 회식 장소에서 걸어서 3분", null },
        { 1, "21:40", "호텔 GMS", "demo-hotel-gms", "숙소", 37.5174808, 126.9089452, 30, false, "숙소 · 다음 날 여기서 출발", null },
        { 2, "11:00", "무탄 여의도점", "naver-1971705948", "중식", 37.5215212, 126.9228725, 70, false, "어제 저녁이 오리요리라 점심은 겹치지 않게 중식으로 골랐어요", null },
        { 2, "12:30", "더현대 서울", "demo-thehyundai-seoul", "백화점", 37.5259638, 126.9284184, 90, false, "실내 구경 · 지하 1층에 다음 카페", null },
        { 2, "14:10", "테디뵈르하우스 더현대서울점", "naver-1096877686", "카페,디저트", 37.5253257, 126.9287361, 80, false, "평점 4.85 · 15:30에 일어나기", null },
        { 2, "15:40", "여의도한강공원", "tour-1059479", "시민공원", 37.5263997727, 126.9336095794, 60, false, "카페에서 걸어서 10분", null },
        { 2, "17:00", "영등포 타임스퀘어", "demo-times-square", "쇼핑센터,할인매장", 37.5172094, 126.9040371, 50, false, "팀장님 사모님 선물 · 꼭 갈 곳", "must" },
        { 2, "18:30", "서울역", null, "KTX정차역", 37.55406888733184, 126.97070335253385, 30, true, null, null },
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
        response.getNotes().add(makeNote("KTX 19:00 → 서울역에 18:30 도착으로 맞췄어요 (30분 전)"));
        response.getNotes().add(makeNote("1일차 18:30 문래 골목집 회식은 예약 시각 그대로 고정했어요"));
        return response;
    }

    /** AI 일정 화면에 미리 채워 둘 출발지·예약(KTX)·숙소 */
    public ScenarioSetup getSetup() {
        ScenarioSetup setup = new ScenarioSetup();
        setup.setOriginName(ORIGIN_NAME);
        setup.setOriginAddress(ORIGIN_ADDRESS);
        setup.setOriginLat(ORIGIN_LAT);
        setup.setOriginLng(ORIGIN_LNG);
        FixedVisit train = new FixedVisit();
        train.setName(TRAIN_NAME);
        train.setTime(TRAIN_TIME);
        train.setDay(NIGHTS + 1); // 마지막 날
        setup.getFixed().add(train);
        setup.setStayName(STAY_NAME);
        setup.setStayAddress(STAY_ADDRESS);
        setup.setStayLat(STAY_LAT);
        setup.setStayLng(STAY_LNG);
        setup.setStayCheckInTime(STAY_CHECK_IN);
        setup.setStayCheckOutTime(STAY_CHECK_OUT);
        setup.setStartTime(START_TIME);
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
