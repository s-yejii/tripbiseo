package com.tripbiseo.plan.dto;

import lombok.Data;

/** 인스타 링크로 장소 찾기 요청 */
@Data
public class LinkRequest {
    private String url;     // 인스타 게시물·릴스 주소
    private String caption; // 사용자가 복사해 붙인 게시물 글 (선택. 인스타가 막으면 이 글로 찾음)
}
