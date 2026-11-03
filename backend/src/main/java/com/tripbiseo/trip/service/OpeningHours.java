package com.tripbiseo.trip.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import com.tripbiseo.common.util.Geo;
import com.tripbiseo.trip.dto.DayHours;
import com.tripbiseo.trip.dto.OpenInfo;
import com.tripbiseo.trip.dto.Place;

/**
 * ② 영업시간 글자 읽기. retrip_place의 use_time·rest_date 원문을 읽어 요일별 시간·영업 여부를 계산해요.
 * PlaceServiceImpl 안에서만 쓰는 도우미라 인터페이스 없이 @Component (DECISIONS 11/01).
 * 원문 예: "08:00~19:30 (요일별: 월 08:00-19:30 / … / 토 정기휴무 (매주 토요일) / 일 08:00-17:00)", "상시 개방"
 * 예전 코드: git show backend-v0:backend/src/main/java/com/tripbiseo/service/PlaceService.java (hours/range/openAt/openInfo/closedOn)
 */
@Component
public class OpeningHours {

    private static final String[] WEEK = {"월", "화", "수", "목", "금", "토", "일"};
    private static final int MINUTES_PER_DAY = 1440;
    private static final int CLOSING_SOON_MIN = 60;
    // Pattern: "10:00-22:00", "10:00 ~ 22:00" 같은 시간 범위를 찾는 정규식
    private static final Pattern RANGE = Pattern.compile("(\\d{1,2}:\\d{2})\\s*[-~]\\s*(\\d{1,2}:\\d{2})");
    // 날짜가 붙은 요일 표기 "금(10/9)"의 "(10/9)" 부분 (그대로 두면 '/'로 나눌 때 깨짐)
    private static final Pattern DATE_NOTE = Pattern.compile("\\(\\s*\\d{1,2}\\s*/\\s*\\d{1,2}\\s*\\)");

    /** 요일별 영업시간 목록. 정보가 없으면 [] */
    public List<DayHours> getDayHoursList(Place place, LocalDate today) {
        List<DayHours> list = new ArrayList<>();
        if (place.getUseTime() == null || place.getUseTime().isBlank()) {
            return list;
        }
        String todayName = getDayName(today);
        String useTime = place.getUseTime();
        int start = useTime.indexOf("요일별:");
        if (start < 0) {
            list.add(toDayHours("매일", useTime, true));
            return list;
        }
        String body = DATE_NOTE.matcher(useTime.substring(start + 4)).replaceAll("").replace(")", "");
        for (String part : body.split("/")) {
            String text = part.trim();
            if (text.length() < 2) {
                continue;
            }
            String day = text.startsWith("매일") ? "매일" : text.substring(0, 1);
            boolean isToday = day.equals(todayName) || day.equals("매일");
            list.add(toDayHours(day, text.substring(day.length()).trim(), isToday));
        }
        return list;
    }

    /** 지금 영업 중인지. 오늘 휴무면 false, 정보가 없으면 null */
    public Boolean isOpenNow(Place place, LocalDate today, int nowMinutes) {
        if (findRestReason(place, today) != null) {
            return false;
        }
        return isOpenAt(place, today, nowMinutes);
    }

    /**
     * 그날 arrive(자정부터 센 분)에 도착해 stay분 머물 때의 영업 정보.
     * open(모르면 null) / closedReason(닫혀 있을 때) / closeAt / minutesLeft·closingSoon(열려 있을 때)
     */
    public OpenInfo getOpenInfo(Place place, LocalDate date, int arrive, int stay) {
        OpenInfo info = new OpenInfo();
        String restReason = place == null ? null : findRestReason(place, date);
        if (restReason != null) {
            info.setOpen(false);
            info.setClosedReason("오늘 휴무 (" + restReason + ")");
            return info;
        }
        int[] range = place == null ? null : getOpenRange(place, date);
        if (range == null) {
            return info; // 영업시간 정보 없음 → open = null
        }
        info.setOpen(isInRange(range, arrive));
        if (range[1] - range[0] >= MINUTES_PER_DAY) {
            return info; // 24시간 영업이면 닫는 시각 없음
        }
        info.setCloseAt(Geo.toHhmm(range[1]));
        if (Boolean.FALSE.equals(info.getOpen())) {
            info.setClosedReason(arrive < range[0] ? Geo.toHhmm(range[0]) + "에 문을 열어요" : Geo.toHhmm(range[1]) + "에 영업이 끝나요");
            return info;
        }
        int closeMinutes = arrive >= range[0] ? range[1] : range[1] - MINUTES_PER_DAY; // 새벽 영업 중이면 어제 연 범위
        int minutesLeft = closeMinutes - arrive;
        info.setMinutesLeft(minutesLeft);
        info.setClosingSoon(minutesLeft <= Math.max(CLOSING_SOON_MIN, stay));
        return info;
    }

