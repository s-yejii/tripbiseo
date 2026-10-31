package com.tripbiseo.common.external;

import static com.tripbiseo.common.exception.ApiException.fail;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.tripbiseo.common.exception.ApiException;
import com.tripbiseo.common.util.Geo;
import com.tripbiseo.common.util.TtlCache;

import tools.jackson.databind.JsonNode;

/**
 * 두 지점 사이 이동시간·경로선.
 *  대중교통 : TMAP 대중교통 → 실패하면 ODsay → 둘 다 안 되면 거리로 추정
 *  도보     : TMAP 보행자 → 추정 /  택시·자가용 : TMAP 자동차 → 추정
 * 같은 구간은 10분 동안 기억해 무료 호출 한도를 아낍니다.
 */
@Component
public class RouteClient {

    private static final Logger log = LoggerFactory.getLogger(RouteClient.class);
    private static final String TMAP = "https://apis.openapi.sk.com";
    private static final String ODSAY = "https://api.odsay.com/v1/api";
    private static final int WALK_ONLY_UNDER_M = 700;   // 이보다 가까우면 걸어감
    private static final int NO_API_UNDER_M = 50;       // 이보다 가까우면 API 없이 추정
    private static final long CACHE_MS = 10 * 60_000L;  // 같은 구간 기억 시간 10분
    private static final double ROAD_DETOUR = 1.3;      // 직선 거리 → 도로 거리
    private static final int WALK_M_PER_MIN = 75;       // 4.5km/h
    private static final int CAR_M_PER_MIN = 367;       // 22km/h
    private static final int CAR_EXTRA_MIN = 5;
    private static final int TRANSIT_M_PER_MIN = 283;   // 17km/h
    private static final int TRANSIT_WAIT_MIN = 8;

    private final ExternalApi externalApi;
    private final TtlCache cache = new TtlCache(800);

    @Value("${TMAP_APP_KEY:}")
    private String tmapKey;
    @Value("${ODSAY_API_KEY:}")
    private String odsayKey;

    public RouteClient(ExternalApi externalApi) {
        this.externalApi = externalApi;
    }

    /** 구간 수단: 고른 수단(walk/transit/taxi)이 있으면 그대로(taxi는 car), 없으면 700m 안은 도보, 그 밖은 여행 이동 수단으로 */
    public String chooseMode(String chosenMode, double meters, List<String> transports) {
        if (chosenMode != null) {
            return chosenMode.equals("taxi") ? "car" : chosenMode;
        }
        if (meters < WALK_ONLY_UNDER_M || transports.equals(List.of("walk"))) {
            return "walk";
        }
        boolean hasTransit = transports.contains("bus") || transports.contains("subway");
        boolean hasCar = transports.contains("car") || transports.contains("taxi");
        return !hasTransit && hasCar ? "car" : "transit";
    }

    /**
     * 두 지점 사이 길찾기. chosenMode: 사용자가 그 구간에 고른 수단(없으면 null).
     * isPrecise=false면 API 없이 거리로만 추정 (대안 후보를 많이 비교할 때)
     */
    public Route getRoute(double lat1, double lng1, double lat2, double lng2, String chosenMode, List<String> transports, boolean isPrecise) {
        double straight = Geo.distanceMeters(lat1, lng1, lat2, lng2);
        String mode = chooseMode(chosenMode, straight, transports);
        if (!isPrecise || straight < NO_API_UNDER_M) {
            return estimate(lat1, lng1, lat2, lng2, mode);
        }
        String key = String.format("%s:%.4f,%.4f>%.4f,%.4f", mode, lat1, lng1, lat2, lng2);
        return cache.get(key, CACHE_MS, () -> callRouteApi(lat1, lng1, lat2, lng2, mode));
    }

    /** API 없이 추정: 도로 우회 1.3배, 도보 4.5km/h, 자동차 22km/h+5분, 대중교통 17km/h+대기 8분 */
    public Route estimate(double lat1, double lng1, double lat2, double lng2, String mode) {
        double meters = Geo.distanceMeters(lat1, lng1, lat2, lng2) * ROAD_DETOUR;
        int minutes;
        if (mode.equals("walk")) {
            minutes = (int) Math.ceil(meters / WALK_M_PER_MIN);
        } else if (mode.equals("car")) {
            minutes = (int) Math.ceil(meters / CAR_M_PER_MIN + CAR_EXTRA_MIN);
        } else {
            minutes = (int) Math.ceil(meters / TRANSIT_M_PER_MIN + TRANSIT_WAIT_MIN);
        }
        int walkMeters = mode.equals("walk") ? (int) meters : 300;
        return route(Math.max(1, minutes), (int) meters, walkMeters, straight(lat1, lng1, lat2, lng2), mode, "estimate", null);
    }

