package com.tripbiseo.plan.dto;

import lombok.Data;

/** 후보 장소 리뷰 한 개 (요청과 관련된 단어가 들어간 리뷰만 꺼냄, 11/04) */
@Data
public class ReviewSnippet {
    private String placeId;
    private String content;
}
