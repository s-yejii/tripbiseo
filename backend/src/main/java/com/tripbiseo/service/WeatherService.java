package com.tripbiseo.service;

import static com.tripbiseo.common.ApiException.fail;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.springframework.stereotype.Service;

import com.tripbiseo.common.Geo;
import com.tripbiseo.common.Http;
import com.tripbiseo.common.Json;
import com.tripbiseo.common.Maps;
import com.tripbiseo.common.TtlCache;

/**
 * 기상청 단기예보 (공공데이터포털). 제미나이의 get_weather 도구가 이걸 부릅니다.
 * 결과: 오늘 남은 시간대별 강수확률(pop)·강수형태(pty)·기온(tmp)
 */
@Service
public class WeatherService {

    private static final String URL = "https://apis.data.go.kr/1360000/VilageFcstInfoService_2.0/getVilageFcst";
    private final ApiKeyService keys;
    private final TtlCache cache = new TtlCache(200);

    public WeatherService(ApiKeyService keys) {
        this.keys = keys;
    }

    public Map<String, Object> forecast(double lat, double lng) {
        String key = keys.get("KMA_SERVICE_KEY");
        if (key == null) throw fail(503, "기상청 키가 없어요.");
        if (key.contains("%")) key = URLDecoder.decode(key, StandardCharsets.UTF_8); // Encoding 키를 넣은 경우
        int[] g = toGrid(lat, lng);
        String[] base = baseTime(Geo.now());
        String serviceKey = key;
        return cache.get("kma:" + g[0] + "," + g[1] + base[0] + base[1], 30 * 60000L, () -> {
            Object json = Http.getJson(Http.url(URL, Http.params("serviceKey", serviceKey, "pageNo", 1, "numOfRows", 400, "dataType", "JSON",
                    "base_date", base[0], "base_time", base[1], "nx", g[0], "ny", g[1])), null, 8000, "기상청");
            String code = Json.str(json, "response", "header", "resultCode");
            if (code != null && !"00".equals(code)) throw fail(502, "기상청 오류: " + Json.str(json, "response", "header", "resultMsg"));
            Map<String, Map<String, Object>> byTime = new TreeMap<>();
            for (Object it : Json.list(json, "response", "body", "items", "item")) {
                String cat = Json.str(it, "category");
                if (!List.of("POP", "PTY", "TMP").contains(cat)) continue;
                String t = Json.str(it, "fcstDate") + " " + Json.str(it, "fcstTime");
                byTime.computeIfAbsent(t, k -> Maps.of("time", k.substring(9, 11) + ":00", "date", k.substring(0, 8))).put(cat.toLowerCase(), Json.str(it, "fcstValue"));
            }
            List<Map<String, Object>> hours = new ArrayList<>(byTime.values());
            return Maps.of("source", "기상청 단기예보", "hours", hours.subList(0, Math.min(18, hours.size())),
                    "note", "pop=강수확률(%), pty=강수형태(0 없음 1 비 2 비/눈 3 눈 4 소나기), tmp=기온(℃)");
        });
    }

    /** 위경도 → 기상청 격자(nx, ny), 기상청 제공 LCC 변환식 */
    static int[] toGrid(double lat, double lon) {
        double re = 6371.00877 / 5.0, d = Math.PI / 180, s1 = 30 * d, s2 = 60 * d, olon = 126 * d, olat = 38 * d;
        double sn = Math.log(Math.cos(s1) / Math.cos(s2)) / Math.log(Math.tan(Math.PI / 4 + s2 / 2) / Math.tan(Math.PI / 4 + s1 / 2));
        double sf = Math.pow(Math.tan(Math.PI / 4 + s1 / 2), sn) * Math.cos(s1) / sn;
        double ro = re * sf / Math.pow(Math.tan(Math.PI / 4 + olat / 2), sn);
        double ra = re * sf / Math.pow(Math.tan(Math.PI / 4 + lat * d / 2), sn);
        double theta = lon * d - olon;
        if (theta > Math.PI) theta -= 2 * Math.PI;
        if (theta < -Math.PI) theta += 2 * Math.PI;
        theta *= sn;
        return new int[] {(int) Math.floor(ra * Math.sin(theta) + 43 + 0.5), (int) Math.floor(ro - ra * Math.cos(theta) + 136 + 0.5)};
    }

    /** 발표 시각(02·05·08·11·14·17·20·23시, 10분 뒤부터 제공) */
    static String[] baseTime(ZonedDateTime now) {
        int m = now.getHour() * 60 + now.getMinute();
        for (int h : new int[] {23, 20, 17, 14, 11, 8, 5, 2})
            if (m >= h * 60 + 10) return new String[] {now.format(DateTimeFormatter.BASIC_ISO_DATE).substring(0, 8), String.format("%02d00", h)};
        return new String[] {now.minusDays(1).format(DateTimeFormatter.BASIC_ISO_DATE).substring(0, 8), "2300"};
    }
}
