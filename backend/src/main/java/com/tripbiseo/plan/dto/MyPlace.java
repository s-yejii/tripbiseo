package com.tripbiseo.plan.dto;

import lombok.Data;

/** 내 장소 한 곳 (GET /api/my/places). group은 저장하지 않고 장소 분류로 정해요 */
@Data
public class MyPlace {
    private long id;            // my_place_id
    private String placeId;     // retrip_place.id
    private String name;
    private String category;    // 가게 분류 원문 (예: 일본식라면, 카페,디저트)
    private String group;       // 식당 · 카페 · 놀거리
    private String address;
    private Double lat;
    private Double lng;
    private Double rating;
    private Integer reviewCount;
    private String imageUrl;
    private String fromName;    // 누구 소식에서 저장했는지 (없으면 null)
    private String createdAt;   // yyyy-MM-dd HH:mm
}
