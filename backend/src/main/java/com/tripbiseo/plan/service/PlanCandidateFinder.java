package com.tripbiseo.plan.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

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

    private final PlaceService placeService;

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

    /** 새 후보 표 (넣은 순서를 지키는 Map: LinkedHashMap) */
    public Map<String, Place> newCandidates() {
        return new LinkedHashMap<>();
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
