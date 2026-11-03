package com.tripbiseo.trip.service;

import static com.tripbiseo.common.exception.ApiException.fail;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

import com.tripbiseo.common.external.FoundPlace;
import com.tripbiseo.common.external.PlaceSearchClient;
import com.tripbiseo.common.util.Geo;
import com.tripbiseo.trip.dto.OpenInfo;
import com.tripbiseo.trip.dto.Place;
import com.tripbiseo.trip.dto.PlaceCard;
import com.tripbiseo.trip.dto.PlaceDetail;
import com.tripbiseo.trip.mapper.PlaceMapper;

import lombok.RequiredArgsConstructor;

/**
 * ② 장소. 우리 DB(retrip_place 장소 · retrip_review 리뷰)에서 찾고, 없으면 TourAPI·카카오(PlaceSearchClient)로 좌표만 찾아요.
 * 반경·가까운 순은 SQL(PostGIS), 영업시간 글자 읽기는 OpeningHours 도우미가 해요.
 * 예전 코드: git show backend-v0:backend/src/main/java/com/tripbiseo/service/PlaceService.java
 */
@Service
@RequiredArgsConstructor
public class PlaceServiceImpl implements PlaceService {

    private static final int MIN_NEARBY_RADIUS_M = 50;
    private static final int MAX_NEARBY_RADIUS_M = 2000;
    private static final int NEARBY_CARD_COUNT = 10;
    private static final int NEARBY_SEARCH_LIMIT = 200;
    private static final int NAME_SEARCH_RADIUS_M = 2000;
    private static final int DETAIL_REVIEW_COUNT = 10;
    // 이 말이 분류에 들어가면 야외 (비 올 때 판단)
    // 검색어가 묶음 말일 때 (11/03: "음식점"으로 찾으면 분류가 "돈가스"·"국밥"처럼 세세해서 아무것도 안 나오던 문제)
    private static final List<String> FOOD_WORDS = List.of("음식점", "음식", "식당", "맛집", "밥집", "밥", "먹을곳");
    private static final List<String> CAFE_WORDS = List.of("카페", "커피", "디저트", "빵집", "베이커리");
    private static final List<String> BAR_WORDS = List.of("술집", "술", "주점", "호프", "맥주", "이자카야", "포차", "바");
    private static final String CAFE_PATTERN = "카페|커피|디저트|베이커리|케이크|도넛|와플|아이스크림|베이글|크레페|^차$";
    private static final String BAR_PATTERN = "이자카야|주점|맥주|호프|포장마차|바\\(BAR\\)|와인";
    private static final String OUTDOOR_WORDS = ".*(공원|산|한강|거리|해변|숲|길|광장|시장|레포츠|축제|관광지).*";

    private final PlaceMapper placeMapper;
    private final OpeningHours openingHours;
    private final PlaceSearchClient placeSearchClient; // DB에 없는 이름일 때 TourAPI → 카카오

    /** 장소 하나. 없으면 404 */
    @Override
    public Place getPlace(String placeId) {
        Place place = placeId == null ? null : placeMapper.selectPlace(placeId);
        if (place == null) {
            throw fail(404, "장소를 찾을 수 없어요.");
        }
        return place;
    }

    /** 이름·분류 검색 최대 20개 */
    @Override
    public List<PlaceCard> getPlaceList(String query, Double lat, Double lng) {
        if (query == null || query.isBlank() || query.trim().length() > 40) {
            throw fail(400, "검색어를 1~40자로 입력해 주세요.");
        }
        String keyword = query.trim();
        List<PlaceCard> cards = new ArrayList<>();
        String compact = keyword.replace(" ", "");
        String groupPattern = CAFE_WORDS.contains(compact) ? CAFE_PATTERN : BAR_WORDS.contains(compact) ? BAR_PATTERN : null;
        boolean isFood = FOOD_WORDS.contains(compact);
        String notFoodPattern = CAFE_PATTERN + "|" + BAR_PATTERN;
        for (Place place : placeMapper.selectPlaceList(keyword, toKind(keyword), lat, lng, groupPattern, isFood, notFoodPattern)) {
            cards.add(getPlaceCard(place, lat, lng));
        }
        return cards;
    }

