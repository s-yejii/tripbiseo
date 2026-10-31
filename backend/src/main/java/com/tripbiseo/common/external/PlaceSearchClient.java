package com.tripbiseo.common.external;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.tripbiseo.common.exception.ApiException;
import com.tripbiseo.common.util.Geo;

import tools.jackson.databind.JsonNode;

/**
 * 우리 DB에 없는 장소를 이름으로 찾기: TourAPI(전국 관광지) → 카카오 로컬 → 못 찾으면 null.
 * 결과는 저장하지 않습니다 (카카오 약관). 키가 없거나 실패하면 null.
 */
@Component
public class PlaceSearchClient {

    private static final String TOUR_URL = "https://apis.data.go.kr/B551011/KorService2/searchKeyword2";
    private static final String KAKAO_URL = "https://dapi.kakao.com/v2/local/search/keyword.json";
    private static final Map<String, String> TOUR_TYPES = Map.of("12", "관광지", "14", "문화시설", "15", "축제·행사",
            "28", "레포츠", "32", "숙박", "38", "쇼핑", "39", "음식점");

    private final ExternalApi externalApi;

    @Value("${TOUR_API_KEY:}")
    private String tourKey;
    @Value("${KMA_SERVICE_KEY:}")
    private String kmaKey; // TourAPI 키가 없으면 같은 공공데이터포털 키라 기상청 키를 씀
    @Value("${KAKAO_REST_API_KEY:}")
    private String kakaoKey;

    public PlaceSearchClient(ExternalApi externalApi) {
        this.externalApi = externalApi;
    }

    /** 이름으로 장소 찾기. lat·lng(null 가능)가 있으면 가까운 곳을 먼저. 못 찾으면 null */
    public FoundPlace searchByName(String name, Double lat, Double lng) {
        FoundPlace place = searchTour(name, lat, lng);
        if (place != null) {
            return place;
        }
        return searchKakao(name, lat, lng);
    }

    // TourAPI 키워드 검색. 이름이 같은 곳 → 이름이 짧은 곳("N서울타워"가 "한쿡 N서울타워"보다 먼저) → 가까운 곳
    private FoundPlace searchTour(String name, Double lat, Double lng) {
        String key = tourKey.isBlank() ? kmaKey : tourKey;
        if (key.isBlank()) {
            return null;
        }
        if (key.contains("%")) {
            key = URLDecoder.decode(key, StandardCharsets.UTF_8);
        }
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("serviceKey", key);
        params.put("MobileOS", "ETC");
        params.put("MobileApp", "tripbiseo");
        params.put("_type", "json");
        params.put("numOfRows", 10);
        params.put("pageNo", 1);
        params.put("keyword", name);
        try {
            JsonNode json = externalApi.getJson(externalApi.buildUrl(TOUR_URL, params), Map.of(), "TourAPI");
            return pickBest(json.path("response").path("body").path("items").path("item"), name, lat, lng);
        } catch (ApiException e) {
            return null;
        }
    }

    // 공공데이터 API는 결과가 1건이면 배열이 아니라 객체 하나로 옴 → 그 경우도 한 칸짜리로 봄
    private FoundPlace pickBest(JsonNode items, String name, Double lat, Double lng) {
        FoundPlace best = null;
        double bestScore = Double.MAX_VALUE;
        Iterable<JsonNode> list = items.isArray() ? items : List.of(items);
        for (JsonNode item : list) {
            FoundPlace place = fromTour(item);
            if (place == null) {
                continue;
            }
            double score = score(place, name, lat, lng);
            if (score < bestScore) {
                best = place;
                bestScore = score;
            }
        }
        return best;
    }

    // 점수가 작을수록 좋음: 이름이 다르면 +100만, 이름 길이 × 1만, 거리(m)
    private static double score(FoundPlace place, String name, Double lat, Double lng) {
        double score = place.getName().equals(name) ? 0 : 1_000_000;
        score += place.getName().length() * 10_000;
        if (lat != null && lng != null) {
            score += Geo.distanceMeters(lat, lng, place.getLat(), place.getLng());
        }
        return score;
    }

    private static FoundPlace fromTour(JsonNode item) {
        if (!item.has("title") || !item.has("mapy") || !item.has("mapx")) {
            return null;
        }
        FoundPlace place = new FoundPlace();
        place.setName(item.path("title").asString(""));
        place.setCategory(TOUR_TYPES.getOrDefault(item.path("contenttypeid").asString(""), "관광지"));
        place.setAddress(item.path("addr1").asString(null));
        place.setLat(item.path("mapy").asDouble(0));
        place.setLng(item.path("mapx").asDouble(0));
        return place;
    }

    private FoundPlace searchKakao(String name, Double lat, Double lng) {
        if (kakaoKey.isBlank()) {
            return null;
        }
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("query", name);
        params.put("size", 1);
        if (lat != null && lng != null) {
            params.put("x", lng);
            params.put("y", lat);
            params.put("sort", "accuracy");
        }
        try {
            JsonNode json = externalApi.getJson(externalApi.buildUrl(KAKAO_URL, params), Map.of("Authorization", "KakaoAK " + kakaoKey), "카카오 로컬");
            JsonNode doc = json.path("documents").path(0);
            if (doc.isMissingNode()) {
                return null;
            }
            return fromKakao(doc);
        } catch (ApiException e) {
            return null;
        }
    }

    private static FoundPlace fromKakao(JsonNode doc) {
        FoundPlace place = new FoundPlace();
        place.setName(doc.path("place_name").asString(""));
        String category = doc.path("category_name").asString("");
        place.setCategory(category.substring(category.lastIndexOf('>') + 1).trim()); // "음식점 > 카페" → "카페"
        String road = doc.path("road_address_name").asString("");
        place.setAddress(road.isBlank() ? doc.path("address_name").asString(null) : road);
        place.setLat(doc.path("y").asDouble(0));
        place.setLng(doc.path("x").asDouble(0));
        return place;
    }
}
