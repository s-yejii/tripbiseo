package com.tripbiseo.dto;

/** 우리 DB 장소 (retrip_place) */
public class Place {
    public String id;
    public String source;      // naver / tour / sample
    public String name;
    public String category;    // biz_type
    public String address;
    public String tel;
    public Double lat;
    public Double lng;
    public String useTime;     // 영업시간 원문
    public String restDate;    // 휴무 원문
    public String imageUrl;
    public Double rating;
    public Integer reviewCount;
    public String amenities;
    public String mapUrl;      // 네이버 지도 주소
    public Double distance;    // 기준점에서 거리(m), 검색할 때만
}
