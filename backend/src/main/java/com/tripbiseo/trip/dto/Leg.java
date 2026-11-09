package com.tripbiseo.trip.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.Data;

/** 구간 (공통 데이터 Leg). /plans/check의 legs[]에는 coords가 있고, stops[].leg는 coords를 null로 두어 키가 빠짐 */
@Data
public class Leg {
    private int minutes;        // 이동 시간(분)
    private int meters;         // 이동 거리(m)
    private int walkMinutes;    // 그중 걷는 시간(분). 대중교통이면 역·정류장까지·갈아탈 때 걷는 시간 (minutes에 포함, 11/04)
    private String mode;        // walk / transit / car (Stop.mode의 taxi는 car)
    private String source;      // tmap / odsay / estimate
    private String detail;      // 탈 노선 설명 (대중교통만, null 가능)
    private String fromName;    // 첫 장소 앞 길안내만: 어디서 오는지 (출발지·숙소 이름, 11/09)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private List<double[]> coords; // 경로선 [[위도, 경도], ...]
}
