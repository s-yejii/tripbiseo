package com.tripbiseo.trip.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.Data;

/** 장소 카드 (공통 데이터). 장소 검색·근처·상세·여행 중 추천이 모두 이 모양을 기본으로 씀 */
@Data
public class PlaceCard {
    private String id;
    private String name;
    private String category;
    private String address;      // null 가능
    private Double lat;
    private Double lng;
    private Double rating;       // null 가능
    private Integer reviewCount; // null 가능
    private String naverUrl;     // DB map_url, 없으면 네이버 지도 검색 링크
    @JsonInclude(JsonInclude.Include.NON_NULL) // 기준 좌표가 있을 때만 키가 생김
    private Integer distance;
}
