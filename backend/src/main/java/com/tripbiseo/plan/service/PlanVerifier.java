package com.tripbiseo.plan.service;

import java.time.LocalDate;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Component;

import com.tripbiseo.common.util.Geo;
import com.tripbiseo.plan.dto.CrowdHour;
import com.tripbiseo.plan.dto.PlanGenerateResponse;
import com.tripbiseo.plan.dto.PlanNote;
import com.tripbiseo.plan.mapper.PlanCrowdMapper;
import com.tripbiseo.trip.dto.OpenInfo;
import com.tripbiseo.trip.dto.Place;
import com.tripbiseo.trip.dto.Stop;
import com.tripbiseo.trip.service.PlaceService;

import lombok.RequiredArgsConstructor;

/**
 * ③ 검증 줄 (11/13): 다 짠 일정에서 예약(고정)·꼭 갈 곳마다 우리 데이터로 다시 확인한 한 줄을 안내 목록에 더함.
 * 예) "✓ 산청숯불가든 을지로 · 11/15(월) 19:00 영업 · 숙소에서 15분"
 * AI가 고른 걸 DB 영업시간·거리로 한 번 더 확인해서 보여 주는 것 (AI가 지어낸 게 아님을 화면에서 보여 줌).
 * 영업시간을 모르면 "영업시간 정보 없음"이라고 그대로 말함 (모르는 걸 맞다고 하지 않음)
 */
@Component
@RequiredArgsConstructor
public class PlanVerifier {

    private static final String STAY_CATEGORY = "숙소";
    private static final String MUST = "must";
    private static final int MAX_LINES = 6; // 안내 목록이 너무 길어지지 않게

    private static final int CROWD_FIRST_HOUR = 7;  // 생활인구 데이터는 7~23시만
    private static final int CROWD_LAST_HOUR = 23;
    private static final int BUSY_PCT = 80;   // 서울 순위 이 이상이면 붐빔
    private static final int NORMAL_PCT = 50; // 이 이상이면 보통, 아래면 한산

    private final PlaceService placeService; // 영업시간 확인 (② 메서드를 부르기만 함)
    private final PlanCrowdMapper planCrowdMapper; // 250m 칸 평소 붐빔 (② 찬기 표 읽기만)

    /** 예약·꼭 갈 곳마다 검증 한 줄을 response 안내(notes)에 더함 */
    public void addVerifyNotes(PlanGenerateResponse response) {
        if (response.getStartDate() == null) {
            return;
        }
        LocalDate start = LocalDate.parse(response.getStartDate());
        List<String> lines = new ArrayList<>();
        for (Stop stop : response.getStops()) {
            if (lines.size() >= MAX_LINES) {
                break;
            }
            if (isTarget(stop)) {
                lines.add(makeLine(stop, start.plusDays(dayOf(stop) - 1), response.getStops()));
            }
        }
        for (String line : lines) {
            PlanNote note = new PlanNote();
            note.setText(line);
            response.getNotes().add(note);
        }
    }

    /** 예약(고정)이거나 꼭 갈 곳인 칸 (숙소 칸은 뺌) */
    private boolean isTarget(Stop stop) {
        boolean isImportant = Boolean.TRUE.equals(stop.getFixed()) || MUST.equals(stop.getImportance());
        return isImportant && !STAY_CATEGORY.equals(stop.getCategory()) && stop.getTime() != null;
    }

    private String makeLine(Stop stop, LocalDate date, List<Stop> stops) {
        String weekday = date.getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.KOREAN); // "월"
        StringBuilder line = new StringBuilder("✓ ").append(stop.getName())
            .append(" · ").append(date.getMonthValue()).append('/').append(date.getDayOfMonth())
            .append('(').append(weekday).append(") ").append(stop.getTime());
        String openText = openTextOf(stop, date);
        if (openText != null) {
            line.append(' ').append(openText);
        }
        String crowdText = crowdTextOf(stop, date);
        if (crowdText != null) {
            line.append(crowdText);
        }
        Integer minutes = minutesFromStay(stop, stops);
        if (minutes != null) {
            line.append(" · 숙소에서 ").append(minutes).append("분");
        }
        return line.toString();
    }

    /** 그 날·그 시각 영업: "영업" / "영업 안 해요" / "영업시간 정보 없음". 장소 번호가 없으면(역·터미널 등) null */
    private String openTextOf(Stop stop, LocalDate date) {
        if (stop.getPlaceId() == null) {
            return null;
        }
        Place place = placeService.getPlace(stop.getPlaceId());
        if (place == null) {
            return null;
        }
        int stay = stop.getStay() == null ? 0 : stop.getStay();
        OpenInfo open = placeService.getOpenInfo(place, date, Geo.toMinutes(stop.getTime()), stay);
        if (open.getOpen() == null) {
            return "· 영업시간 정보 없음";
        }
        return Boolean.TRUE.equals(open.getOpen()) ? "영업" : "· 그 시각 영업 안 해요";
    }

    /**
     * 그 요일·시각에 이 장소가 든 250m 칸이 평소 얼마나 붐비는지 (서울 생활인구, 찬기 표).
     * 데이터가 없는 곳(중구·강남·종로 밖)·시각(7~23시 밖)이면 null = 줄에 안 붙임 (가짜 값 안 만듦)
     */
    private String crowdTextOf(Stop stop, LocalDate date) {
        if (stop.getLat() == null || stop.getLng() == null) {
            return null;
        }
        int hour = Geo.toMinutes(stop.getTime()) / 60;
        if (hour < CROWD_FIRST_HOUR || hour > CROWD_LAST_HOUR) {
            return null;
        }
        int dayKind = date.getDayOfWeek().getValue(); // 1=월 … 7=일 (공휴일 8은 달력이 없어서 안 씀)
        CrowdHour crowd = planCrowdMapper.selectCrowdHour(stop.getLat(), stop.getLng(), dayKind, hour);
        if (crowd == null || crowd.getSeoulPct() == null) {
            return null;
        }
        return " · " + hour + "시 " + crowdLevelText(crowd.getSeoulPct());
    }

    /**
     * 서울 순위(같은 요일·시각 서울 칸 중 0~100, 높을수록 붐빔)를 세 단계 말로 (11/13 주형 A안).
     * 80 이상 "붐빔(서울 상위 N%)" · 50~79 "보통" · 50 미만 "한산"
     */
    String crowdLevelText(int seoulPct) {
        if (seoulPct >= BUSY_PCT) {
            return "붐빔(서울 상위 " + Math.max(1, 100 - seoulPct) + "%)";
        }
        return seoulPct >= NORMAL_PCT ? "보통" : "한산";
    }

    /** 그날 숙소(없으면 전날 숙소)에서 이 칸까지 어림 이동 분. 숙소가 없으면 null */
    private Integer minutesFromStay(Stop stop, List<Stop> stops) {
        Stop stay = findStay(stops, dayOf(stop));
        if (stay == null) {
            stay = findStay(stops, dayOf(stop) - 1);
        }
        if (stay == null || stay.getLat() == null || stop.getLat() == null) {
            return null;
        }
        return PlanTravelTime.minutesBetween(new double[] {stay.getLat(), stay.getLng()}, new double[] {stop.getLat(), stop.getLng()});
    }

    private Stop findStay(List<Stop> stops, int day) {
        for (Stop stop : stops) {
            if (STAY_CATEGORY.equals(stop.getCategory()) && dayOf(stop) == day && stop.getLat() != null) {
                return stop;
            }
        }
        return null;
    }

    private int dayOf(Stop stop) {
        return stop.getDay() == null ? 1 : stop.getDay();
    }
}
