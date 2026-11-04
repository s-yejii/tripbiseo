package com.tripbiseo.trip.dto;

import lombok.Data;

/** POST /api/places/photos/trip 요청 한 칸: 일정 칸의 이름·주소로 사진을 찾아요 (장소 번호가 없는 칸도 있어서) */
@Data
public class TripPhotoRequest {
    private String key;     // 응답의 placeId 칸에 그대로 돌려줄 값 (화면이 칸을 찾는 데 씀)
    private String name;
    private String address;
    private String placeId;  // 없을 수 있어요
    private String category;
    private Double lat;
    private Double lng;
}
