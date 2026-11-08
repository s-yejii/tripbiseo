package com.tripbiseo.plan.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import com.tripbiseo.common.dev.ScenarioSetup;
import com.tripbiseo.common.util.Geo;
import com.tripbiseo.plan.dto.FixedVisit;
import com.tripbiseo.plan.dto.PlanConditions;
import com.tripbiseo.plan.dto.PlanGenerateResponse;
import com.tripbiseo.plan.dto.PlanNote;
import com.tripbiseo.trip.dto.Stop;

/**
 * 발표 시나리오 일정 (③, 11/08 주형: "시연 계정은 뭘 안 눌러도 우리 시나리오대로").
 * 시연 계정(DEMO_ACCOUNTS)이 AI 일정을 만들면 고른 조건과 상관없이 이 일정이 나와요. 날짜만 사용자가 고른 날.
 * 장소·일차·시각은 여행 86(영등포 워크숍 1박 2일)과 같아요. 시나리오를 바꾸려면 아래 STOPS 표만 고치면 돼요.
 *   출발: 서울 구로구 가마산로 242 3층 → 1일차: 한강 → 더현대 카페 → 18:30 회식(고정) → 숙소(호텔 GMS, 영등포구 경인로 867)
 *   2일차: 영등포공원 → 냉면 → 타임스퀘어 → 한강(야외, 비·늦음 장면) → 서울역 KTX 19:00(고정, 15분 전 도착)
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
    private static final String TRAIN_TIME = "19:00"; // KTX 출발. 일정엔 15분 전 18:45 도착
    private static final String STAY_NAME = "호텔 GMS";
    private static final String STAY_ADDRESS = "서울 영등포구 경인로 867";
    private static final double STAY_LAT = 37.5174808;
    private static final double STAY_LNG = 126.9089452;
    private static final String STAY_CHECK_IN = "15:00";
    private static final String STAY_CHECK_OUT = "11:00";
    private static final String START_TIME = "15:00"; // 시연 계정은 1일차 이 시각부터 (가마산로 242에서 출발, 11/08 주형)

    // 일차, 시각, 이름, 장소 번호(retrip_place.id, 없으면 null), 종류, 위도, 경도, 머무는 분, 고정, 메모
    private static final Object[][] STOPS = {
        { 1, "15:40", "여의도한강공원", "tour-1059479", "시민공원", 37.5263997727, 126.9336095794, 60, false, "가마산로에서 지하철로 40분 · 다음 카페까지 걸어서 7분" },
        { 1, "16:50", "테디뵈르하우스 더현대서울점", "naver-1096877686", "카페,디저트", 37.5253257, 126.9287361, 60, false, "인스타에서 담은 카페 · 평점 4.8" },
        { 1, "18:30", "문래 골목집", "naver-597569164", "오리요리", 37.5133865, 126.8931051, 100, true, null },
        { 1, "20:40", "호텔 GMS", "demo-hotel-gms", "숙소", 37.5174808, 126.9089452, 30, false, "예약해 둔 숙소 · 영등포역 5번 출구 걸어서 4분 · 다음 날 여기서 출발" },
        { 2, "10:00", "영등포공원", "demo-yeongdeungpo-park", "관광지", 37.5150168, 126.9107969, 80, false, "숙소에서 걸어서 5분 · 아침 산책" },
        { 2, "11:40", "이관복명장냉면 타임스퀘어점", "naver-2090669039", "냉면", 37.5169546, 126.9042079, 100, false, "리뷰에 단체석 넓다는 말이 많아요" },
        { 2, "13:40", "영등포 타임스퀘어", "demo-times-square", "쇼핑센터,할인매장", 37.5172094, 126.9040371, 80, false, "실내라 비 와도 괜찮아요" },
        { 2, "15:30", "여의도한강공원", "tour-1059479", "시민공원", 37.5263997727, 126.9336095794, 60, false, "KTX 타기 전 마지막 산책" },
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
        response.getNotes().add(makeNote("KTX 19:00 → 서울역에 18:45 도착으로 맞췄어요 (출발 15분 전)"));
        response.getNotes().add(makeNote("1일차 18:30 문래 골목집은 예약 시각 그대로 고정했어요"));
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
        return setup;
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
