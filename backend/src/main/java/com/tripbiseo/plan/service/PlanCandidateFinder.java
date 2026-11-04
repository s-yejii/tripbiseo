package com.tripbiseo.plan.service;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import org.springframework.dao.DataAccessException;

import com.tripbiseo.common.external.FoundLocation;
import com.tripbiseo.common.util.Geo;
import com.tripbiseo.plan.dto.PlanArea;
import com.tripbiseo.plan.dto.PlanConditions;
import com.tripbiseo.plan.dto.PlanDong;
import com.tripbiseo.plan.mapper.AreaStatMapper;
import com.tripbiseo.common.external.LocationSearchClient;
import com.tripbiseo.live.dto.PlaceTagCount;
import com.tripbiseo.live.service.PlaceTagReader;
import com.tripbiseo.trip.dto.Place;
import com.tripbiseo.trip.service.PlaceService;

import lombok.RequiredArgsConstructor;

/**
 * ③ 일정 만들기 후보 장소 찾기 (RAG의 "찾기" 단계). Gemini는 여기서 찾은 후보 안에서만 고르게 해요.
 * 후보: 내 위치 근처 2km + 꼭 갈 곳과 그 주변 1km + 지역 이름이 들어간 곳 (우리 DB, PostGIS)
 * 근거: Gemini가 없는 가게를 지어내는 게 이 기능의 가장 큰 위험이라, DB 장소를 먼저 주고 id로 고르게 함.
 */
@Component
@RequiredArgsConstructor
public class PlanCandidateFinder {

    private static final int NEAR_ME_RADIUS_M = 2000;
    private static final int NEAR_ME_LIMIT = 60;
    private static final int AROUND_MUST_RADIUS_M = 1000;
    private static final int AROUND_MUST_LIMIT = 12;
    private static final int AREA_LIMIT = 25;
    private static final String NEAR_ME_WORDS = ".*(내 ?위치|현재 ?위치|여기|근처).*";
    private static final int GU_RADIUS_M = 3000;    // 구를 고르면 3km (구 하나가 대략 이 정도)
    private static final int DONG_RADIUS_M = 1500;  // 동·동네를 고르면 1.5km (걸어서 20분)
    private static final int KIND_LIMIT = 25;       // 종류마다 후보 수 (평점 순)
    // 화면 태그 코드 → ④ PlaceTagReader가 리뷰에서 세는 태그 이름
    // 종류 코드 → 프롬프트·안내에 쓰는 이름 (PlaceMapper kindCase와 같은 코드)
    private static final String TOUR_KIND = "tour";
    // 들를 곳이 아닌 이름: 도로·길(둘레길·산책길·숲길은 뺌)·교차로·주차장·표지석 등
    private static final Pattern NOT_SIGHT = Pattern.compile("명예도로|도로$|교차로|사거리|삼거리|나들목|IC$|주차장|표지석|기념비|(?<!둘레|산책|숲|하늘|순라|골목|올레)길$");
    private static final Pattern GOOD_SIGHT = Pattern.compile("공원|시장|거리|골목|전망|전시|미술관|박물관|갤러리|둘레길|산책|숲|천$|강$|마을|창작촌|광장");
    private static final int GOOD_SIGHT_ENOUGH = 3;
    private static final int MAX_SHAPE_LENGTH = 50000; // 동 경계 글자 길이 상한
    private static final int DONG_PLACE_LIMIT = 40;    // 동 하나에서 가져올 장소 수
    private static final int SIGHT_ENOUGH = 6;     // 들를 곳이 이만큼 있으면 카카오를 안 부름
    private static final int SIGHT_RADIUS_M = 3000;
    private static final Map<String, String> KIND_NAMES = Map.of("food", "밥집", "cafe", "카페", "bar", "술집", "tour", "관광·산책",
            "culture", "전시·문화", "shop", "쇼핑", "stay", "숙소", "etc", "기타");
    private static final Map<String, String> TAG_NAMES = Map.of("mood", "분위기", "quiet", "조용", "value", "가성비",
            "no_wait", "웨이팅없음", "solo", "혼밥");

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(PlanCandidateFinder.class);

