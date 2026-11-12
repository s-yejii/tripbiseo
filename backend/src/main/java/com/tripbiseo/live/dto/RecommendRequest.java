package com.tripbiseo.live.dto;

import java.util.ArrayList;
import java.util.List;

import lombok.Data;

/** POST /live/recommend 요청 */
@Data
public class RecommendRequest {
    private Double lat;
    private Double lng;
    private Integer radius;   // 반경(m) 100~3000, 필수
    private String category;  // 장소 종류 (식당·맛집·밥 등이면 음식점 전체)
    private List<String> tags = new ArrayList<>(); // 원하는 분위기 (혼밥·조용·분위기·데이트·가성비·웨이팅없음), 선택
    private String similarTo; // 놓친 장소 id. 있으면 업종(biz_type)이 겹치는 곳만, 같은 브랜드 다른 지점 먼저 (선택, 11/12)
}
