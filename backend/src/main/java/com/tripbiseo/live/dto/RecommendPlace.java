package com.tripbiseo.live.dto;

import java.util.ArrayList;
import java.util.List;

import lombok.Data;

/** 추천 장소 한 곳: ②의 장소 카드(distance 항상 있음) + 영업 정보 */
@Data
public class RecommendPlace {
    private String id;
    private String name;
    private String category;
    private String address;
    private Double lat;
    private Double lng;
    private Double rating;
    private Integer reviewCount;
    private String naverUrl;
    private Integer distance;     // m
    private Boolean open;
    private String closeAt;
    private Integer minutesLeft;
    private Boolean closingSoon;
    private List<String> reasons = new ArrayList<>(); // 추천 이유 칩 (리뷰 근거). 예: "혼밥하기 좋아요 · 리뷰 15개 중 5개"
}