    private final PlaceService placeService;
    private final AreaStatMapper areaStatMapper; // 고른 동 안의 장소
    private final LocationSearchClient locationSearchClient; // 들를 곳 보충 (카카오)
    private final PlaceTagReader placeTagReader; // 리뷰 태그 세기 (④ 도우미를 같이 씀)

    /** "내 위치·여기·근처"라고 했거나 지역이 비어 있으면 내 위치 기준 */
    public boolean isNearMe(String region) {
        return region == null || region.isBlank() || region.matches(NEAR_ME_WORDS);
    }

    /** 내 위치 근처 2km 후보 (가까운 순 60곳) */
    public void addNearMe(Map<String, Place> candidates, double lat, double lng) {
        addAll(candidates, placeService.getNearbyPlaceList(lat, lng, NEAR_ME_RADIUS_M, null), NEAR_ME_LIMIT);
    }

    /** 꼭 갈 곳 하나를 실제 장소로 찾고, 그 주변 1km 12곳도 후보에. 못 찾으면 null */
    public Place addMustVisit(Map<String, Place> candidates, String name, Double lat, Double lng) {
        Place place = placeService.getPlaceByName(name, lat, lng);
        if (place == null) {
            return null;
        }
        if (place.getId() != null) {
            candidates.put(place.getId(), place);
        }
        addAll(candidates, placeService.getNearbyPlaceList(place.getLat(), place.getLng(), AROUND_MUST_RADIUS_M, null), AROUND_MUST_LIMIT);
        return place;
    }

    /** 지역 이름의 낱말마다 (2자 이상, "서울" 제외) 주소·이름이 들어간 곳 25곳 */
    public void addArea(Map<String, Place> candidates, String region) {
        for (String word : region.split("[\\s,·/]+")) {
            if (word.length() >= 2 && !word.equals("서울")) {
                addAll(candidates, placeService.getAreaPlaceList(word), AREA_LIMIT);
            }
        }
    }

    /** 고른 지역 중심 반경(구 3km · 동 1.5km)에서 고른 종류마다 평점 순 25곳 (11/03) */
    public void addKinds(Map<String, Place> candidates, double lat, double lng, String areaType, List<String> kinds) {
        int radius = "gu".equals(areaType) ? GU_RADIUS_M : DONG_RADIUS_M;
        for (String kind : kinds) {
            addAll(candidates, placeService.getKindPlaceList(lat, lng, radius, kind, KIND_LIMIT), KIND_LIMIT);
        }
        if (kinds.contains(TOUR_KIND)) {
            addSights(candidates, lat, lng);
        }
    }

    /** 직접 고르기 지도에 보여 줄 장소: 종류별 평점 순. 들를 곳이 모자라면 카카오로 보충 */
    public List<Place> findKindPlaces(double lat, double lng, String kind) {
        Map<String, Place> found = newCandidates();
        addKinds(found, lat, lng, "gu", List.of(kind));
        return new ArrayList<>(found.values());
    }

    /** 지도에서 고른 구들: 구마다 후보를 모으고, 고른 동 안의 장소를 더해 표시해 둠 (점수를 더 줘서 먼저 들어가게) */
    public void addAreas(Map<String, Place> candidates, PlanConditions conditions, List<String> kinds) {
        Set<String> dongPlaceIds = new HashSet<>();
        for (PlanArea area : conditions.getAreas()) {
            addKinds(candidates, area.getLat(), area.getLng(), "gu", kinds);
            for (PlanDong dong : area.getDongs() == null ? List.<PlanDong>of() : area.getDongs()) {
                addDongPlaces(candidates, dong, dongPlaceIds);
            }
        }
        conditions.setDongPlaceIds(dongPlaceIds);
        keepInAreas(candidates, conditions.getAreas());
    }

    /** 반경으로 모으면 옆 구도 섞여서, 고른 구 주소인 곳만 남김 (꼭 갈 곳은 이 뒤에 따로 더해져서 안 빠짐) */
    private void keepInAreas(Map<String, Place> candidates, List<PlanArea> areas) {
        boolean hasAny = false;
        for (Place place : candidates.values()) {
            hasAny = hasAny || isInAreas(place, areas);
        }
        if (hasAny) { // 고른 구 주소인 곳이 하나도 없으면 주변이라도 남김 (0곳으로 실패하지 않게)
            candidates.values().removeIf(place -> !isInAreas(place, areas)); // removeIf: 조건에 맞는 칸을 지움
        }
    }

