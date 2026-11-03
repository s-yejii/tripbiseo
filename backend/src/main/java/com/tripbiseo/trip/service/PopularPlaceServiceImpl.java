package com.tripbiseo.trip.service;

import java.text.NumberFormat;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.tripbiseo.common.util.Geo;
import com.tripbiseo.trip.dto.Place;
import com.tripbiseo.trip.dto.PopularPlace;
import com.tripbiseo.trip.dto.PopularResponse;
import com.tripbiseo.trip.mapper.PlaceMapper;

import lombok.RequiredArgsConstructor;

/**
 * 인기 장소. 점수는 SQL(PlaceMapper.selectPopularPlaceList)이 계산하고, 여기서는
 * 반경 넓히기 · 오늘 쉬는 곳 빼기 · 같은 분류는 2개까지 · 보여 줄 이유 한 줄을 정해요.
 */
@Service
@RequiredArgsConstructor
public class PopularPlaceServiceImpl implements PopularPlaceService {

    private static final int NEAR_RADIUS_M = 3000;   // 대중교통 10~15분
    private static final int WIDE_RADIUS_M = 5000;
    private static final int MIN_RADIUS_M = 200;
    private static final int MAX_LIMIT = 20;
    private static final int FETCH_TIMES = 5;         // 거를 걸 생각해서 limit의 5배를 가져옴
    private static final int SAME_CATEGORY_MAX = 2;   // 같은 분류("카페,디저트")는 2개까지
    private static final String CAFE_PATTERN = ".*(카페|커피|디저트|베이커리|케이크|도넛|와플|아이스크림|베이글|크레페).*";
    private static final String BAR_PATTERN = ".*(이자카야|주점|맥주|호프|포장마차|와인).*";
    private static final int VISITORS_SHOWN_MIN = 5;  // 이 수보다 적으면 "N명"을 안 보여 줌 (개인정보)
    private static final String SCOPE_NEAR = "near";
    private static final String SCOPE_WIDE = "wide";
    private static final String SCOPE_SEOUL = "seoul";

    private final PlaceMapper placeMapper;
    private final PlaceService placeService;
    private final OpeningHours openingHours;

    /** 인기 장소 (반경 넓히기 포함) */
    @Override
    public PopularResponse getPopularPlaceList(long memberId, Double lat, Double lng, Integer radius, int limit) {
        int count = Math.min(Math.max(limit, 1), MAX_LIMIT);
        boolean hasPoint = lat != null && lng != null;
        PopularResponse response = new PopularResponse();
        List<Place> rows;
        if (!hasPoint) {
            response.setScope(SCOPE_SEOUL);
            rows = find(memberId, null, null, null, count);
        } else if (radius != null) {
            response.setScope(SCOPE_NEAR);
            rows = find(memberId, lat, lng, Math.min(Math.max(radius, MIN_RADIUS_M), WIDE_RADIUS_M), count);
        } else {
            response.setScope(SCOPE_NEAR);
            rows = find(memberId, lat, lng, NEAR_RADIUS_M, count);
            if (countScored(rows) < count) {
                response.setScope(SCOPE_WIDE);
                rows = find(memberId, lat, lng, WIDE_RADIUS_M, count);
            }
            if (countScored(rows) < count) {
                response.setScope(SCOPE_SEOUL); // 시범 지역 밖이거나 기록이 적은 곳: 반경 없이 (거리는 그대로 계산)
                rows = find(memberId, lat, lng, null, count);
            }
        }
        addPicked(response, rows, count, lat, lng);
        return response;
    }

    private List<Place> find(long memberId, Double lat, Double lng, Integer radius, int count) {
        return placeMapper.selectPopularPlaceList(memberId, lat, lng, radius, count * FETCH_TIMES);
    }

    /** 다른 회원 기록이 있는 장소 수 */
    private int countScored(List<Place> rows) {
        int scored = 0;
        for (Place place : rows) {
            if (place.getScore() != null && place.getScore() > 0) {
                scored++;
            }
        }
        return scored;
    }

    /** 오늘 쉬는 곳은 빼고, 같은 분류는 2개·같은 묶음(카페/식당/술집)은 절반까지만 해서 count개 */
    private void addPicked(PopularResponse response, List<Place> rows, int count, Double lat, Double lng) {
        LocalDate today = Geo.now().toLocalDate();
        int groupMax = (count + 1) / 2;
        Map<String, Integer> categoryCount = new HashMap<>();
        Map<String, Integer> groupCount = new HashMap<>();
        for (Place place : rows) {
            if (response.getPlaces().size() >= count) {
                break;
            }
            String category = place.getCategory() == null ? "" : place.getCategory();
            String group = toGroup(category);
            int sameCategory = categoryCount.getOrDefault(category, 0);
            int sameGroup = groupCount.getOrDefault(group, 0);
            if (sameCategory >= SAME_CATEGORY_MAX || sameGroup >= groupMax || openingHours.isRestDay(place, today)) {
                continue;
            }
            categoryCount.put(category, sameCategory + 1);
            groupCount.put(group, sameGroup + 1);
            response.getPlaces().add(toPopularPlace(place, lat, lng));
        }
    }

    /** 분류 → 묶음 (카페 / 술집 / 식당·그 밖) */
    private String toGroup(String category) {
        if (category.matches(CAFE_PATTERN)) {
            return "cafe";
        }
        return category.matches(BAR_PATTERN) ? "bar" : "food";
    }

    private PopularPlace toPopularPlace(Place place, Double lat, Double lng) {
        PopularPlace popular = new PopularPlace();
        popular.setPlace(placeService.getPlaceCard(place, lat, lng));
        int visitors = place.getVisitors() == null ? 0 : place.getVisitors();
        if (visitors >= VISITORS_SHOWN_MIN) {
            popular.setVisitors(visitors);
            popular.setReason("최근 한 달 " + visitors + "명이 다녀갔어요");
        } else if (place.getRating() != null) {
            int reviews = place.getReviewCount() == null ? 0 : place.getReviewCount();
            popular.setReason("★" + place.getRating() + " · 리뷰 " + NumberFormat.getInstance().format(reviews));
        } else {
            popular.setReason("가 볼 만한 곳");
        }
        return popular;
    }
}
