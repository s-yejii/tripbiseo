package com.tripbiseo.plan.dto;

import java.util.ArrayList;
import java.util.List;

import com.tripbiseo.trip.dto.PlaceCard;

import lombok.Data;

/** 인스타 링크에서 찾은 장소 (저장 안 함. 화면에서 고른 곳만 일정 조건에 넣음) */
@Data
public class LinkResponse {
    private String source;                              // page(인스타 글을 읽음) / caption(붙여 넣은 글만 씀)
    private List<PlaceCard> places = new ArrayList<>(); // 실제로 찾은 장소. DB에 없는 곳(TourAPI·카카오)은 id가 null
    private List<String> missed = new ArrayList<>();    // 글에는 있었지만 장소를 찾지 못한 이름
}