    private boolean isInAreas(Place place, List<PlanArea> areas) {
        for (PlanArea area : areas) {
            if (place.getAddress() != null && place.getAddress().contains(area.getName())) {
                return true;
            }
        }
        return false;
    }

    private void addDongPlaces(Map<String, Place> candidates, PlanDong dong, Set<String> dongPlaceIds) {
        if (dong.getGeometry() == null || dong.getGeometry().length() > MAX_SHAPE_LENGTH) {
            return;
        }
        try {
            for (Place place : areaStatMapper.selectPlacesInShape(dong.getGeometry(), DONG_PLACE_LIMIT)) {
                candidates.putIfAbsent(place.getId(), place);
                dongPlaceIds.add(place.getId());
            }
        } catch (DataAccessException e) { // 경계 글자가 이상하면 그 동만 건너뜀
            log.info("동 경계로 장소를 찾지 못함: {}", dong.getName());
        }
    }

    /** 들를 만한 곳만: 도로·교차로·주차장 같은 곳은 빼고, 공원·시장·거리 같은 곳이 충분하면 그것만 */
    private List<FoundLocation> pickGoodSights(List<FoundLocation> sights) {
        List<FoundLocation> kept = new ArrayList<>();
        List<FoundLocation> good = new ArrayList<>();
        for (FoundLocation sight : sights) {
            if (NOT_SIGHT.matcher(sight.getName()).find()) {
                continue;
            }
            kept.add(sight);
            if (GOOD_SIGHT.matcher(sight.getName()).find()) {
                good.add(sight);
            }
        }
        return good.size() >= GOOD_SIGHT_ENOUGH ? good : kept;
    }

    /** 들를 곳이 모자라면 카카오 관광명소로 보충 (우리 DB의 관광 데이터는 거의 종로구뿐). 저장할 때는 장소 연결 없이 이름·좌표만 남음 */
    private void addSights(Map<String, Place> candidates, double lat, double lng) {
        int count = 0;
        for (Place place : candidates.values()) {
            boolean isNear = place.getLat() != null && Geo.distanceMeters(lat, lng, place.getLat(), place.getLng()) <= SIGHT_RADIUS_M;
            if (TOUR_KIND.equals(place.getKind()) && isNear) { // 여러 구면 이 구 근처만 셈
                count++;
            }
        }
        if (count >= SIGHT_ENOUGH) {
            return;
        }
        for (FoundLocation found : pickGoodSights(locationSearchClient.searchSights(lat, lng, SIGHT_RADIUS_M))) {
            Place place = new Place();
            place.setId("kakao-" + found.getName());
            place.setName(found.getName());
            place.setCategory("관광지");
            place.setKind(TOUR_KIND);
            place.setAddress(found.getAddress());
            place.setLat(found.getLat());
            place.setLng(found.getLng());
            candidates.putIfAbsent(place.getId(), place);
        }
    }

    /** 후보들의 리뷰 태그 개수 (장소 id → 개수). 점수·이유에 같이 쓰려고 한 번만 셈 */
    public Map<String, PlaceTagCount> countTags(Map<String, Place> candidates) {
        return placeTagReader.countTags(new ArrayList<>(candidates.values()));
    }

    /** 후보마다 점수 = 보정 평점 + 고른 태그가 리뷰에 많으면 태그마다 0.3 (장소 id → 점수) */
    public Map<String, Double> scoreOf(Map<String, Place> candidates, Map<String, PlaceTagCount> tagCounts, List<String> tags) {
        List<String> wantedTags = toTagNames(tags);
        Map<String, Double> scores = new HashMap<>();
        for (Place place : candidates.values()) {
            scores.put(place.getId(), placeTagReader.getScore(place, tagCounts.get(place.getId()), wantedTags));
        }
        return scores;
    }

    /** 후보마다 리뷰 근거 (장소 id → "분위기 좋아요 · 리뷰 10개 중 6개" 목록). 근거가 없는 곳은 빠짐 */
    public Map<String, List<String>> reasonsOf(Map<String, Place> candidates, Map<String, PlaceTagCount> tagCounts, List<String> tags) {
        List<String> wantedTags = toTagNames(tags);
        Map<String, List<String>> reasons = new HashMap<>();
        for (Place place : candidates.values()) {
            List<String> found = placeTagReader.getReasons(tagCounts.get(place.getId()), wantedTags);
            if (!found.isEmpty()) {
                reasons.put(place.getId(), found);
            }
        }
        return reasons;
    }

