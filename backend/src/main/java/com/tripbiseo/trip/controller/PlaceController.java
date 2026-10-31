package com.tripbiseo.trip.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.tripbiseo.trip.dto.PlaceCard;
import com.tripbiseo.trip.dto.PlaceDetail;
import com.tripbiseo.trip.service.PlaceService;

import lombok.RequiredArgsConstructor;

/** ② 장소 검색·근처·상세 (우리 DB) */
@RestController
@RequestMapping("/api/places")
@RequiredArgsConstructor
public class PlaceController {

    private static final String DEFAULT_RADIUS_M = "200";

    private final PlaceService placeService;

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

    /** 장소 상세 */
    @GetMapping("/{id}")
    public PlaceDetail getPlaceDetail(@PathVariable String id) {
        return placeService.getPlaceDetail(id);
    }
}
