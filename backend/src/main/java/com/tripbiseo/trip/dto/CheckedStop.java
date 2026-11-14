package com.tripbiseo.trip.dto;

import lombok.Data;

/** 일정 점검 stops[]: 보낸 칸 값 + 예상 도착·문제·구간 */
@Data
public class CheckedStop {
    private Long id;
    private Integer day;
    private String placeId;
    private String name;
    private String category;
    private Double lat;
    private Double lng;
    private String time;    // 계획 시각 (비어 있었으면 서버가 채운 값)
    private Integer stay;
    private Boolean fixed;
    private String mode;
    private String arrive;  // 예상 도착 HH:mm
    private String issue;   // 문제 한 줄, 없으면 null
    private Boolean hoursUnknown; // 영업시간 정보가 없으면 true (문제는 아니고 "확인 전" 안내용, 11/15)
    private String naverUrl;      // 네이버 지도 주소: 장소 map_url, 없으면 이름·주소 검색 링크. DB 장소가 아니면 null
    private Leg leg;        // 앞 장소에서 오는 구간 (coords 없음). 첫 칸은 null
}
