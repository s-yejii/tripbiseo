package com.tripbiseo.plan.dto;

import lombok.Data;

/** 추천 게시물 한 칸 (resources/plan/recommended-links.json 한 줄) */
@Data
public class RecommendedLink {
    private String platform; // instagram · youtube · threads
    private String title;
    private String area;     // 지역 이름 (예: 문래동)
    private String url;      // 게시물 주소
    private String imageUrl; // 미리보기 사진. 없으면 null
}
