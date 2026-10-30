package com.tripbiseo.trip.service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

import com.tripbiseo.common.external.PlaceSearchClient;
import com.tripbiseo.trip.dto.DayHours;
import com.tripbiseo.trip.dto.OpenInfo;
import com.tripbiseo.trip.dto.Place;
import com.tripbiseo.trip.dto.PlaceCard;
import com.tripbiseo.trip.dto.PlaceDetail;

import lombok.RequiredArgsConstructor;

/**
 * ② 장소. 지금은 가짜 값이에요. 장소 Mapper(trip/mapper/PlaceMapper + XML)를 만들고 하나씩 진짜로 바꿔요.
 * 예전 코드: git show backend-v0:backend/src/main/java/com/tripbiseo/service/PlaceService.java
 *           git show backend-v0:backend/src/main/resources/mapper/PlaceMapper.xml
 * 주의: 예전에는 openInfo·isIndoor·naverMapLink 같은 static 도우미를 다른 기능이 바로 불렀어요. 이제는 이 인터페이스 메서드로만
 */
@Service
@RequiredArgsConstructor
public class PlaceServiceImpl implements PlaceService {

    private final PlaceSearchClient placeSearchClient; // TODO: ② getPlaceByName에서 DB에 없을 때 씀

    /** 장소 하나 */
    // TODO: ② 장소 하나. selectPlace(id) → 없으면 404 "장소를 찾을 수 없어요." 참고: 예전 PlaceService.find, PlaceMapper.xml findById
    @Override
    public Place getPlace(String placeId) {
        return samplePlace();
    }

    /** 이름·분류 검색 */
    // TODO: ② 장소 검색. q 1~40자 검사, "분식집" → "분식". 참고: 예전 PlaceService.search, PlaceMapper.xml search
    //       주의: 예전 정렬은 "위도 차이² + 경도 차이²"(도 단위, 미터 아님) → PostGIS ORDER BY geom <-> 점, distance는 ST_Distance(m)
    @Override
    public List<PlaceCard> getPlaceList(String query, Double lat, Double lng) {
        List<PlaceCard> cards = new ArrayList<>();
        cards.add(getPlaceCard(samplePlace(), lat, lng));
        return cards;
    }

    /** 근처 장소 카드 */
    // TODO: ② 근처 장소 카드. radius를 50~2000으로 자르고 getNearbyPlaceList → 10개 → getPlaceCard. 참고: 예전 PlaceService.nearby
    @Override
    public List<PlaceCard> getNearbyCardList(double lat, double lng, int radius) {
        List<PlaceCard> cards = new ArrayList<>();
        PlaceCard card = getPlaceCard(samplePlace(), lat, lng);
        card.setDistance(240);
        cards.add(card);
        return cards;
    }

    /** 장소 상세 */
    // TODO: ② 장소 상세. 카드 + tel·imageUrl(http→https)·hours·openNow·amenities(쉼표로 나눔)·리뷰 긴 순 10개.
    //       참고: 예전 PlaceService.detail/hours, PlaceMapper.xml reviews
    @Override
    public PlaceDetail getPlaceDetail(String placeId) {
        Place place = samplePlace();
        PlaceDetail detail = new PlaceDetail();
        detail.setId(place.getId());
        detail.setName(place.getName());
        detail.setCategory(place.getCategory());
        detail.setAddress(place.getAddress());
        detail.setLat(place.getLat());
        detail.setLng(place.getLng());
        detail.setNaverUrl(getNaverUrl(place.getName(), place.getAddress()));
        detail.setUseTime("09:00~18:00");
        detail.setRestDate(place.getRestDate());
        detail.getHours().add(dayHours("매일", "09:00-18:00", true));
        detail.setOpenNow(true);
        detail.getReviews().add("한복 입고 가면 무료예요. 오전에 가면 사람이 적어요.");
        return detail;
    }

