package com.tripbiseo.live.dto;

import lombok.Data;

/** '지금 갈 수 있는 구역' 기준 */
@Data
public class AreaInfo {
    private int slackMin;     // 기준 일정까지 여유(분)
    private String basis;     // 기준 일정 이름 (고정 일정, 없으면 다음 일정)
    private String basisTime; // 기준 일정 시각
    private Double lat;       // 보낸 내 위치
    private Double lng;
}