    /**
     * Gemini에게 보여 줄 후보 (11/03). 점수순으로 자르면 점수가 낮게 깔린 종류(관광공사 장소 등)가 통째로 빠져서,
     * 꼭 갈 곳을 먼저 넣고 → 고른 종류부터 종류마다 한 곳씩 돌아가며(점수순) max곳까지 채움
     */
    public Map<String, Place> pickForPrompt(Map<String, Place> sorted, List<String> kinds, List<String> mustVisit, int max) {
        Map<String, Place> picked = newCandidates();
        Map<String, List<Place>> kindLists = new LinkedHashMap<>(); // 종류 → 그 종류 후보 (점수순)
        for (String kind : kinds) {
            kindLists.put(kind, new ArrayList<>());
        }
        for (Place place : sorted.values()) {
            if (isMustVisit(place, mustVisit) && picked.size() < max) {
                picked.put(place.getId(), place);
                continue;
            }
            String kind = place.getKind() == null ? "etc" : place.getKind();
            if (!kindLists.containsKey(kind)) {
                kindLists.put(kind, new ArrayList<>()); // 고르지 않은 종류(꼭 갈 곳 주변 등)는 뒤에
            }
            kindLists.get(kind).add(place);
        }
        int index = 0;
        boolean hasMore = true;
        while (picked.size() < max && hasMore) {
            hasMore = false;
            for (List<Place> places : kindLists.values()) {
                if (index < places.size() && picked.size() < max) {
                    picked.put(places.get(index).getId(), places.get(index));
                    hasMore = true;
                }
            }
            index++;
        }
        return picked;
    }

    /** 꼭 갈 곳으로 말한 이름과 같은 장소인지 (이름이 서로 포함되면 같은 곳으로 봄) */
    public boolean isMustVisit(Place place, List<String> mustVisit) {
        if (mustVisit == null || place.getName() == null) {
            return false;
        }
        for (String name : mustVisit) {
            if (name != null && name.length() >= 2 && (place.getName().contains(name) || name.contains(place.getName()))) {
                return true;
            }
        }
        return false;
    }

    /** 점수 높은 순으로 다시 줄 세운 후보 표 (Gemini에게 좋은 후보부터 보여 주려고) */
    public Map<String, Place> sortByScore(Map<String, Place> candidates, Map<String, Double> scores) {
        List<Place> places = new ArrayList<>(candidates.values());
        places.sort((first, second) -> Double.compare(scores.getOrDefault(second.getId(), 0.0), scores.getOrDefault(first.getId(), 0.0)));
        Map<String, Place> sorted = newCandidates();
        for (Place place : places) {
            sorted.put(place.getId(), place);
        }
        return sorted;
    }

    /** 새 후보 표 (넣은 순서를 지키는 Map: LinkedHashMap) */
    public Map<String, Place> newCandidates() {
        return new LinkedHashMap<>();
    }

    /** 종류 코드 → 이름 (food → 밥집). 모르는 코드면 기타 */
    public String kindName(String kind) {
        return KIND_NAMES.getOrDefault(kind == null ? "etc" : kind, "기타");
    }

    /** 화면 태그 코드(quiet…) → 리뷰에서 세는 태그 이름(조용…) */
    public List<String> toTagNames(List<String> tags) {
        List<String> names = new ArrayList<>();
        if (tags == null) {
            return names;
        }
        for (String tag : tags) {
            if (TAG_NAMES.containsKey(tag)) {
                names.add(TAG_NAMES.get(tag));
            }
        }
        return names;
    }

    /** 이미 있는 id는 그대로 두고 앞에서부터 limit개까지 추가 */
    private void addAll(Map<String, Place> candidates, List<Place> places, int limit) {
        int added = 0;
        for (Place place : places) {
            if (added >= limit) {
                break;
            }
            if (place.getId() != null && !candidates.containsKey(place.getId())) {
                candidates.put(place.getId(), place);
                added++;
            }
        }
    }
}
