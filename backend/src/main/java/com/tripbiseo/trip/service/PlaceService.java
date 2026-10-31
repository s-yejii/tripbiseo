package com.tripbiseo.trip.service;

import java.time.LocalDate;
import java.util.List;

import com.tripbiseo.trip.dto.OpenInfo;
import com.tripbiseo.trip.dto.Place;
import com.tripbiseo.trip.dto.PlaceCard;
import com.tripbiseo.trip.dto.PlaceDetail;

/**
 * ② 장소 (retrip_place, retrip_review).
 * ③④가 부르는 공유 메서드: getPlace, getNearbyPlaceList, getAreaPlaceList, getPlaceByName, getPlaceCard, getOpenInfo, isIndoor, getNaverUrl
 */
public interface PlaceService {

    /** 장소 하나. 없으면 404 "장소를 찾을 수 없어요." */
    Place getPlace(String placeId);

    /** GET /api/places/search: 이름·분류 검색 최대 20개. lat·lng가 있으면 가까운 순 + distance */
    List<PlaceCard> getPlaceList(String query, Double lat, Double lng);

    /** GET /api/places/nearby: 근처 장소 카드 거리순 최대 10개 (반경 50~2000m로 자름) */
    List<PlaceCard> getNearbyCardList(double lat, double lng, int radius);

    /** GET /api/places/{id}: 장소 상세 (영업시간·리뷰) */
    PlaceDetail getPlaceDetail(String placeId);

    /** 반경(m) 안 장소, 가까운 순 (distance 채움). category가 있으면 분류에 그 말이 들어간 곳만 (④ 추천·대안, ③ 내 위치 일정) */
    List<Place> getNearbyPlaceList(double lat, double lng, int radius, String category);

    /** 주소·이름에 지역 이름이 들어간 장소, 평점 순 (③ 일정 만들기 후보) */
    List<Place> getAreaPlaceList(String region);

    /** 이름으로 장소 찾기: 우리 DB → TourAPI → 카카오. 외부에서 찾으면 id가 null. 못 찾으면 null (③) */
    Place getPlaceByName(String name, Double lat, Double lng);

    /** 장소 → 장소 카드. lat·lng가 있으면 distance 계산 */
    PlaceCard getPlaceCard(Place place, Double lat, Double lng);

    /** 그날 arriveMinutes(자정부터 센 분)에 도착해 stayMinutes 머물 때의 영업 정보 */
    OpenInfo getOpenInfo(Place place, LocalDate date, int arriveMinutes, int stayMinutes);

    /** 실내 장소인지 (카페·식당·전시 …). 비 올 때 대안에 씀 */
    boolean isIndoor(String category);

    /** DB에 없는 장소용 네이버 지도 검색 링크 */
    String getNaverUrl(String name, String address);
}
