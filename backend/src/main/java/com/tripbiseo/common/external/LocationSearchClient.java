package com.tripbiseo.common.external;

import static com.tripbiseo.common.exception.ApiException.fail;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import tools.jackson.databind.JsonNode;

/**
 * 카카오 로컬로 지역·위치 찾기 (어디로 가요? · 어디서 출발해요?).
 * 우리 장소 DB는 카페·음식점뿐이고 주소가 도로명이라, 동 이름·역·건물은 카카오에 물어봐요. 결과는 저장하지 않아요 (카카오 약관).
 */
@Component
public class LocationSearchClient {

    private static final String ADDRESS_URL = "https://dapi.kakao.com/v2/local/search/address.json";
    private static final String KEYWORD_URL = "https://dapi.kakao.com/v2/local/search/keyword.json";
    private static final String COORD_URL = "https://dapi.kakao.com/v2/local/geo/coord2address.json";
    private static final String CATEGORY_URL = "https://dapi.kakao.com/v2/local/search/category.json";
    private static final String LABEL = "카카오 로컬";
    private static final String SUBWAY_CODE = "SW8";          // 카카오 분류: 지하철역
    private static final int SUBWAY_RADIUS_M = 3000;
    private static final int SUBWAY_COUNT = 4;
    private static final String OFFICE_CODE = "PO3";          // 카카오 분류: 공공기관 (같은 이름의 가게를 빼려고)
    private static final String OFFICE_NAME = "\\S*(구청|시청)"; // "구로구청 별관" 같은 곳은 뺌
    private static final String SIGHT_CODE = "AT4";           // 카카오 분류: 관광명소 (공원·거리·전망대 등)
    private static final int SIGHT_COUNT = 15;
    private static final String SEOUL = "서울";             // 시범 지역: 지역 검색은 서울만
    private static final double SEOUL_LAT = 37.5665;         // 서울시청 (키워드 보충 검색의 중심)
    private static final double SEOUL_LNG = 126.9780;
    private static final int SEOUL_RADIUS_M = 20000;          // 시청에서 20km면 서울 거의 전부
    private static final int AREA_MAX = 10;
    private static final int AREA_KEYWORD_FROM = 3;           // 지역 결과가 이보다 적으면 키워드로 보충
    private static final int ADDRESS_SIZE = 30;               // 카카오 주소 검색 최대
    private static final int KEYWORD_SIZE = 15;               // 카카오 키워드 검색 최대

    private final ExternalApi externalApi;

    @Value("${KAKAO_REST_API_KEY:}")
    private String kakaoKey;

    public LocationSearchClient(ExternalApi externalApi) {
        this.externalApi = externalApi;
    }

    /** 지역(구·동) 찾기. 서울만, 최대 10개. 적으면 키워드(홍대·성수역 같은 동네 이름)로 보충 */
    public List<FoundArea> searchAreas(String query) {
        List<FoundArea> areas = new ArrayList<>();
        Set<String> names = new HashSet<>(); // 같은 지역 두 번 안 넣기
        for (JsonNode doc : getDocuments(ADDRESS_URL, Map.of("query", query, "size", ADDRESS_SIZE))) {
            FoundArea area = toArea(doc);
            if (area != null && names.add(area.getFullName())) {
                areas.add(area);
            }
        }
        if (areas.size() < AREA_KEYWORD_FROM) {
            addKeywordAreas(areas, names, query);
        }
        return areas.size() > AREA_MAX ? areas.subList(0, AREA_MAX) : areas;
    }

