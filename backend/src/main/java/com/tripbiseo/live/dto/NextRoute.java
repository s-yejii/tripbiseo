package com.tripbiseo.live.dto;

import java.util.ArrayList;
import java.util.List;

import lombok.Data;

/** 내 위치 → 다음 남은 일정 */
@Data
public class NextRoute {
    private Long id;
    private int minutes;
    private List<double[]> coords = new ArrayList<>(); // [[위도, 경도], ...]
    private String source; // tmap / odsay / estimate
}
