package com.tripbiseo.live.dto;

import lombok.Data;

/** POST /live/recommend 요청 */
@Data
public class RecommendRequest {
    private Double lat;
    private Double lng;
    private Integer radius;   // 반경(m) 100~3000, 필수
    private String category;  // 장소 종류 (식당·맛집·밥 등이면 음식점 전체)
}
