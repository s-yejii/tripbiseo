package com.tripbiseo.plan.service;

import java.time.LocalDate;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Component;

import com.tripbiseo.common.util.Geo;
import com.tripbiseo.plan.dto.PlanGenerateResponse;
import com.tripbiseo.plan.dto.PlanNote;
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

    private final PlaceService placeService; // 영업시간 확인 (② 메서드를 부르기만 함)

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
