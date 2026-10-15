package com.tripbiseo.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.tripbiseo.common.ApiException;
import com.tripbiseo.common.Geo;
import com.tripbiseo.common.Http;
import com.tripbiseo.common.Json;
import com.tripbiseo.common.Maps;
import com.tripbiseo.common.TtlCache;

/**
 * 두 지점 사이 이동시간·경로선.
 *  대중교통 : TMAP 대중교통 → 실패하면 ODsay → 둘 다 안 되면 거리로 추정
 *  도보     : TMAP 보행자 → 추정 /  택시·자가용 : TMAP 자동차 → 추정
 * 같은 구간은 10분 동안 기억해 무료 호출 한도를 아낍니다.
 */
@Service
public class RouteService {

    private static final Logger log = LoggerFactory.getLogger(RouteService.class);
    private static final String TMAP = "https://apis.openapi.sk.com";
    private static final String ODSAY = "https://api.odsay.com/v1/api";

    /** minutes: 이동 분, meters: 거리, walkMeters: 걷는 거리, coords: [[위도, 경도]...], mode: walk/transit/car, source: tmap/odsay/estimate, detail: 탈 노선 */
    public record Leg(int minutes, int meters, int walkMeters, List<double[]> coords, String mode, String source, String detail) {
        public Map<String, Object> toMap() {
            return Maps.of("minutes", minutes, "meters", meters, "mode", mode, "source", source, "detail", detail, "coords", coords);
        }
    }

    private final ApiKeyService keys;
    private final TtlCache cache = new TtlCache(800);

    public RouteService(ApiKeyService keys) {
        this.keys = keys;
    }

    /** 구간 수단: 고른 수단(walk/transit/taxi)이 있으면 그대로, 없으면 700m 안은 도보, 그 밖은 여행 이동 수단으로 */
    public static String mode(String chosen, double meters, List<String> transports) {
        if (chosen != null) return chosen.equals("taxi") ? "car" : chosen;
        if (meters < 700 || transports.equals(List.of("walk"))) return "walk";
        return !transports.contains("bus") && !transports.contains("subway") && (transports.contains("car") || transports.contains("taxi")) ? "car" : "transit";
    }

    public Leg leg(double lat1, double lng1, double lat2, double lng2, List<String> transports, boolean precise) {
        return leg(lat1, lng1, lat2, lng2, null, transports, precise);
    }

    /** chosen: 사용자가 그 구간에 고른 수단(없으면 null). precise=false면 API 없이 거리로만 추정 (대안 후보를 많이 비교할 때) */
    public Leg leg(double lat1, double lng1, double lat2, double lng2, String chosen, List<String> transports, boolean precise) {
        double straight = Geo.meters(lat1, lng1, lat2, lng2);
        String mode = mode(chosen, straight, transports);
        if (!precise || straight < 50) return estimate(lat1, lng1, lat2, lng2, mode);
        String key = String.format("%s:%.4f,%.4f>%.4f,%.4f", mode, lat1, lng1, lat2, lng2);
        return cache.get(key, 10 * 60000L, () -> {
            try {
                return switch (mode) {
                    case "walk" -> tmapFeatures("/tmap/routes/pedestrian?version=1&format=json", lat1, lng1, lat2, lng2, "walk");
                    case "car" -> tmapFeatures("/tmap/routes?version=1&format=json", lat1, lng1, lat2, lng2, "car");
                    default -> transit(lat1, lng1, lat2, lng2);
                };
            } catch (ApiException e) {
                log.info("길찾기 실패 → 거리로 추정: {}", e.getMessage());
                return estimate(lat1, lng1, lat2, lng2, mode);
            }
        });
    }

    // TMAP 대중교통 → 실패하면 ODsay
    private Leg transit(double lat1, double lng1, double lat2, double lng2) {
        if (keys.has("TMAP_APP_KEY")) {
            try {
                Object json = Http.postJson(TMAP + "/transit/routes", Map.of("appKey", keys.get("TMAP_APP_KEY")),
                        Maps.of("startX", String.valueOf(lng1), "startY", String.valueOf(lat1), "endX", String.valueOf(lng2), "endY", String.valueOf(lat2),
                                "count", 1, "lang", 0, "format", "json"), 8000, "TMAP 대중교통");
                Map<String, Object> it = Json.map(json, "metaData", "plan", "itineraries", 0);
                if (it == null) throw ApiException.fail(502, "TMAP 대중교통 결과 없음");
                List<double[]> coords = new ArrayList<>();
                List<String> rides = new ArrayList<>();
                for (Object leg : Json.list(it, "legs")) {
                    List<double[]> part = line(Json.str(leg, "passShape", "linestring"));
                    if (part.isEmpty()) for (Object st : Json.list(leg, "steps")) part.addAll(line(Json.str(st, "linestring")));
                    coords.addAll(part);
                    if (!"WALK".equals(Json.str(leg, "mode")))
                        rides.add(ride("BUS".equals(Json.str(leg, "mode")), Json.str(leg, "route"), Json.str(leg, "start", "name"), Json.str(leg, "end", "name"),
                                Json.list(leg, "passStopList", "stationList").size() - 1));
                }
                return new Leg(round(Json.num(it, "totalTime") / 60), round(Json.num(it, "totalDistance")), round(Json.num(it, "totalWalkDistance")),
                        coords.size() > 1 ? coords : straight(lat1, lng1, lat2, lng2), "transit", "tmap", String.join(" · ", rides));
            } catch (ApiException e) {
                log.info("TMAP 대중교통 실패 → ODsay: {}", e.getMessage());
            }
        }
        return odsay(lat1, lng1, lat2, lng2);
    }

