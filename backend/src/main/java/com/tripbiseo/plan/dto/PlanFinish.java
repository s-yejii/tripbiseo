package com.tripbiseo.plan.dto;

import lombok.Data;

/** 끝 장소를 찾은 결과 (서버 안에서만 씀, 11/04): 어디서 몇 시까지 + 마지막 날 일정이 끝나야 하는 시각 */
@Data
public class PlanFinish {
    private String name;        // 카카오에서 찾은 이름 (못 찾으면 사용자가 말한 이름)
    private String time;        // 도착해야 하는 시각 HH:mm
    private Double lat;         // 못 찾으면 null
    private Double lng;
    private String address;
    private int travelMin;      // 지역 중심 → 끝 장소 이동 분 (못 찾으면 추정값)
    private int lastEndMin;     // 마지막 날 일정이 이 시각(0시부터 분)까지 끝나야 함
    private boolean found;      // 끝 장소 위치를 찾았는지 (Lombok이 isFound()를 만들어 줌)
}
