package com.tripbiseo.common.external;

import java.util.ArrayList;
import java.util.List;

import lombok.Data;

/** 길 안내 결과 (GET /api/route 응답). 못 찾으면 available=false + message */
@Data
public class RouteGuide {
    private String mode;          // walk / bus / subway / car
    private boolean available;    // 이 수단으로 길을 찾았나
    private String message;       // 못 찾았을 때 이유
    private String source;        // tmap / odsay
    private int minutes;
    private int meters;
    private Integer fare;         // 택시는 예상 택시비, 대중교통은 요금 (모르면 null)
    private List<double[]> coords = new ArrayList<>(); // 경로선 [[위도, 경도], ...]
    private List<GuideStep> steps = new ArrayList<>();
}