    // 수단별 API를 부르고, 실패하면 거리로 추정
    private Route callRouteApi(double lat1, double lng1, double lat2, double lng2, String mode) {
        try {
            if (mode.equals("walk")) {
                return tmapFeatures("/tmap/routes/pedestrian?version=1&format=json", lat1, lng1, lat2, lng2, "walk");
            }
            if (mode.equals("car")) {
                return tmapFeatures("/tmap/routes?version=1&format=json", lat1, lng1, lat2, lng2, "car");
            }
            return transit(lat1, lng1, lat2, lng2);
        } catch (ApiException e) {
            log.info("길찾기 실패 → 거리로 추정: {}", e.getMessage());
            return estimate(lat1, lng1, lat2, lng2, mode);
        }
    }

    // TMAP 대중교통 → 실패하면 ODsay
    private Route transit(double lat1, double lng1, double lat2, double lng2) {
        if (!tmapKey.isBlank()) {
            try {
                return tmapTransit(lat1, lng1, lat2, lng2);
            } catch (ApiException e) {
                log.info("TMAP 대중교통 실패 → ODsay: {}", e.getMessage());
            }
        }
        return odsay(lat1, lng1, lat2, lng2);
    }

    private Route tmapTransit(double lat1, double lng1, double lat2, double lng2) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("startX", String.valueOf(lng1));
        body.put("startY", String.valueOf(lat1));
        body.put("endX", String.valueOf(lng2));
        body.put("endY", String.valueOf(lat2));
        body.put("count", 1);
        body.put("lang", 0);
        body.put("format", "json");
        JsonNode json = externalApi.postJson(TMAP + "/transit/routes", Map.of("appKey", tmapKey), body, "TMAP 대중교통");
        JsonNode itinerary = json.path("metaData").path("plan").path("itineraries").path(0);
        if (itinerary.isMissingNode()) {
            throw fail(502, "TMAP 대중교통 결과 없음");
        }
        List<double[]> coords = new ArrayList<>();
        List<String> rides = new ArrayList<>();
        for (JsonNode leg : itinerary.path("legs")) {
            coords.addAll(tmapLegLine(leg));
            String legMode = leg.path("mode").asString("");
            if (!legMode.equals("WALK")) {
                int stopCount = leg.path("passStopList").path("stationList").size() - 1;
                rides.add(ride(legMode.equals("BUS"), leg.path("route").asString(""), leg.path("start").path("name").asString(""),
                        leg.path("end").path("name").asString(""), stopCount));
            }
        }
        return route((int) Math.round(itinerary.path("totalTime").asDouble(0) / 60), itinerary.path("totalDistance").asInt(0),
                itinerary.path("totalWalkDistance").asInt(0), lineOrStraight(coords, lat1, lng1, lat2, lng2), "transit", "tmap", String.join(" · ", rides));
    }

    // TMAP 대중교통 한 구간의 선: passShape가 있으면 그것, 없으면 걷기 steps를 이어 붙임
    private List<double[]> tmapLegLine(JsonNode leg) {
        List<double[]> part = parseLine(leg.path("passShape").path("linestring").asString(null));
        if (part.isEmpty()) {
            for (JsonNode step : leg.path("steps")) {
                part.addAll(parseLine(step.path("linestring").asString(null)));
            }
        }
        return part;
    }

    private Route odsay(double lat1, double lng1, double lat2, double lng2) {
        if (odsayKey.isBlank()) {
            throw fail(503, "ODsay 키 없음");
        }
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("SX", lng1);
        params.put("SY", lat1);
        params.put("EX", lng2);
        params.put("EY", lat2);
        params.put("apiKey", odsayKey);
        JsonNode json = externalApi.getJson(externalApi.buildUrl(ODSAY + "/searchPubTransPathT", params), Map.of(), "ODsay");
        JsonNode path = json.path("result").path("path").path(0);
        if (path.isMissingNode()) {
            throw fail(502, "ODsay 결과 없음");
        }
        List<String> rides = new ArrayList<>(); // trafficType 1 지하철, 2 버스, 3 걷기
        for (JsonNode sub : path.path("subPath")) {
            int type = sub.path("trafficType").asInt(0);
            if (type != 3) {
                String routeName = sub.path("lane").path(0).path(type == 2 ? "busNo" : "name").asString("");
                rides.add(ride(type == 2, routeName, sub.path("startName").asString(""), sub.path("endName").asString(""),
                        sub.path("stationCount").asInt(0)));
            }
        }
        JsonNode info = path.path("info");
        List<double[]> coords = odsayLine(info.path("mapObj").asString(""));
        return route(info.path("totalTime").asInt(0), info.path("totalDistance").asInt(0), info.path("totalWalk").asInt(0),
                lineOrStraight(coords, lat1, lng1, lat2, lng2), "transit", "odsay", String.join(" · ", rides));
    }

    // ODsay 노선 모양(loadLane). 실패하면 빈 목록 → 직선으로 그림
    private List<double[]> odsayLine(String mapObj) {
        List<double[]> coords = new ArrayList<>();
        try {
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("mapObject", "0:0@" + mapObj);
            params.put("apiKey", odsayKey);
            JsonNode lane = externalApi.getJson(externalApi.buildUrl(ODSAY + "/loadLane", params), Map.of(), "ODsay");
            for (JsonNode oneLane : lane.path("result").path("lane")) {
                for (JsonNode section : oneLane.path("section")) {
                    for (JsonNode point : section.path("graphPos")) {
                        coords.add(new double[] {point.path("y").asDouble(0), point.path("x").asDouble(0)});
                    }
                }
            }
        } catch (ApiException ignored) {
            // 경로선만 없음
        }
        return coords;
    }

    // TMAP 보행자·자동차 (GeoJSON features)
    private Route tmapFeatures(String path, double lat1, double lng1, double lat2, double lng2, String mode) {
        if (tmapKey.isBlank()) {
            throw fail(503, "TMAP 키 없음");
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("startX", String.valueOf(lng1));
        body.put("startY", String.valueOf(lat1));
        body.put("endX", String.valueOf(lng2));
        body.put("endY", String.valueOf(lat2));
        body.put("startName", "%EC%B6%9C%EB%B0%9C"); // "출발"
        body.put("endName", "%EB%8F%84%EC%B0%A9");   // "도착"
        body.put("reqCoordType", "WGS84GEO");
        body.put("resCoordType", "WGS84GEO");
        JsonNode json = externalApi.postJson(TMAP + path, Map.of("appKey", tmapKey), body, "TMAP");
        List<double[]> coords = new ArrayList<>();
        JsonNode summary = null;
        for (JsonNode feature : json.path("features")) {
            if (summary == null && feature.path("properties").has("totalTime")) {
                summary = feature.path("properties");
            }
            if (feature.path("geometry").path("type").asString("").equals("LineString")) {
                for (JsonNode p : feature.path("geometry").path("coordinates")) {
                    coords.add(new double[] {p.path(1).asDouble(0), p.path(0).asDouble(0)});
                }
            }
        }
        if (summary == null) {
            throw fail(502, "TMAP 결과 없음");
        }
        int meters = summary.path("totalDistance").asInt(0);
        int minutes = Math.max(1, (int) Math.round(summary.path("totalTime").asDouble(0) / 60));
        return route(minutes, meters, mode.equals("walk") ? meters : 0, lineOrStraight(coords, lat1, lng1, lat2, lng2), mode, "tmap", null);
    }

    /** "버스 6513 구로디지털단지→대림 3정거장" */
    private static String ride(boolean isBus, String routeName, String from, String to, int stopCount) {
        return (isBus ? "버스 " : "") + routeName + " " + from + "→" + to + " " + stopCount + "정거장";
    }

    private static Route route(int minutes, int meters, int walkMeters, List<double[]> coords, String mode, String source, String detail) {
        Route r = new Route();
        r.setMinutes(minutes);
        r.setMeters(meters);
        r.setWalkMeters(walkMeters);
        r.setCoords(coords);
        r.setMode(mode);
        r.setSource(source);
        r.setDetail(detail);
        return r;
    }

    private static List<double[]> lineOrStraight(List<double[]> coords, double lat1, double lng1, double lat2, double lng2) {
        return coords.size() > 1 ? coords : straight(lat1, lng1, lat2, lng2);
    }

    private static List<double[]> straight(double lat1, double lng1, double lat2, double lng2) {
        List<double[]> line = new ArrayList<>();
        line.add(new double[] {lat1, lng1});
        line.add(new double[] {lat2, lng2});
        return line;
    }

    /** TMAP 선 글자 "경도,위도 경도,위도 …" → [[위도, 경도]] */
    private static List<double[]> parseLine(String text) {
        List<double[]> out = new ArrayList<>();
        if (text == null) {
            return out;
        }
        for (String pair : text.trim().split("\\s+")) {
            String[] xy = pair.split(",");
            if (xy.length != 2) {
                continue;
            }
            try {
                out.add(new double[] {Double.parseDouble(xy[1]), Double.parseDouble(xy[0])});
            } catch (NumberFormatException ignored) {
                // 잘못된 점은 건너뜀
            }
        }
        return out;
    }
}