    /** 근처 장소 카드 거리순 최대 10개 (반경 50~2000m로 자름) */
    @Override
    public List<PlaceCard> getNearbyCardList(double lat, double lng, int radius) {
        int safeRadius = Math.min(Math.max(radius, MIN_NEARBY_RADIUS_M), MAX_NEARBY_RADIUS_M);
        List<PlaceCard> cards = new ArrayList<>();
        for (Place place : placeMapper.selectNearbyPlaceList(lat, lng, safeRadius, null, NEARBY_CARD_COUNT)) {
            cards.add(getPlaceCard(place, null, null));
        }
        return cards;
    }

    /** 장소 상세: 카드 + 전화·사진·영업시간·편의시설·리뷰 */
    @Override
    public PlaceDetail getPlaceDetail(String placeId) {
        Place place = getPlace(placeId);
        LocalDate today = Geo.now().toLocalDate();
        PlaceDetail detail = new PlaceDetail();
        detail.setId(place.getId());
        detail.setName(place.getName());
        detail.setCategory(place.getCategory());
        detail.setAddress(place.getAddress());
        detail.setLat(place.getLat());
        detail.setLng(place.getLng());
        detail.setRating(place.getRating());
        detail.setReviewCount(place.getReviewCount());
        detail.setNaverUrl(getPlaceNaverUrl(place));
        detail.setTel(place.getTel());
        detail.setImageUrl(toHttps(place.getImageUrl()));
        detail.setUseTime(place.getUseTime());
        detail.setRestDate(place.getRestDate());
        detail.setHours(openingHours.getDayHoursList(place, today));
        detail.setOpenNow(openingHours.isOpenNow(place, today, Geo.nowMinutes()));
        detail.setAmenities(splitAmenities(place.getAmenities()));
        detail.setReviews(placeMapper.selectReviewList(placeId, DETAIL_REVIEW_COUNT));
        return detail;
    }

    /** 반경 안 장소, 가까운 순 (PostGIS ST_DWithin + 거리 정렬) */
    @Override
    public List<Place> getNearbyPlaceList(double lat, double lng, int radius, String category) {
        return placeMapper.selectNearbyPlaceList(lat, lng, radius, category, NEARBY_SEARCH_LIMIT);
    }

    /** 지역 이름이 들어간 장소, 보정 평점 순 */
    @Override
    public List<Place> getAreaPlaceList(String region) {
        if (region == null || region.isBlank()) {
            return new ArrayList<>();
        }
        return placeMapper.selectAreaPlaceList(region.trim());
    }

    /**
     * 이름으로 장소 찾기: ① DB 이름(가까운 곳 먼저) → ② "분식집"처럼 종류로 말했으면 근처 2km 그 분류 보정 평점 1위
     * → ③ TourAPI·카카오(id 없음) → 못 찾으면 null
     */
    @Override
    public Place getPlaceByName(String name, Double lat, Double lng) {
        if (name == null || name.isBlank()) {
            return null;
        }
        List<Place> found = placeMapper.selectPlaceListByName(name.trim());
        if (!found.isEmpty()) {
            return lat == null || lng == null ? found.get(0) : findClosest(found, lat, lng);
        }
        if (lat != null && lng != null) {
            Place best = findBestRated(getNearbyPlaceList(lat, lng, NAME_SEARCH_RADIUS_M, toKind(name)));
            if (best != null) {
                return best;
            }
        }
        FoundPlace external = placeSearchClient.searchByName(name.trim(), lat, lng);
        return external == null ? null : toPlace(external);
    }

    /** 장소 → 장소 카드. distance는 place.distance가 있으면 그것, 없고 lat·lng가 있으면 계산 */
    @Override
    public PlaceCard getPlaceCard(Place place, Double lat, Double lng) {
        PlaceCard card = new PlaceCard();
        card.setId(place.getId());
        card.setName(place.getName());
        card.setCategory(place.getCategory());
        card.setAddress(place.getAddress());
        card.setLat(place.getLat());
        card.setLng(place.getLng());
        card.setRating(place.getRating());
        card.setReviewCount(place.getReviewCount());
        card.setNaverUrl(getPlaceNaverUrl(place));
        Double distance = place.getDistance();
        if (distance == null && lat != null && lng != null) {
            distance = Geo.distanceMeters(lat, lng, place.getLat(), place.getLng());
        }
        if (distance != null) {
            card.setDistance((int) Math.round(distance));
        }
        return card;
    }