    private Leg odsay(double lat1, double lng1, double lat2, double lng2) {
        String key = keys.get("ODSAY_API_KEY");
        if (key == null) throw ApiException.fail(503, "ODsay 키 없음");
        Object json = Http.getJson(Http.url(ODSAY + "/searchPubTransPathT", Http.params("SX", lng1, "SY", lat1, "EX", lng2, "EY", lat2, "apiKey", key)), null, 8000, "ODsay");
        Map<String, Object> path = Json.map(json, "result", "path", 0);
        if (path == null) throw ApiException.fail(502, "ODsay 결과 없음");
        List<double[]> coords = new ArrayList<>();
        try { // 노선 모양(loadLane). 실패하면 직선
            Object lane = Http.getJson(Http.url(ODSAY + "/loadLane", Http.params("mapObject", "0:0@" + Json.str(path, "info", "mapObj"), "apiKey", key)), null, 8000, "ODsay");
            for (Object l : Json.list(lane, "result", "lane"))
                for (Object sec : Json.list(l, "section"))
                    for (Object p : Json.list(sec, "graphPos")) coords.add(new double[] {Json.num(p, "y"), Json.num(p, "x")});
        } catch (ApiException ignored) {
            // 경로선만 없음
        }
        List<String> rides = new ArrayList<>(); // trafficType 1 지하철, 2 버스, 3 걷기
        for (Object sp : Json.list(path, "subPath"))
            if (round(Json.num(sp, "trafficType")) != 3)
                rides.add(ride(round(Json.num(sp, "trafficType")) == 2, Json.str(sp, "lane", 0, round(Json.num(sp, "trafficType")) == 2 ? "busNo" : "name"),
                        Json.str(sp, "startName"), Json.str(sp, "endName"), round(Json.num(sp, "stationCount"))));
        return new Leg(round(Json.num(path, "info", "totalTime")), round(Json.num(path, "info", "totalDistance")), round(Json.num(path, "info", "totalWalk")),
                coords.size() > 1 ? coords : straight(lat1, lng1, lat2, lng2), "transit", "odsay", String.join(" · ", rides));
    }

    // TMAP 보행자·자동차 (GeoJSON features)
    private Leg tmapFeatures(String path, double lat1, double lng1, double lat2, double lng2, String mode) {
        String key = keys.get("TMAP_APP_KEY");
        if (key == null) throw ApiException.fail(503, "TMAP 키 없음");
        Object json = Http.postJson(TMAP + path, Map.of("appKey", key), Maps.of("startX", String.valueOf(lng1), "startY", String.valueOf(lat1),
                "endX", String.valueOf(lng2), "endY", String.valueOf(lat2), "startName", "%EC%B6%9C%EB%B0%9C", "endName", "%EB%8F%84%EC%B0%A9",
                "reqCoordType", "WGS84GEO", "resCoordType", "WGS84GEO"), 8000, "TMAP");
        List<double[]> coords = new ArrayList<>();
        Double sec = null, dist = null;
        for (Object f : Json.list(json, "features")) {
            if (sec == null && Json.num(f, "properties", "totalTime") != null) {
                sec = Json.num(f, "properties", "totalTime");
                dist = Json.num(f, "properties", "totalDistance");
            }
            if ("LineString".equals(Json.str(f, "geometry", "type")))
                for (Object p : Json.list(f, "geometry", "coordinates")) coords.add(new double[] {Json.num(p, 1), Json.num(p, 0)});
        }
        if (sec == null) throw ApiException.fail(502, "TMAP 결과 없음");
        int m = round(dist);
        return new Leg(Math.max(1, round(sec / 60)), m, mode.equals("walk") ? m : 0, coords.size() > 1 ? coords : straight(lat1, lng1, lat2, lng2), mode, "tmap", null);
    }

    /** API 없이 추정: 도로 우회 1.3배, 도보 4.5km/h, 자동차 22km/h+5분, 대중교통 17km/h+대기 8분 */
    public Leg estimate(double lat1, double lng1, double lat2, double lng2, String mode) {
        double m = Geo.meters(lat1, lng1, lat2, lng2) * 1.3;
        int minutes = switch (mode) {
            case "walk" -> (int) Math.ceil(m / 75);
            case "car" -> (int) Math.ceil(m / 367 + 5);
            default -> (int) Math.ceil(m / 283 + 8);
        };
        return new Leg(Math.max(1, minutes), (int) m, mode.equals("walk") ? (int) m : 300, straight(lat1, lng1, lat2, lng2), mode, "estimate", null);
    }

    /** "버스 6513 구로디지털단지→대림 3정거장" */
    private static String ride(boolean bus, String route, String from, String to, int stops) {
        return (bus ? "버스 " : "") + route + " " + from + "→" + to + " " + stops + "정거장";
    }

    private static List<double[]> straight(double lat1, double lng1, double lat2, double lng2) {
        return List.of(new double[] {lat1, lng1}, new double[] {lat2, lng2});
    }

    /** TMAP 선 글자 "경도,위도 경도,위도 …" → [[위도, 경도]] */
    private static List<double[]> line(String text) {
        List<double[]> out = new ArrayList<>();
        if (text == null) return out;
        for (String pair : text.trim().split("\\s+")) {
            String[] xy = pair.split(",");
            if (xy.length == 2) {
                try {
                    out.add(new double[] {Double.parseDouble(xy[1]), Double.parseDouble(xy[0])});
                } catch (NumberFormatException ignored) {
                    // 잘못된 점은 건너뜀
                }
            }
        }
        return out;
    }

    private static int round(Double v) {
        return v == null ? 0 : (int) Math.round(v);
    }
}
