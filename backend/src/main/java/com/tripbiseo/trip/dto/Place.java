package com.tripbiseo.trip.dto;

import lombok.Data;

/** 우리 DB 장소 (retrip_place 한 줄). 화면에는 PlaceCard·PlaceDetail로 바꿔서 보냄 */
@Data
public class Place {
    private String id;          // DB에 없는 외부 장소(TourAPI·카카오)면 null
    private String source;      // naver / tour / sample
    private String name;
    private String category;    // biz_type, 없으면 관광공사 분류
    private String address;
    private String tel;
    private Double lat;         // DB 컬럼은 latitude
    private Double lng;         // DB 컬럼은 longitude
    private String useTime;     // 영업시간 원문
    private String restDate;    // 휴무 원문
    private String imageUrl;
    private Double rating;
    private Integer reviewCount;
    private String amenities;   // 편의시설 "포장, 예약, …"
    private String mapUrl;      // 네이버 지도 주소
    private Double distance;    // 기준점에서 거리(m). 근처 검색 SQL이 채움
}