    // ---------------------------------------------------------------- 도우미

    /** 그 날 그 시각에 열려 있나. 정보가 없으면 null */
    private Boolean isOpenAt(Place place, LocalDate date, int minutes) {
        int[] range = getOpenRange(place, date);
        return range == null ? null : isInRange(range, minutes);
    }

    /** 범위 안인지 (새벽까지 영업하면 다음 날 새벽 시각도 확인) */
    private boolean isInRange(int[] range, int minutes) {
        boolean isToday = minutes >= range[0] && minutes < range[1];
        boolean isLateNight = minutes + MINUTES_PER_DAY >= range[0] && minutes + MINUTES_PER_DAY < range[1];
        return isToday || isLateNight;
    }

    /** 그 날 영업 [여는 분, 닫는 분]. 새벽까지면 닫는 분이 1440을 넘음. 24시간이면 [0, 2880], 휴무면 [0, 0], 정보 없으면 null */
    private int[] getOpenRange(Place place, LocalDate date) {
        String useTime = place.getUseTime();
        if (useTime == null || useTime.isBlank()) {
            return null;
        }
        if (useTime.contains("상시") || useTime.contains("24시간")) {
            return new int[] {0, MINUTES_PER_DAY * 2};
        }
        String text = findTodayText(place, date);
        if (text.contains("휴무")) {
            return new int[] {0, 0};
        }
        Matcher matcher = RANGE.matcher(text);
        if (!matcher.find()) {
            return null;
        }
        int open = Geo.toMinutes(matcher.group(1));
        int close = Geo.toMinutes(matcher.group(2));
        if (close <= open) {
            close += MINUTES_PER_DAY; // 새벽까지 영업
        }
        return new int[] {open, close};
    }

    /** 그 요일의 영업시간 글자 (요일별 표기가 없으면 원문 전체) */
    private String findTodayText(Place place, LocalDate date) {
        String dayName = getDayName(date);
        String text = place.getUseTime();
        for (DayHours hours : getDayHoursList(place, date)) {
            if (hours.getDay().equals(dayName) || hours.getDay().equals("매일")) {
                text = hours.getText();
            }
        }
        return text;
    }

    /** 휴무일이면 이유 글, 아니면 null ("매주 월요일" / 요일별 표기의 "월 정기휴무") */
    private String findRestReason(Place place, LocalDate date) {
        String dayName = getDayName(date);
        String restDate = place.getRestDate();
        if (restDate != null && restDate.contains(dayName + "요일") && !restDate.contains("째")) {
            return restDate; // "둘째 주 월요일"처럼 몇째 주 휴무는 그날인지 알 수 없어서 건너뜀
        }
        String useTime = place.getUseTime();
        if (useTime != null && useTime.matches(".*(요일별:|/)\\s*" + dayName + "\\s*(정기)?휴무.*")) {
            return dayName + "요일 휴무";
        }
        return null;
    }

    /** 요일 이름 (월~일) */
    private String getDayName(LocalDate date) {
        return WEEK[date.getDayOfWeek().getValue() - 1];
    }

    private DayHours toDayHours(String day, String text, boolean isToday) {
        DayHours hours = new DayHours();
        hours.setDay(day);
        hours.setText(text);
        hours.setToday(isToday);
        return hours;
    }
}
