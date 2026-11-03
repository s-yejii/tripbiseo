package com.tripbiseo.trip.dto;

import lombok.Data;

/** 인기 장소 하나: 장소 카드 + 다녀간 사람 수 + 보여 줄 이유 한 줄 */
@Data
public class PopularPlace {
    private PlaceCard place;
    private Integer visitors; // 최근 30일 다녀간 다른 회원 수 (5명 미만이면 null)
    private String reason;    // "최근 한 달 12명이 다녀갔어요" 또는 "★4.6 · 리뷰 1,234"
}
