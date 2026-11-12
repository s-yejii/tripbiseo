package com.tripbiseo.common.external;

import static com.tripbiseo.common.exception.ApiException.fail;

import java.time.ZonedDateTime;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
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
 * 기상청 단기예보를 날짜별로 받는 곳 (11/06 ③, 여행 날짜 날씨·옷차림용).
 * WeatherClient(④ 여행 중, 오늘 18시간)와 같은 API를 쓰지만, 이쪽은 3일 뒤까지 전부 + 습도(REH)·하늘(SKY)·최저/최고(TMN/TMX)까지 받아요.
 * 격자 변환·발표 시각 계산은 WeatherClient 것을 그대로 씀 (같은 패키지).
 */
@Component
public class DayForecastClient {

    private static final String URL = "https://apis.data.go.kr/1360000/VilageFcstInfoService_2.0/getVilageFcst";
    private static final long CACHE_MS = 60 * 60_000L; // 같은 격자·발표 시각은 1시간 기억 (발표는 3시간마다)
    private static final int ROWS = 1500;              // 3일치 × 12종류 × 시간대가 한 번에 들어오게
    private static final List<String> CATEGORIES = List.of("TMP", "TMN", "TMX", "REH", "SKY", "POP", "PTY");

    private final ExternalApi externalApi;
    private final TtlCache cache = new TtlCache(200);

    @Value("${KMA_SERVICE_KEY:}")
    private String serviceKey;

    public DayForecastClient(ExternalApi externalApi) {
        this.externalApi = externalApi;
    }

    /** 이 좌표의 시간대별 예보 (지금부터 3일 뒤까지). 키가 없으면 503, 기상청 오류는 502 */
    public List<ForecastHour> getHourList(double lat, double lng) {
        if (serviceKey.isBlank()) {
            throw fail(503, "기상청 키가 없어요.");
        }
        // Encoding 키(%가 들어 있음)를 넣었으면 Decoding 키로 바꿈 (주소를 만들 때 다시 인코딩하므로)
        String key = serviceKey.contains("%") ? URLDecoder.decode(serviceKey, StandardCharsets.UTF_8) : serviceKey;
        int[] grid = WeatherClient.toGrid(lat, lng);
        String[] base = WeatherClient.baseTime(ZonedDateTime.now(Geo.KST)); // 기상청 발표 시각은 진짜 지금 기준 (시연 시계가 미래면 기상청이 거절해서, 11/06)
        String cacheKey = "kma-day:" + grid[0] + "," + grid[1] + base[0] + base[1];
        return cache.get(cacheKey, CACHE_MS, () -> callForecast(key, grid, base));
    }

    private List<ForecastHour> callForecast(String key, int[] grid, String[] base) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("serviceKey", key);
        params.put("pageNo", 1);
        params.put("numOfRows", ROWS);
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
        return toHourList(json.path("response").path("body").path("items").path("item"));
    }

    // 항목 여러 줄(시각 × 종류)을 시각별 한 줄로 묶음. TreeMap이라 날짜·시각 순으로 정렬됨
    private List<ForecastHour> toHourList(JsonNode items) {
        Map<String, ForecastHour> byTime = new TreeMap<>();
        for (JsonNode item : items) {
            String category = item.path("category").asString("");
            if (!CATEGORIES.contains(category)) {
                continue;
            }
            String date = item.path("fcstDate").asString("");
            String time = item.path("fcstTime").asString("");
            ForecastHour hour = byTime.get(date + time);
            if (hour == null) {
                hour = new ForecastHour();
                hour.setDate(date);
                hour.setHour(Integer.parseInt(time.substring(0, 2)));
                byTime.put(date + time, hour);
            }
            setValue(hour, category, item.path("fcstValue").asString(""));
        }
        return new ArrayList<>(byTime.values());
    }

    private void setValue(ForecastHour hour, String category, String value) {
        Double number = toNumber(value);
        switch (category) {
            case "TMP" -> hour.setTmp(number);
            case "TMN" -> hour.setTmn(number);
            case "TMX" -> hour.setTmx(number);
            case "REH" -> hour.setReh(number);
            case "SKY" -> hour.setSky(number == null ? null : number.intValue());
            case "POP" -> hour.setPop(number == null ? null : number.intValue());
            default -> hour.setPty(number == null ? null : number.intValue()); // PTY
        }
    }

    // 기상청 값은 글자("12", "-0.5")로 와요. 숫자가 아니면 null
    private static Double toNumber(String value) {
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
