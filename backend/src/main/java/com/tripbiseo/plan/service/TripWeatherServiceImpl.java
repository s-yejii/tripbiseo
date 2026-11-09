package com.tripbiseo.plan.service;

import static com.tripbiseo.common.exception.ApiException.fail;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.tripbiseo.common.external.DayForecastClient;
import com.tripbiseo.common.external.ForecastHour;
import com.tripbiseo.common.util.Geo;
import com.tripbiseo.plan.dto.DayWeather;

import lombok.RequiredArgsConstructor;

/**
 * ③ 여행 날짜 날씨 + 옷차림 (11/06).
 * 기상청 단기예보는 3일 뒤까지만 있어요. 그 뒤 날짜는 숫자를 지어내지 않고 "3일 전부터 보여요"로 안내해요.
 * 옷차림은 흔히 쓰는 '기온별 옷차림 표'를 낮(9~21시) 평균 기온에 맞춰 골라요.
 */
@Service
@RequiredArgsConstructor
public class TripWeatherServiceImpl implements TripWeatherService {

    private static final int MAX_DAYS = 7;          // 한 번에 묻는 여행 날짜 수
    private static final int FORECAST_DAYS = 3;     // 기상청 단기예보 범위 (오늘 + 3일)
    private static final int DAY_START_HOUR = 9;    // 낮 = 9시~21시 (여행하는 시간)
    private static final int DAY_END_HOUR = 21;
    private static final int RAIN_POP_PERCENT = 60; // 강수확률이 이 이상이면 비 온다고 봄 (④ LiveWeatherChecker와 같은 기준)
    private static final int WIDE_GAP_DEGREE = 10;  // 일교차가 이 이상이면 겉옷 안내
    private static final String SOURCE = "기상청 단기예보";
    private static final DateTimeFormatter KMA_DATE = DateTimeFormatter.BASIC_ISO_DATE; // yyyyMMdd

    // 기온별 옷차림 표: 낮 평균 기온이 앞 숫자 이상이면 그 줄 (위에서부터 확인)
    private static final int[] OUTFIT_MIN_TEMP = {28, 23, 20, 17, 12, 9, 5};
    private static final String[] OUTFIT_TEXT = {
        "민소매 · 반팔 · 반바지 · 원피스",
        "반팔 · 얇은 셔츠 · 반바지 · 면바지",
        "얇은 가디건 · 긴팔 · 면바지 · 청바지",
        "얇은 니트 · 맨투맨 · 가디건 · 청바지",
        "자켓 · 가디건 · 야상 · 청바지",
        "자켓 · 트렌치코트 · 니트 · 청바지",
        "코트 · 가죽자켓 · 히트텍 · 니트",
    };
    private static final String OUTFIT_COLDEST = "패딩 · 두꺼운 코트 · 목도리 · 기모"; // 4도 이하

    private final DayForecastClient dayForecastClient;

    @Override
    public List<DayWeather> getWeatherList(double lat, double lng, LocalDate startDate, LocalDate endDate) {
        LocalDate lastDate = endDate == null ? startDate : endDate;
        if (lastDate.isBefore(startDate) || startDate.plusDays(MAX_DAYS - 1).isBefore(lastDate)) {
            throw fail(400, "날짜는 " + MAX_DAYS + "일 안으로 골라 주세요.");
        }
        List<ForecastHour> hours = loadHours(lat, lng, startDate);
        List<DayWeather> days = new ArrayList<>();
        for (LocalDate date = startDate; !date.isAfter(lastDate); date = date.plusDays(1)) {
            days.add(makeDay(date, hoursOf(hours, date)));
        }
        return days;
    }

    // 예보 범위 안 날짜가 하나라도 있을 때만 기상청을 불러요. 키가 없거나 기상청 오류면 그대로 503·502
    private List<ForecastHour> loadHours(double lat, double lng, LocalDate startDate) {
        LocalDate lastForecastDate = today().plusDays(FORECAST_DAYS);
        if (startDate.isAfter(lastForecastDate)) {
            return List.of();
        }
        return dayForecastClient.getHourList(lat, lng);
    }

    private List<ForecastHour> hoursOf(List<ForecastHour> hours, LocalDate date) {
        String kmaDate = date.format(KMA_DATE);
        List<ForecastHour> list = new ArrayList<>();
        for (ForecastHour hour : hours) {
            if (hour.getDate().equals(kmaDate)) {
                list.add(hour);
            }
        }
        return list;
    }

    /** 하루 예보 → 날씨 한 줄. 예보가 없으면 안내 문구만 */
    private DayWeather makeDay(LocalDate date, List<ForecastHour> hours) {
        DayWeather day = new DayWeather();
        day.setDate(date.toString());
        day.setSource(SOURCE);
        if (hours.isEmpty()) {
            day.setMessage(noForecastMessage(date));
            return day;
        }
        List<ForecastHour> dayHours = daytimeOf(hours);
        day.setAvailable(true);
        day.setMinTemp(round(minTemp(hours)));
        day.setMaxTemp(round(maxTemp(hours)));
        day.setDayTemp(round(average(dayHours, true)));
        day.setHumidity(round(average(dayHours, false)));
        day.setMaxPop(maxPop(dayHours));
        ForecastHour rainHour = findRainHour(dayHours);
        day.setRainFrom(rainHour == null ? null : Geo.toHhmm(rainHour.getHour() * 60));
        day.setSky(rainHour == null ? mostSky(dayHours) : rainWord(rainHour));
        day.setOutfit(outfitFor(day.getDayTemp()));
        day.setOutfitNote(outfitNote(day));
        return day;
    }

