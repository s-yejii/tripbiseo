package com.tripbiseo.plan.dto;

import lombok.Data;

/** 일정 초안 안내 문구 {text, url} */
@Data
public class PlanNote {
    private String text;
    private String url; // 네이버 지도 검색 링크, AI 실패 안내는 null
}
