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
import com.tripbiseo.common.util.TtlCache;

import tools.jackson.databind.JsonNode;

/**
 * 길 안내 (⑤ 길 안내 화면): 경로선 + 단계별 안내(어디서 꺾는지, 몇 번 버스를 어디서 타고 내리는지).
 * RouteClient는 몇 분 걸리는지만 필요해서 안내 문장을 버려요. 안내까지 필요한 곳은 이 클래스를 써요.
 *   도보 TMAP 보행자 · 택시 TMAP 자동차(예상 택시비) · 버스/지하철 TMAP 대중교통 → 실패하면 ODsay
 * 못 찾으면 예외 대신 available=false와 이유를 돌려줘요 (화면이 "이 수단은 지금 안내할 수 없어요"를 보여 줌)
 */
@Component // Spring이 객체를 하나 만들어 두고, 필요한 곳에 생성자 주입으로 넣어 줌
public class RouteGuideClient {

    private static final Logger log = LoggerFactory.getLogger(RouteGuideClient.class);
    private static final String TMAP = "https://apis.openapi.sk.com";
    private static final String ODSAY = "https://api.odsay.com/v1/api";
    private static final long CACHE_MS = 60_000L;       // 같은 출발·도착·수단은 1분 동안 다시 안 부름
    private static final int TRANSIT_COUNT = 10;        // 대중교통 경로 후보 수 (버스만·지하철 포함을 고르려고 여러 개 받음)
    private static final int PATH_SUBWAY = 1;           // 경로 종류(TMAP·ODsay 같음): 1 지하철, 2 버스, 3 버스+지하철
    private static final int PATH_BUS = 2;
    private static final int PATH_MIXED = 3;
    private static final int SECONDS_PER_MIN = 60;

    private final ExternalApi externalApi;
    private final TtlCache cache = new TtlCache(300);

    @Value("${TMAP_APP_KEY:}")
    private String tmapKey;
    @Value("${ODSAY_API_KEY:}")
    private String odsayKey;

    public RouteGuideClient(ExternalApi externalApi) {
        this.externalApi = externalApi;
    }

    /** 길 안내. mode: walk / bus / subway / car */
    public RouteGuide getGuide(double lat1, double lng1, double lat2, double lng2, String mode) {
        String key = String.format("%s:%.5f,%.5f>%.5f,%.5f", mode, lat1, lng1, lat2, lng2);
        return cache.get(key, CACHE_MS, () -> findGuide(lat1, lng1, lat2, lng2, mode));
    }

    private RouteGuide findGuide(double lat1, double lng1, double lat2, double lng2, String mode) {
        try {
            if (mode.equals("walk")) {
                return tmapFeatures("/tmap/routes/pedestrian?version=1&format=json", lat1, lng1, lat2, lng2, "walk");
            }
            if (mode.equals("car")) {
                return tmapFeatures("/tmap/routes?version=1&format=json", lat1, lng1, lat2, lng2, "car");
            }
            return transit(lat1, lng1, lat2, lng2, mode);
        } catch (ApiException e) {
            log.info("길 안내 실패 ({}): {}", mode, e.getMessage());
            return unavailable(mode, e.getMessage());
        }
    }

    // ---------------------------------------------------------------- 도보·택시 (TMAP GeoJSON)