    private String noForecastMessage(LocalDate date) {
        String monthDay = date.getMonthValue() + "/" + date.getDayOfMonth();
        if (date.isBefore(today())) {
            return "지난 날짜예요";
        }
        return monthDay + " 날씨는 " + FORECAST_DAYS + "일 전부터 보여요";
    }

    // 낮(9~21시) 칸. 오늘이라 낮이 다 지났으면 남은 칸 전부
    private List<ForecastHour> daytimeOf(List<ForecastHour> hours) {
        List<ForecastHour> list = new ArrayList<>();
        for (ForecastHour hour : hours) {
            if (hour.getHour() >= DAY_START_HOUR && hour.getHour() <= DAY_END_HOUR) {
                list.add(hour);
            }
        }
        return list.isEmpty() ? hours : list;
    }

    // 최저: 기상청 TMN이 있으면 그 값, 없으면(오늘 06시가 지남) 남은 시간 기온 중 가장 낮은 값
    private Double minTemp(List<ForecastHour> hours) {
        Double min = null;
        for (ForecastHour hour : hours) {
            if (hour.getTmn() != null) {
                return hour.getTmn();
            }
            if (hour.getTmp() != null && (min == null || hour.getTmp() < min)) {
                min = hour.getTmp();
            }
        }
        return min;
    }

    // 최고: 기상청 TMX가 있으면 그 값, 없으면 남은 시간 기온 중 가장 높은 값
    private Double maxTemp(List<ForecastHour> hours) {
        Double max = null;
        for (ForecastHour hour : hours) {
            if (hour.getTmx() != null) {
                return hour.getTmx();
            }
            if (hour.getTmp() != null && (max == null || hour.getTmp() > max)) {
                max = hour.getTmp();
            }
        }
        return max;
    }

    // isTemp=true면 기온, false면 습도의 평균 (값 없는 칸은 빼고)
    private Double average(List<ForecastHour> hours, boolean isTemp) {
        double sum = 0;
        int count = 0;
        for (ForecastHour hour : hours) {
            Double value = isTemp ? hour.getTmp() : hour.getReh();
            if (value != null) {
                sum += value;
                count++;
            }
        }
        return count == 0 ? null : sum / count;
    }

    private Integer maxPop(List<ForecastHour> hours) {
        Integer max = null;
        for (ForecastHour hour : hours) {
            if (hour.getPop() != null && (max == null || hour.getPop() > max)) {
                max = hour.getPop();
            }
        }
        return max;
    }

    /** 낮에 비·눈이 오거나(PTY ≠ 0) 강수확률 60% 이상인 첫 칸 (없으면 null) */
    private ForecastHour findRainHour(List<ForecastHour> hours) {
        for (ForecastHour hour : hours) {
            boolean isFalling = hour.getPty() != null && hour.getPty() != 0;
            boolean isLikely = hour.getPop() != null && hour.getPop() >= RAIN_POP_PERCENT;
            if (isFalling || isLikely) {
                return hour;
            }
        }
        return null;
    }

    private String rainWord(ForecastHour hour) {
        return hour.getPty() != null && hour.getPty() == 3 ? "눈" : "비";
    }

    // 낮에 가장 많이 나온 하늘 상태 (1 맑음 · 3 구름많음 · 4 흐림)
    private String mostSky(List<ForecastHour> hours) {
        Map<Integer, Integer> counts = new HashMap<>();
        Integer best = null;
        for (ForecastHour hour : hours) {
            if (hour.getSky() == null) {
                continue;
            }
            int count = counts.getOrDefault(hour.getSky(), 0) + 1;
            counts.put(hour.getSky(), count);
            if (best == null || count > counts.get(best)) {
                best = hour.getSky();
            }
        }
        if (best == null) {
            return null;
        }
        return best == 1 ? "맑음" : best == 3 ? "구름많음" : "흐림";
    }

    /** 낮 평균 기온 → 옷차림 한 줄 (기온을 모르면 null) */
    private String outfitFor(Integer dayTemp) {
        if (dayTemp == null) {
            return null;
        }
        for (int i = 0; i < OUTFIT_MIN_TEMP.length; i++) {
            if (dayTemp >= OUTFIT_MIN_TEMP[i]) {
                return OUTFIT_TEXT[i];
            }
        }
        return OUTFIT_COLDEST;
    }

    // 일교차가 크면 겉옷, 비·눈 소식이 있으면 우산
    private String outfitNote(DayWeather day) {
        List<String> notes = new ArrayList<>();
        if (day.getMinTemp() != null && day.getMaxTemp() != null && day.getMaxTemp() - day.getMinTemp() >= WIDE_GAP_DEGREE) {
            notes.add("일교차 " + (day.getMaxTemp() - day.getMinTemp()) + "도라 겉옷 하나 더");
        }
        if (day.getRainFrom() != null) {
            notes.add("우산 챙기기"); // 몇 시부터인지는 날씨 줄(rainFrom)에 이미 있어요
        }
        return notes.isEmpty() ? null : String.join(" · ", notes);
    }

    private LocalDate today() {
        return Geo.now().toLocalDate(); // 시연 시계가 생기면 그 날짜를 따라가요
    }

    private static Integer round(Double value) {
        return value == null ? null : (int) Math.round(value);
    }
}