    /** 반경 안 장소, 가까운 순 */
    // TODO: ② 근처 검색. 참고: 예전 PlaceService.nearby/within, PlaceMapper.xml inBox
    //       주의: 거리 계산은 PostGIS로 — WHERE ST_DWithin(geom, 점, radius) ORDER BY geom <-> 점. Java로 다 불러와 재지 않기
    //       (예전 inBox는 평점순 200개만 가져와서, 장소가 많은 곳에서는 가까운 곳이 빠졌어요)
    @Override
    public List<Place> getNearbyPlaceList(double lat, double lng, int radius, String category) {
        List<Place> places = new ArrayList<>();
        Place place = samplePlace();
        place.setDistance(240.0);
        places.add(place);
        return places;
    }

    /** 지역 이름으로 장소 */
    // TODO: ② 지역 장소. address·name ILIKE 지역, tour·sample 먼저, 보정 평점 순 60개. 참고: 예전 PlaceService.inArea, PlaceMapper.xml inArea
    @Override
    public List<Place> getAreaPlaceList(String region) {
        List<Place> places = new ArrayList<>();
        places.add(samplePlace());
        return places;
    }

    /** 이름으로 장소 찾기 */
    // TODO: ② 이름으로 찾기. DB 이름 → (근처 2km 그 분류 1위) → placeSearchClient.searchByName(외부, id 없음) → null.
    //       참고: 예전 PlaceService.resolve/kindOf. 주의: 근처 1위도 getNearbyPlaceList(PostGIS)로
    @Override
    public Place getPlaceByName(String name, Double lat, Double lng) {
        return samplePlace();
    }

    /** 장소 → 장소 카드 */
    // TODO: ② 장소 카드. naverUrl은 map_url, 없으면 getNaverUrl. distance는 place.distance가 있으면 반올림. 참고: 예전 PlaceService.card
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
        card.setNaverUrl(getNaverUrl(place.getName(), place.getAddress()));
        if (lat != null && lng != null) {
            card.setDistance(240);
        }
        return card;
    }

    /** 영업 정보 */
    // TODO: ② 영업 정보. use_time "10:00~22:00"·rest_date "매주 화요일"을 읽어 open·closedReason·closeAt·minutesLeft·closingSoon.
    //       참고: 예전 PlaceService.openInfo/range/closedOn/openAt
    @Override
    public OpenInfo getOpenInfo(Place place, LocalDate date, int arriveMinutes, int stayMinutes) {
        OpenInfo info = new OpenInfo();
        info.setOpen(true);
        info.setCloseAt("22:00");
        info.setMinutesLeft(455);
        info.setClosingSoon(false);
        return info;
    }

    /** 실내 장소인지 */
    // TODO: ② 실내 판단. 참고: 예전 PlaceService.isIndoor (분류에 카페·음식·전시·박물관 … 이 들어가면 실내)
    @Override
    public boolean isIndoor(String category) {
        return category != null && (category.contains("카페") || category.contains("음식") || category.contains("문화"));
    }

    /** 네이버 지도 검색 링크 (예전 PlaceService.naverMapLink에서 옮김. 주소에서 "구·동"을 붙여 검색) */
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

    // ---------------------------------------------------------------- 가짜 값 (진짜 로직을 만들면 지우기)
    private static Place samplePlace() {
        Place place = new Place();
        place.setId("e70caff5-27cf-5c76-a1f3-201b7032d672");
        place.setSource("sample");
        place.setName("경복궁");
        place.setCategory("관광지");
        place.setAddress("서울 종로구 사직로 161");
        place.setLat(37.5796);
        place.setLng(126.977);
        place.setRestDate("매주 화요일");
        return place;
    }

    private static DayHours dayHours(String day, String text, boolean isToday) {
        DayHours hours = new DayHours();
        hours.setDay(day);
        hours.setText(text);
        hours.setToday(isToday);
        return hours;
    }
}
