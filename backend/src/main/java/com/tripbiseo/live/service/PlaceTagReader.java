package com.tripbiseo.live.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.tripbiseo.live.dto.PlaceTagCount;
import com.tripbiseo.live.mapper.PlaceTagMapper;
import com.tripbiseo.trip.dto.Place;

import lombok.RequiredArgsConstructor;

/**
 * ④ 추천 점수·이유 칩: 리뷰에서 "혼밥·조용·분위기·데이트·가성비·웨이팅없음" 태그를 세서 판단해요.
 * 태그 인정: 그 장소 리뷰 중 2개 이상이면서 (3개 이상 또는 20% 이상) — 리뷰 한두 개의 의견이 아니라 여러 리뷰의 공통점일 때만.
 * 근거: 장소마다 리뷰가 10~19개로 고르게 있어서 비율로 판단할 수 있음 (DB 확인 11/02).
 */
@Component
@RequiredArgsConstructor
public class PlaceTagReader {

    private static final int MIN_TAG_COUNT = 2;
    private static final int SURE_TAG_COUNT = 3;
    private static final int TAG_PERCENT = 20;
    private static final double TAG_BONUS = 0.3;     // 원하는 태그가 있으면 보정 평점에 더하는 점수
    private static final int MAX_EXTRA_REASONS = 2;  // 원하는 태그가 없을 때 보여 줄 다른 태그 수

    private final PlaceTagMapper placeTagMapper;

    /** 후보 장소들의 태그 개수 (장소 id → 개수). 후보가 없으면 빈 표 */
    public Map<String, PlaceTagCount> countTags(List<Place> places) {
        Map<String, PlaceTagCount> countMap = new HashMap<>();
        List<String> placeIds = new ArrayList<>();
        for (Place place : places) {
            if (place.getId() != null) {
                placeIds.add(place.getId());
            }
        }
        if (placeIds.isEmpty()) {
            return countMap;
        }
        for (PlaceTagCount count : placeTagMapper.selectPlaceTagCountList(placeIds)) {
            countMap.put(count.getPlaceId(), count);
        }
        return countMap;
    }

    /** 추천 점수 = 보정 평점 + 원하는 태그마다 0.3 */
    public double getScore(Place place, PlaceTagCount count, List<String> wantedTags) {
        double score = getAdjustedRating(place);
        for (String tag : wantedTags) {
            if (hasTag(count, tag)) {
                score += TAG_BONUS;
            }
        }
        return score;
    }

    /** 이유 칩: 원하는 태그 중 있는 것, 없으면 그 장소의 다른 태그 2개까지. 예: "혼밥하기 좋아요 · 리뷰 15개 중 5개" */
    public List<String> getReasons(PlaceTagCount count, List<String> wantedTags) {
        List<String> reasons = new ArrayList<>();
        if (count == null) {
            return reasons;
        }
        for (String tag : wantedTags) {
            if (hasTag(count, tag)) {
                reasons.add(toReason(tag, count));
            }
        }
        if (!reasons.isEmpty()) {
            return reasons;
        }
        for (String tag : List.of("분위기", "조용", "혼밥", "데이트", "가성비", "웨이팅없음")) {
            if (reasons.size() < MAX_EXTRA_REASONS && hasTag(count, tag)) {
                reasons.add(toReason(tag, count));
            }
        }
        return reasons;
    }

    // ---------------------------------------------------------------- 도우미

    /** 태그 인정: 2개 이상이면서 (3개 이상 또는 리뷰의 20% 이상) */
    private boolean hasTag(PlaceTagCount count, String tag) {
        if (count == null || count.getTotal() == 0) {
            return false;
        }
        int tagCount = getTagCount(count, tag);
        boolean isEnoughShare = tagCount * 100 >= count.getTotal() * TAG_PERCENT;
        return tagCount >= MIN_TAG_COUNT && (tagCount >= SURE_TAG_COUNT || isEnoughShare);
    }

    private int getTagCount(PlaceTagCount count, String tag) {
        switch (tag) {
            case "혼밥": return count.getSolo();
            case "조용": return count.getQuiet();
            case "분위기": return count.getMood();
            case "데이트": return count.getDate();
            case "가성비": return count.getValue();
            case "웨이팅없음": return count.getNoWait();
            default: return 0;
        }
    }

    private String toReason(String tag, PlaceTagCount count) {
        String label;
        switch (tag) {
            case "혼밥": label = "혼밥하기 좋아요"; break;
            case "조용": label = "조용해요"; break;
            case "분위기": label = "분위기 좋아요"; break;
            case "데이트": label = "데이트하기 좋아요"; break;
            case "가성비": label = "가성비 좋아요"; break;
            default: label = "웨이팅 적어요"; break;
        }
        return label + " · 리뷰 " + count.getTotal() + "개 중 " + getTagCount(count, tag) + "개";
    }

    /** 보정 평점: (리뷰 수 × 평점 + 100 × 4.6) ÷ (리뷰 수 + 100). ② PlaceMapper SQL과 같은 식 */
    private double getAdjustedRating(Place place) {
        int reviewCount = place.getReviewCount() == null ? 0 : place.getReviewCount();
        double rating = place.getRating() == null ? 0 : place.getRating();
        return (reviewCount * rating + 100 * 4.6) / (reviewCount + 100);
    }
}