    /** 출발 위치 찾기: 키워드(역·출구·건물·가게) → 없으면 주소. lat·lng가 있으면 거리도 같이 */
    public List<FoundLocation> searchLocations(String query, Double lat, Double lng) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("query", query);
        params.put("size", KEYWORD_SIZE);
        if (lat != null && lng != null) {
            params.put("x", lng);
            params.put("y", lat);
        }
        List<FoundLocation> locations = new ArrayList<>();
        for (JsonNode doc : getDocuments(KEYWORD_URL, params)) {
            locations.add(toLocation(doc));
        }
        if (locations.isEmpty()) {
            for (JsonNode doc : getDocuments(ADDRESS_URL, Map.of("query", query, "size", KEYWORD_SIZE))) {
                locations.add(addressToLocation(doc));
            }
        }
        return locations;
    }

    /** 근처 관광명소 (일정에 넣을 들를 곳이 우리 DB에 없을 때), 가까운 순 */
    public List<FoundLocation> searchSights(double lat, double lng, int radius) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("category_group_code", SIGHT_CODE);
        params.put("x", lng);
        params.put("y", lat);
        params.put("radius", radius);
        params.put("sort", "distance");
        params.put("size", SIGHT_COUNT);
        List<FoundLocation> sights = new ArrayList<>();
        for (JsonNode doc : getDocuments(CATEGORY_URL, params)) {
            sights.add(toLocation(doc));
        }
        return sights;
    }

    /** 출발지로 고르기 좋은 곳: 가까운 지하철역 4곳 + 가까운 구청·시청 1곳씩, 가까운 순 */
    public List<FoundLocation> searchHubs(double lat, double lng) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("category_group_code", SUBWAY_CODE);
        params.put("x", lng);
        params.put("y", lat);
        params.put("radius", SUBWAY_RADIUS_M);
        params.put("sort", "distance");
        params.put("size", SUBWAY_COUNT);
        List<FoundLocation> hubs = new ArrayList<>();
        for (JsonNode doc : getDocuments(CATEGORY_URL, params)) {
            hubs.add(toLocation(doc));
        }
        addOffices(hubs, lat, lng);
        hubs.sort(Comparator.comparingInt((FoundLocation hub) -> hub.getDistance() == null ? Integer.MAX_VALUE : hub.getDistance()));
        return hubs;
    }

    /** 지금 좌표의 주소("서울 영등포구 …")로 그 지역의 구청·시청을 찾아 더함 */
    private void addOffices(List<FoundLocation> hubs, double lat, double lng) {
        FoundLocation here = findAddress(lat, lng);
        if (here == null) {
            return;
        }
        String[] words = here.getAddress().split(" ");
        if (words.length >= 2) {
            addOffice(hubs, words[1] + "청", "구청", lat, lng); // 영등포구 → 영등포구청, 성남시 → 성남시청
            addOffice(hubs, words[0] + "시청", "시청", lat, lng); // 서울 → 서울시청
        }
    }

    /** 공공기관 중 이름이 맞는 첫 곳을 더함. 분류 글자는 "구청"·"시청"으로 */
    private void addOffice(List<FoundLocation> hubs, String query, String category, double lat, double lng) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("query", query);
        params.put("category_group_code", OFFICE_CODE);
        params.put("x", lng);
        params.put("y", lat);
        params.put("size", KEYWORD_SIZE);
        for (JsonNode doc : getDocuments(KEYWORD_URL, params)) {
            FoundLocation location = toLocation(doc);
            if (location.getName().matches(OFFICE_NAME)) {
                location.setCategory(category);
                hubs.add(location);
                return;
            }
        }
    }

    /** 좌표 → 주소 ("현재 위치로"). 건물 이름이 있으면 그 이름, 없으면 도로명 주소. 못 찾으면 null */
    public FoundLocation findAddress(double lat, double lng) {
        List<JsonNode> docs = getDocuments(COORD_URL, Map.of("x", lng, "y", lat));
        if (docs.isEmpty()) {
            return null;
        }
        JsonNode road = docs.get(0).path("road_address");
        String jibun = docs.get(0).path("address").path("address_name").asString("");
        String roadName = road.path("address_name").asString("");
        String building = road.path("building_name").asString("");
        FoundLocation location = new FoundLocation();
        location.setAddress(roadName.isBlank() ? jibun : roadName);
        location.setName(building.isBlank() ? location.getAddress() : building);
        location.setCategory("현재 위치");
        location.setLat(lat);
        location.setLng(lng);
        return location;
    }

    // ---------------------------------------------------------------- 도우미

    /** 카카오에 물어보고 documents 목록. 키가 없으면 503 */
    private List<JsonNode> getDocuments(String url, Map<String, Object> params) {
        if (kakaoKey.isBlank()) {
            throw fail(503, "위치 검색 키(KAKAO_REST_API_KEY)가 설정되지 않았어요.");
        }
        JsonNode json = externalApi.getJson(externalApi.buildUrl(url, params), Map.of("Authorization", "KakaoAK " + kakaoKey), LABEL);
        List<JsonNode> docs = new ArrayList<>();
        for (JsonNode doc : json.path("documents")) {
            docs.add(doc);
        }
        return docs;
    }

    /** 주소 검색 결과 중 지역(REGION)이면서 서울인 것만 → 구 또는 동. 아니면 null */
    private static FoundArea toArea(JsonNode doc) {
        JsonNode address = doc.path("address");
        boolean isRegion = "REGION".equals(doc.path("address_type").asString(""));
        if (!isRegion || !address.path("region_1depth_name").asString("").startsWith(SEOUL)) {
            return null;
        }
        String gu = address.path("region_2depth_name").asString("");
        String dong = address.path("region_3depth_name").asString("");
        if (gu.isBlank()) {
            return null; // "서울" 전체는 너무 넓어서 뺌
        }
        FoundArea area = new FoundArea();
        area.setName(dong.isBlank() ? gu : dong);
        area.setFullName(doc.path("address_name").asString(""));
        area.setType(dong.isBlank() ? "gu" : "dong");
        area.setLat(doc.path("y").asDouble(0));
        area.setLng(doc.path("x").asDouble(0));
        return area;
    }

    /** 키워드로 서울 안 동네·역 찾아 보충 (예: "홍대" → 홍대입구역) */
    private void addKeywordAreas(List<FoundArea> areas, Set<String> names, String query) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("query", query);
        params.put("size", KEYWORD_SIZE);
        params.put("x", SEOUL_LNG);
        params.put("y", SEOUL_LAT);
        params.put("radius", SEOUL_RADIUS_M);
        for (JsonNode doc : getDocuments(KEYWORD_URL, params)) {
            String address = doc.path("address_name").asString("");
            String name = doc.path("place_name").asString("");
            if (address.startsWith(SEOUL) && names.add(name)) {
                FoundArea area = new FoundArea();
                area.setName(name);
                area.setFullName(address);
                area.setType("spot");
                area.setLat(doc.path("y").asDouble(0));
                area.setLng(doc.path("x").asDouble(0));
                areas.add(area);
            }
        }
    }

    /** 키워드 검색 결과 → 위치 */
    private static FoundLocation toLocation(JsonNode doc) {
        FoundLocation location = new FoundLocation();
        location.setName(doc.path("place_name").asString(""));
        String road = doc.path("road_address_name").asString("");
        location.setAddress(road.isBlank() ? doc.path("address_name").asString("") : road);
        String category = doc.path("category_name").asString("");
        location.setCategory(category.substring(category.lastIndexOf('>') + 1).trim()); // "교통,수송 > 지하철역" → "지하철역"
        location.setLat(doc.path("y").asDouble(0));
        location.setLng(doc.path("x").asDouble(0));
        String distance = doc.path("distance").asString("");
        location.setDistance(distance.isBlank() ? null : Integer.valueOf(distance));
        return location;
    }

    /** 주소 검색 결과 → 위치 (건물 이름이 있으면 그 이름) */
    private static FoundLocation addressToLocation(JsonNode doc) {
        JsonNode road = doc.path("road_address");
        String building = road.path("building_name").asString("");
        String roadName = road.path("address_name").asString("");
        FoundLocation location = new FoundLocation();
        location.setAddress(roadName.isBlank() ? doc.path("address_name").asString("") : roadName);
        location.setName(building.isBlank() ? location.getAddress() : building);
        location.setCategory("주소");
        location.setLat(doc.path("y").asDouble(0));
        location.setLng(doc.path("x").asDouble(0));
        return location;
    }
}
