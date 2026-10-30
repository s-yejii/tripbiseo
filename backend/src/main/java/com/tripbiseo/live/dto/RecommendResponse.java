package com.tripbiseo.live.dto;

import java.util.ArrayList;
import java.util.List;

import lombok.Data;

/** POST /live/recommend 응답 */
@Data
public class RecommendResponse {
    private List<RecommendPlace> places = new ArrayList<>(); // 최대 3개, 리뷰 수로 보정한 평점 순
    private int closedSkipped;   // 평점 없는 곳·도착 때 닫힌 곳이라 뺀 수
}
