package com.tripbiseo.trip.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.tripbiseo.common.auth.LoginUser;
import com.tripbiseo.common.external.FoundArea;
import com.tripbiseo.common.external.FoundLocation;
import com.tripbiseo.trip.dto.PlaceCard;
import com.tripbiseo.trip.dto.PlaceDetail;
import com.tripbiseo.trip.dto.PopularResponse;
import com.tripbiseo.trip.service.PlaceService;
import com.tripbiseo.trip.service.PopularPlaceService;

import lombok.RequiredArgsConstructor;

/** ② 장소 검색·근처·상세 (우리 DB) */
@RestController
@RequestMapping("/api/places")
@RequiredArgsConstructor
public class PlaceController {

    private static final String DEFAULT_RADIUS_M = "200";

    private static final String DEFAULT_POPULAR_COUNT = "6";

    private final PlaceService placeService;
    private final PopularPlaceService popularPlaceService;
    private final com.tripbiseo.trip.service.PlacePhotoService placePhotoService;
    private final LoginUser loginUser;

    /** 이름·분류로 장소 검색. q가 없으면 400 "q 값이 필요해요." */
    @GetMapping("/search")
    public List<PlaceCard> getPlaceList(@RequestParam String q, @RequestParam(required = false) Double lat,
                                        @RequestParam(required = false) Double lng) {
        return placeService.getPlaceList(q, lat, lng);
    }

    /** 근처 장소 (반경 기본 200m) */
    @GetMapping("/nearby")
    public List<PlaceCard> getNearbyCardList(@RequestParam double lat, @RequestParam double lng,
                                             @RequestParam(defaultValue = DEFAULT_RADIUS_M) int radius) {
        return placeService.getNearbyCardList(lat, lng, radius);
    }

    /** 요즘 인기 장소 (lat·lng 없으면 시범 지역 전체, radius 없으면 3km → 5km → 전체로 넓힘) */
    @GetMapping("/popular")
    public PopularResponse getPopularPlaceList(@RequestParam(required = false) Double lat, @RequestParam(required = false) Double lng,
                                               @RequestParam(required = false) Integer radius,
                                               @RequestParam(defaultValue = DEFAULT_POPULAR_COUNT) int limit) {
        return popularPlaceService.getPopularPlaceList(loginUser.id(), lat, lng, radius, limit);
    }

    /** 장소들의 사진 (?ids=a,b,c). 찾은 곳만 돌려줌 */
    @GetMapping("/photos")
    public List<com.tripbiseo.trip.dto.PlacePhoto> getPlacePhotoList(@RequestParam List<String> ids) {
        return placePhotoService.getPlacePhotoList(ids);
    }

    /** 한 일정의 장소들 사진 (같은 일정 안에서 같은 사진은 안 씀) */
    @org.springframework.web.bind.annotation.PostMapping("/photos/trip")
    public List<com.tripbiseo.trip.dto.PlacePhoto> getTripPhotoList(@org.springframework.web.bind.annotation.RequestBody List<com.tripbiseo.trip.dto.TripPhotoRequest> stops) {
        return placePhotoService.getTripPhotoList(stops);
    }

    /** 어디로 가요? 서울 구·동 찾기 (카카오) */
    @GetMapping("/areas")
    public List<FoundArea> getAreaList(@RequestParam String q) {
        return placeService.getAreaList(q);
    }

    /** 어디서 출발해요? 역·건물·주소 찾기 (카카오). lat·lng가 있으면 거리도 */
    @GetMapping("/locations")
    public List<FoundLocation> getLocationList(@RequestParam String q, @RequestParam(required = false) Double lat,
                                               @RequestParam(required = false) Double lng) {
        return placeService.getLocationList(q, lat, lng);
    }

    /** 출발지 추천: 좌표 근처 지하철역·구청·시청 */
    @GetMapping("/locations/hubs")
    public List<FoundLocation> getLocationHubList(@RequestParam double lat, @RequestParam double lng) {
        return placeService.getLocationHubList(lat, lng);
    }

    /** 현재 위치로 출발: 내 좌표의 건물 이름·주소 */
    @GetMapping("/locations/here")
    public FoundLocation getLocationHere(@RequestParam double lat, @RequestParam double lng) {
        return placeService.getLocationHere(lat, lng);
    }

    /** 숙소 위치 찍기: 숙소(호텔·모텔·게스트하우스 등)만 찾기 (카카오). 없으면 주소 */
    @GetMapping("/locations/stays")
    public List<FoundLocation> getLocationStayList(@RequestParam String q) {
        return placeService.getLocationStayList(q);
    }

    /** 장소 상세 */
    @GetMapping("/{id}")
    public PlaceDetail getPlaceDetail(@PathVariable String id) {
        return placeService.getPlaceDetail(id);
    }
}
