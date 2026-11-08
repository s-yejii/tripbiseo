package com.tripbiseo.plan.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import com.tripbiseo.common.util.Geo;
import com.tripbiseo.plan.dto.PlanConditions;
import com.tripbiseo.plan.dto.PlanGenerateResponse;
import com.tripbiseo.plan.dto.PlanNote;
import com.tripbiseo.trip.dto.Stop;

/**
 * 발표 시나리오 일정 (③, 11/08 주형: "시연 계정은 뭘 안 눌러도 우리 시나리오대로").
 * 시연 계정(DEMO_ACCOUNTS)이 AI 일정을 만들면 고른 조건과 상관없이 이 일정이 나와요. 날짜만 사용자가 고른 날.
 * 장소·일차·시각은 여행 86(영등포 워크숍 1박 2일)과 같아요. 시나리오를 바꾸려면 아래 STOPS 표만 고치면 돼요.
 *   1일차: 한강 → 더현대 카페 → 18:30 회식(고정) → 숙소
 *   2일차: 영등포공원 → 냉면 → 타임스퀘어 → 한강(야외, 비·늦음 장면) → 서울역 KTX 19:00(고정, 15분 전 도착)
 */
@Component
public class DemoScenario {

    private static final String TITLE = "영등포 워크숍 1박 2일";
    private static final int DEFAULT_PEOPLE = 4;
    private static final int NIGHTS = 1;

    // 일차, 시각, 이름, 장소 번호(retrip_place.id, 없으면 null), 종류, 위도, 경도, 머무는 분, 고정, 메모
    private static final Object[][] STOPS = {
        { 1, "15:00", "여의도한강공원", "tour-1059479", "시민공원", 37.5263997727, 126.9336095794, 80, false, "한강 바로 옆 · 다음 카페까지 걸어서 7분" },
        { 1, "16:40", "테디뵈르하우스 더현대서울점", "naver-1096877686", "카페,디저트", 37.5253257, 126.9287361, 70, false, "인스타에서 담은 카페 · 평점 4.8" },
        { 1, "18:30", "문래 골목집", "naver-597569164", "오리요리", 37.5133865, 126.8931051, 100, true, null },
        { 1, "20:40", "페어필드 바이 메리어트 서울", null, "숙소", 37.5171665, 126.9095753, 30, false, "숙소 · 체크인 16:00 · 다음 날 여기서 출발" },
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
