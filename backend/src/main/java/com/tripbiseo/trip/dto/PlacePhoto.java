package com.tripbiseo.trip.dto;

import lombok.Data;

/** 장소 사진 하나 (11/04). 파일을 저장하지 않고 주소와 출처만 알려 줘요 */
@Data
public class PlacePhoto {
    private String placeId;
    private String imageUrl;  // 사진 주소
    private String siteName;  // 출처 이름 (블로그 이름). DB 사진이면 null
    private String linkUrl;   // 원문 주소. DB 사진이면 null
    private Integer step;     // 일정 사진을 찾은 단계: 1 DB, 2 TourAPI, 3 블로그
    private String title;     // 블로그 글 제목 (3단계일 때)
}
