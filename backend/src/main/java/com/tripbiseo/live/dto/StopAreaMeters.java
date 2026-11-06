package com.tripbiseo.live.dto;

import lombok.Data;

/** GPS 한 점에서 경계가 있는 칸(공원 등, place_area)까지 거리 */
@Data
public class StopAreaMeters {
    private Long stopId;   // trip_stop.stop_id
    private Double meters; // 경계까지 거리(m), 경계 안이면 0
}
