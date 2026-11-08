package com.tripbiseo.after.dto;

import lombok.Data;

/** 회고 extraStops[]: 계획에 없는데 GPS로 15분 넘게 한 자리에 있던 곳 */
@Data
public class ExtraStop {
    private Integer day;     // 몇째 날. 예: 2
    private String name;     // 장소 이름. 아직 못 찾으면 null (나중에 근처 장소로 채움)
    private Double lat;      // 머문 자리 = 그 자리 첫 GPS 점의 위도
    private Double lng;      // 머문 자리 경도
    private Integer stay;    // 머문 분. 예: 45
    private String kind;     // visit(들른 곳) / lodging(숙소로 보이는 곳: 자정을 넘겨 머묾)
    private String arrive;   // 온 시각 HH:mm. 예: "11:15"
}
