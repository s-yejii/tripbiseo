package com.tripbiseo.common.external;

import java.util.List;

import lombok.Data;

/** 두 지점 사이 길찾기 결과 (RouteClient가 만듦). 화면 응답의 Leg로 옮겨 담아 씀 */
@Data
public class Route {
    private int minutes;          // 이동 시간(분)
    private int meters;           // 이동 거리(m)
    private int walkMeters;       // 그중 걷는 거리(m)
    private int walkMinutes;      // 그중 걷는 시간(분). 대중교통이면 역·정류장까지 걷기 + 갈아타기 걷기 (11/04)
    private List<double[]> coords; // 경로선 [[위도, 경도], ...]
    private String mode;          // walk / transit / car
    private String source;        // tmap / odsay / estimate
    private String detail;        // 탈 노선 설명 (대중교통만, 없으면 null)
}
