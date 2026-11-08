package com.tripbiseo.live.dto;

import lombok.Data;

/** AI에게 묻기로 찾은 장소 한 곳 (화면 카드·지도 핀) */
@Data
public class AskedPlace {
    private String id;
    private String name;
    private String kind;           // cafe / food / bar …
    private String category;       // 세부 종류 (예: 카페,디저트)
    private Double lat;
    private Double lng;
    private Double rating;         // 보정 평점
    private Integer reviewCount;
    private String useTime;        // 영업시간 원문 (영업 중인지는 서버가 봄)
    private String restDate;       // 휴무 원문
    private Integer detourMinutes; // 가는 길에서 몇 분 더 (가는 길이 없으면 null)
    private Double joinLat;        // 가는 길 위에서 이 장소와 가장 가까운 점 = 지도 점선 시작
    private Double joinLng;
}
