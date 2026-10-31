package com.tripbiseo.common.external;

import static com.tripbiseo.common.exception.ApiException.fail;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.tripbiseo.common.util.Geo;
import com.tripbiseo.common.util.TtlCache;

import tools.jackson.databind.JsonNode;

/**
 * 기상청 단기예보 (공공데이터포털 '기상청_단기예보 조회서비스').
 * 결과: 오늘 남은 시간대별 강수확률(pop)·강수형태(pty)·기온(tmp), 최대 18시간
 */
@Component
public class WeatherClient {

    private static final String URL = "https://apis.data.go.kr/1360000/VilageFcstInfoService_2.0/getVilageFcst";
    private static final long CACHE_MS = 30 * 60_000L; // 같은 격자·발표 시각은 30분 기억
    private static final int MAX_HOURS = 18;
    private static final List<String> CATEGORIES = List.of("POP", "PTY", "TMP");
    private static final int[] BASE_HOURS = {23, 20, 17, 14, 11, 8, 5, 2}; // 발표 시각(늦은 순)
    private static final int RELEASE_DELAY_MIN = 10; // 발표 10분 뒤부터 제공

    private final ExternalApi externalApi;
    private final TtlCache cache = new TtlCache(200);

    @Value("${KMA_SERVICE_KEY:}")
    private String serviceKey;

    public WeatherClient(ExternalApi externalApi) {
        this.externalApi = externalApi;
    }

    /** 이 좌표의 오늘 시간대별 예보. 키가 없으면 503, 기상청 오류는 502 */
    public Forecast getForecast(double lat, double lng) {
        if (serviceKey.isBlank()) {
            throw fail(503, "기상청 키가 없어요.");
        }
        // Encoding 키(%가 들어 있음)를 넣었으면 Decoding 키로 바꿈 (주소를 만들 때 다시 인코딩하므로)
        String key = serviceKey.contains("%") ? URLDecoder.decode(serviceKey, StandardCharsets.UTF_8) : serviceKey;
        int[] grid = toGrid(lat, lng);
        String[] base = baseTime(Geo.now());
        String cacheKey = "kma:" + grid[0] + "," + grid[1] + base[0] + base[1];
        return cache.get(cacheKey, CACHE_MS, () -> callForecast(key, grid, base));
    }

    private Forecast callForecast(String key, int[] grid, String[] base) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("serviceKey", key);
        params.put("pageNo", 1);
        params.put("numOfRows", 400);
        params.put("dataType", "JSON");
        params.put("base_date", base[0]);
        params.put("base_time", base[1]);
        params.put("nx", grid[0]);
        params.put("ny", grid[1]);
        JsonNode json = externalApi.getJson(externalApi.buildUrl(URL, params), Map.of(), "기상청");
        JsonNode header = json.path("response").path("header");
        String code = header.path("resultCode").asString("00");
        if (!code.equals("00")) {
            throw fail(502, "기상청 오류: " + header.path("resultMsg").asString(""));
        }
        return toForecast(json.path("response").path("body").path("items").path("item"));
    }

    // 항목 여러 줄(시각 × 종류)을 시각별 한 줄로 묶음. TreeMap이라 시각 순으로 정렬됨
    private Forecast toForecast(JsonNode items) {
        Map<String, Forecast.Hour> byTime = new TreeMap<>();
        for (JsonNode item : items) {
            String category = item.path("category").asString("");
            if (!CATEGORIES.contains(category)) {
                continue;
            }
            String date = item.path("fcstDate").asString("");
            String time = item.path("fcstTime").asString("");
            Forecast.Hour hour = byTime.get(date + time);
            if (hour == null) {
                hour = new Forecast.Hour();
                hour.setDate(date);
                hour.setTime(time.substring(0, 2) + ":00");
                byTime.put(date + time, hour);
            }
            String value = item.path("fcstValue").asString("");
            if (category.equals("POP")) {
                hour.setPop(value);
            } else if (category.equals("PTY")) {
                hour.setPty(value);
            } else {
                hour.setTmp(value);
            }
        }
        List<Forecast.Hour> hours = new ArrayList<>(byTime.values());
        Forecast forecast = new Forecast();
        forecast.setHours(new ArrayList<>(hours.subList(0, Math.min(MAX_HOURS, hours.size()))));
        return forecast;
    }

    /** 위경도 → 기상청 격자(nx, ny). 기상청이 준 LCC 변환식 그대로 */
    static int[] toGrid(double lat, double lon) {
        double re = 6371.00877 / 5.0, d = Math.PI / 180, s1 = 30 * d, s2 = 60 * d, olon = 126 * d, olat = 38 * d;
        double sn = Math.log(Math.cos(s1) / Math.cos(s2)) / Math.log(Math.tan(Math.PI / 4 + s2 / 2) / Math.tan(Math.PI / 4 + s1 / 2));
        double sf = Math.pow(Math.tan(Math.PI / 4 + s1 / 2), sn) * Math.cos(s1) / sn;
        double ro = re * sf / Math.pow(Math.tan(Math.PI / 4 + olat / 2), sn);
        double ra = re * sf / Math.pow(Math.tan(Math.PI / 4 + lat * d / 2), sn);
        double theta = lon * d - olon;
        if (theta > Math.PI) {
            theta -= 2 * Math.PI;
        }
        if (theta < -Math.PI) {
            theta += 2 * Math.PI;
        }
        theta *= sn;
        return new int[] {(int) Math.floor(ra * Math.sin(theta) + 43 + 0.5), (int) Math.floor(ro - ra * Math.cos(theta) + 136 + 0.5)};
    }

    /** 가장 최근 발표 시각 [yyyyMMdd, HH00] (02·05·08·11·14·17·20·23시, 10분 뒤부터 제공) */
    static String[] baseTime(ZonedDateTime now) {
        int minutes = now.getHour() * 60 + now.getMinute();
        for (int hour : BASE_HOURS) {
            if (minutes >= hour * 60 + RELEASE_DELAY_MIN) {
                return new String[] {now.format(DateTimeFormatter.BASIC_ISO_DATE).substring(0, 8), String.format("%02d00", hour)};
            }
        }
        return new String[] {now.minusDays(1).format(DateTimeFormatter.BASIC_ISO_DATE).substring(0, 8), "2300"};
    }
}
