package com.tripbiseo.trip.dto;

import java.util.ArrayList;
import java.util.List;

import lombok.Data;

/** GET /api/places/{id} 응답: 장소 카드(distance 없음) + 상세 */
@Data
public class PlaceDetail {
    private String id;
    private String name;
    private String category;
    private String address;
    private Double lat;
    private Double lng;
    private Double rating;
    private Integer reviewCount;
    private String naverUrl;
    private String tel;         // null 가능
    private String imageUrl;    // null 가능, http:// → https://
    private String useTime;     // 영업시간 원문
    private String restDate;    // 휴무 원문
    private List<DayHours> hours = new ArrayList<>(); // 정보 없으면 []
    private Boolean openNow;    // 오늘 휴무면 false, 정보 없으면 null
    private List<String> amenities = new ArrayList<>();
    private List<String> reviews = new ArrayList<>(); // 긴 순 최대 10개
}