    private RouteGuide tmapFeatures(String path, double lat1, double lng1, double lat2, double lng2, String mode) {
        if (tmapKey.isBlank()) {
            throw fail(503, "TMAP 키(TMAP_APP_KEY)가 없어요");
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
        JsonNode json = externalApi.postJson(TMAP + path, Map.of("appKey", tmapKey), body, "TMAP 길 안내");

        RouteGuide guide = newGuide(mode, "tmap");
        JsonNode summary = null;
        for (JsonNode feature : json.path("features")) {
            JsonNode properties = feature.path("properties");
            JsonNode geometry = feature.path("geometry");
            if (summary == null && properties.has("totalTime")) {
                summary = properties;
            }
            if (geometry.path("type").asString("").equals("LineString")) {
                for (JsonNode point : geometry.path("coordinates")) {
                    guide.getCoords().add(new double[] {point.path(1).asDouble(0), point.path(0).asDouble(0)});
                }
            } else {
                addPointStep(guide, geometry, properties);
            }
        }
        if (summary == null) {
            throw fail(502, "TMAP 길 안내 결과가 없어요");
        }
        guide.setMeters(summary.path("totalDistance").asInt(0));
        guide.setMinutes(Math.max(1, (int) Math.round(summary.path("totalTime").asDouble(0) / SECONDS_PER_MIN)));
        if (mode.equals("car") && summary.has("taxiFare")) {
            guide.setFare(summary.path("taxiFare").asInt(0));
        }
        return guide;
    }

    /** TMAP 안내 지점(Point) 하나 → 단계 하나 (안내 문장이 없으면 건너뜀) */
    private void addPointStep(RouteGuide guide, JsonNode geometry, JsonNode properties) {
        String description = properties.path("description").asString("");
        if (description.isBlank()) {
            return;
        }
        JsonNode xy = geometry.path("coordinates");
        GuideStep step = new GuideStep();
        step.setLat(xy.path(1).asDouble(0));
        step.setLng(xy.path(0).asDouble(0));
        step.setKind(toTurnKind(properties.path("turnType").asInt(0)));
        step.setText(description);
        guide.getSteps().add(step);
    }

    /** TMAP turnType → 안내 종류 (200 출발, 201 도착, 12·16·17 왼쪽, 13·18·19 오른쪽, 211~217 횡단보도, 125~128 육교·지하도·계단) */
    private String toTurnKind(int turnType) {
        if (turnType == 200) {
            return "start";
        }
        if (turnType == 201) {
            return "end";
        }
        if (turnType == 12 || turnType == 16 || turnType == 17) {
            return "left";
        }
        if (turnType == 13 || turnType == 18 || turnType == 19) {
            return "right";
        }
        if (turnType >= 211 && turnType <= 217) {
            return "crosswalk";
        }
        if (turnType >= 125 && turnType <= 128) {
            return "stairs";
        }
        return "straight";
    }

    // ---------------------------------------------------------------- 버스·지하철

    /** TMAP 대중교통 → 안 되면 ODsay. 둘 다 안 되면 두 이유를 같이 알려 줌 */
    private RouteGuide transit(double lat1, double lng1, double lat2, double lng2, String mode) {
        String tmapReason;
        try {
            return tmapTransit(lat1, lng1, lat2, lng2, mode);
        } catch (ApiException e) {
            tmapReason = e.getMessage();
        }
        try {
            return odsayTransit(lat1, lng1, lat2, lng2, mode);
        } catch (ApiException e) {
            throw fail(502, "대중교통 길 안내를 못 받았어요 (TMAP: " + tmapReason + " / ODsay: " + e.getMessage() + ")");
        }
    }

    private RouteGuide tmapTransit(double lat1, double lng1, double lat2, double lng2, String mode) {
        if (tmapKey.isBlank()) {
            throw fail(503, "TMAP 키 없음");
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("startX", String.valueOf(lng1));
        body.put("startY", String.valueOf(lat1));
        body.put("endX", String.valueOf(lng2));
        body.put("endY", String.valueOf(lat2));
        body.put("count", TRANSIT_COUNT);
        body.put("lang", 0);
        body.put("format", "json");
        JsonNode json = externalApi.postJson(TMAP + "/transit/routes", Map.of("appKey", tmapKey), body, "TMAP 대중교통");
        JsonNode itinerary = pickPath(json.path("metaData").path("plan").path("itineraries"), "pathType", mode);
        if (itinerary == null) {
            throw fail(404, mode.equals("bus") ? "버스로만 가는 길이 없어요" : "지하철로 가는 길이 없어요");
        }
        RouteGuide guide = newGuide(mode, "tmap");
        guide.setMinutes(Math.max(1, (int) Math.round(itinerary.path("totalTime").asDouble(0) / SECONDS_PER_MIN)));
        guide.setMeters(itinerary.path("totalDistance").asInt(0));
        if (itinerary.path("fare").path("regular").has("totalFare")) {
            guide.setFare(itinerary.path("fare").path("regular").path("totalFare").asInt(0));
        }
        for (JsonNode leg : itinerary.path("legs")) {
            addTmapLeg(guide, leg);
        }
        addEndStep(guide, lat2, lng2);
        return guide;
    }

    /** TMAP 대중교통 구간 하나: 걷기 / 타기 + 내리기, 경로선 */
    private void addTmapLeg(RouteGuide guide, JsonNode leg) {
        String legMode = leg.path("mode").asString("");
        JsonNode start = leg.path("start");
        String startName = start.path("name").asString("");
        String endName = leg.path("end").path("name").asString("");
        int minutes = (int) Math.round(leg.path("sectionTime").asDouble(0) / SECONDS_PER_MIN);
        guide.getCoords().addAll(tmapLegLine(leg));
        if (legMode.equals("WALK")) {
            String target = endName.isBlank() || endName.equals("도착지") ? "도착지" : endName;
            guide.getSteps().add(step(start.path("lat").asDouble(0), start.path("lon").asDouble(0), "walk",
                    target + "까지 걷기", minutes + "분 · " + leg.path("distance").asInt(0) + "m", false));
            return;
        }
        boolean isBus = legMode.equals("BUS");
        int stopCount = Math.max(1, leg.path("passStopList").path("stationList").size() - 1);
        String routeName = shortRouteName(leg.path("route").asString(""));
        guide.getSteps().add(step(start.path("lat").asDouble(0), start.path("lon").asDouble(0), isBus ? "bus" : "subway",
                (isBus ? "버스 " : "") + routeName + " 타기", startName + " → " + endName + " · " + stopCount + "정거장 · " + minutes + "분", true));
        JsonNode end = leg.path("end");
        guide.getSteps().add(step(end.path("lat").asDouble(0), end.path("lon").asDouble(0), "getoff", endName + "에서 내리기", null, false));
    }

    /** "간선:470" → "470" */
    private String shortRouteName(String routeName) {
        int colon = routeName.indexOf(':');
        return colon >= 0 ? routeName.substring(colon + 1) : routeName;
    }

    /** TMAP 대중교통 구간의 선: passShape가 있으면 그것, 없으면 걷기 steps를 이어 붙임 */
    private List<double[]> tmapLegLine(JsonNode leg) {
        List<double[]> part = parseLine(leg.path("passShape").path("linestring").asString(null));
        if (part.isEmpty()) {
            for (JsonNode walkStep : leg.path("steps")) {
                part.addAll(parseLine(walkStep.path("linestring").asString(null)));
            }
        }
        return part;
    }

    private RouteGuide odsayTransit(double lat1, double lng1, double lat2, double lng2, String mode) {
        if (odsayKey.isBlank()) {
            throw fail(503, "ODsay 키 없음");
        }
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("SX", lng1);
        params.put("SY", lat1);
        params.put("EX", lng2);
        params.put("EY", lat2);
        params.put("apiKey", odsayKey);
        JsonNode json = externalApi.getJson(externalApi.buildStrictUrl(ODSAY + "/searchPubTransPathT", params), Map.of(), "ODsay");
        checkOdsayError(json);
        JsonNode path = pickPath(json.path("result").path("path"), "pathType", mode);
        if (path == null) {
            throw fail(404, mode.equals("bus") ? "버스로만 가는 길이 없어요" : "지하철로 가는 길이 없어요");
        }
        RouteGuide guide = newGuide(mode, "odsay");
        JsonNode info = path.path("info");
        guide.setMinutes(info.path("totalTime").asInt(0));
        guide.setMeters(info.path("totalDistance").asInt(0));
        guide.setFare(info.has("payment") ? info.path("payment").asInt(0) : null);
        for (JsonNode sub : path.path("subPath")) {
            addOdsaySub(guide, sub);
        }
        guide.getCoords().addAll(odsayLine(info.path("mapObj").asString("")));
        addEndStep(guide, lat2, lng2);
        return guide;
    }

    /** ODsay는 키·주소 오류도 200으로 보내고 본문에 error를 담아요 → 그 이유를 그대로 알려 줌 */
    private void checkOdsayError(JsonNode json) {
        JsonNode error = json.path("error");
        if (error.isMissingNode() || error.isNull()) {
            return;
        }
        JsonNode first = error.isArray() ? error.path(0) : error;
        String message = first.path("message").asString(first.path("msg").asString("알 수 없는 오류"));
        throw fail(502, "ODsay 오류 " + first.path("code").asString("") + " " + message);
    }

    /** ODsay 구간 하나 (trafficType 1 지하철, 2 버스, 3 걷기). 지하철은 way(방면)도 알려 줌 */
    private void addOdsaySub(RouteGuide guide, JsonNode sub) {
        int type = sub.path("trafficType").asInt(0);
        int minutes = sub.path("sectionTime").asInt(0);
        if (type == 3) {
            if (minutes > 0) {
                guide.getSteps().add(step(null, null, "walk", minutes + "분 걷기", sub.path("distance").asInt(0) + "m", false));
            }
            return;
        }
        boolean isBus = type == 2;
        JsonNode lane = sub.path("lane").path(0);
        String routeName = isBus ? "버스 " + lane.path("busNo").asString("") : lane.path("name").asString("");
        String startName = sub.path("startName").asString("");
        String endName = sub.path("endName").asString("");
        String way = sub.path("way").asString("");
        String detail = startName + " → " + endName + " · " + sub.path("stationCount").asInt(0) + "정거장 · " + minutes + "분"
                + (way.isBlank() ? "" : " · " + way + " 방면");
        guide.getSteps().add(step(sub.path("startY").asDouble(0), sub.path("startX").asDouble(0), isBus ? "bus" : "subway",
                routeName + " 타기", detail, true));
        guide.getSteps().add(step(sub.path("endY").asDouble(0), sub.path("endX").asDouble(0), "getoff", endName + "에서 내리기", null, false));
    }

    /** ODsay 노선 모양 (실패하면 빈 목록) */
    private List<double[]> odsayLine(String mapObj) {
        List<double[]> coords = new ArrayList<>();
        try {
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("mapObject", "0:0@" + mapObj);
            params.put("apiKey", odsayKey);
            JsonNode lane = externalApi.getJson(externalApi.buildStrictUrl(ODSAY + "/loadLane", params), Map.of(), "ODsay");
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

    /** 후보 경로 중 수단에 맞는 첫 경로: 버스는 버스만(2), 지하철은 지하철이 들어간 경로(1·3). 없으면 null */
    private JsonNode pickPath(JsonNode paths, String typeField, String mode) {
        for (JsonNode path : paths) {
            int type = path.path(typeField).asInt(0);
            boolean isMatch = mode.equals("bus") ? type == PATH_BUS : (type == PATH_SUBWAY || type == PATH_MIXED);
            if (isMatch) {
                return path;
            }
        }
        return null;
    }

    // ---------------------------------------------------------------- 도우미

    private RouteGuide newGuide(String mode, String source) {
        RouteGuide guide = new RouteGuide();
        guide.setMode(mode);
        guide.setSource(source);
        guide.setAvailable(true);
        return guide;
    }

    private RouteGuide unavailable(String mode, String reason) {
        RouteGuide guide = new RouteGuide();
        guide.setMode(mode);
        guide.setAvailable(false);
        guide.setMessage(reason);
        return guide;
    }

    private void addEndStep(RouteGuide guide, double lat, double lng) {
        guide.getSteps().add(step(lat, lng, "end", "도착", null, false));
    }

    private static GuideStep step(Double lat, Double lng, String kind, String text, String detail, boolean isKey) {
        GuideStep step = new GuideStep();
        step.setLat(lat);
        step.setLng(lng);
        step.setKind(kind);
        step.setText(text);
        step.setDetail(detail);
        step.setKey(isKey);
        return step;
    }

    /** "127.0,37.5 127.1,37.6" → [[37.5,127.0],[37.6,127.1]] */
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
