package com.tripbiseo.live.dto;

import lombok.Data;

/** 출발 시각 ±10분 안일 때만 오는 출발 안내 */
@Data
public class LeaveInfo {
    private Long stopId;      // 지금 있는 칸 또는 다음 칸 id
    private String name;
    private String leaveAt;   // HH:mm
    private int minutesLeft;  // −10~10 (음수 = 지남)
    private String to;        // 다음 장소 이름, null 가능
    private boolean atStop;   // true면 지금 있는 곳에서 출발
}