    /** 그날 arriveMinutes에 도착해 stayMinutes 머물 때의 영업 정보 */
    @Override
    public OpenInfo getOpenInfo(Place place, LocalDate date, int arriveMinutes, int stayMinutes) {
        return openingHours.getOpenInfo(place, date, arriveMinutes, stayMinutes);
    }

    /** 실내 장소인지: 분류에 공원·거리·시장 같은 야외 말이 없으면 실내로 봄 */
    @Override
    public boolean isIndoor(String category) {
        return category != null && !category.matches(OUTDOOR_WORDS);
    }

    /** 네이버 지도 검색 링크 (주소에서 "구·동"을 붙여 검색) */
    @Override
    public String getNaverUrl(String name, String address) {
        String area = "";
        if (address != null) {
            for (String word : address.split("\\s+")) {
                if (word.endsWith("구") || word.endsWith("동")) {
                    area = word;
                    break;
                }
            }
        }
        String keyword = (name + " " + area).trim();
        return "https://map.naver.com/p/search/" + URLEncoder.encode(keyword, StandardCharsets.UTF_8).replace("+", "%20");
    }

    // ---------------------------------------------------------------- 도우미

    /** "분식집" → "분식", "맛집" 같은 꼬리를 떼서 분류 검색에 씀 */
    private String toKind(String word) {
        String kind = word.trim().replaceAll("(맛집|집|가게|전문점)$", "");
        return kind.isEmpty() ? word.trim() : kind;
    }

    /** DB 네이버 지도 주소, 없으면 검색 링크 */
    private String getPlaceNaverUrl(Place place) {
        return place.getMapUrl() != null && !place.getMapUrl().isBlank() ? place.getMapUrl() : getNaverUrl(place.getName(), place.getAddress());
    }

    /** http:// → https:// (화면이 https라 섞인 주소는 막힘) */
    private String toHttps(String url) {
        return url == null ? null : url.replaceFirst("^http://", "https://");
    }

    /** "포장, 예약, 주차" → ["포장", "예약", "주차"] */
    private List<String> splitAmenities(String amenities) {
        List<String> list = new ArrayList<>();
        if (amenities == null) {
            return list;
        }
        for (String word : amenities.split(",")) {
            if (!word.trim().isEmpty()) {
                list.add(word.trim());
            }
        }
        return list;
    }

    /** 기준점에서 제일 가까운 장소 */
    private Place findClosest(List<Place> places, double lat, double lng) {
        Place closest = places.get(0);
        double closestMeters = Double.MAX_VALUE;
        for (Place place : places) {
            double meters = Geo.distanceMeters(lat, lng, place.getLat(), place.getLng());
            if (meters < closestMeters) {
                closest = place;
                closestMeters = meters;
            }
        }
        return closest;
    }

    /** 평점 있는 곳 중 보정 평점 1위 (없으면 null) */
    private Place findBestRated(List<Place> places) {
        Place best = null;
        double bestScore = -1;
        for (Place place : places) {
            if (place.getRating() == null) {
                continue;
            }
            double score = getAdjustedRating(place);
            if (score > bestScore) {
                best = place;
                bestScore = score;
            }
        }
        return best;
    }

    /** 보정 평점: (리뷰 수 × 평점 + 100 × 4.6) ÷ (리뷰 수 + 100). SQL의 adjustedRating과 같은 식 */
    private double getAdjustedRating(Place place) {
        int count = place.getReviewCount() == null ? 0 : place.getReviewCount();
        double rating = place.getRating() == null ? 0 : place.getRating();
        return (count * rating + 100 * 4.6) / (count + 100);
    }

    /** 외부 검색 결과 → Place (DB에 없어서 id는 null, 저장하지 않음) */
    private Place toPlace(FoundPlace found) {
        Place place = new Place();
        place.setName(found.getName());
        place.setCategory(found.getCategory());
        place.setAddress(found.getAddress());
        place.setLat(found.getLat());
        place.setLng(found.getLng());
        return place;
    }
}
