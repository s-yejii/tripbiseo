package com.tripbiseo.live.dto;

import java.util.ArrayList;
import java.util.List;

import lombok.Data;

/** POST /live/status 응답: 지금 상황 (알림·여유·날씨) */
@Data
public class StatusResponse {
    private String now;            // 지금 시각 HH:mm
    private Long currentStopId;    // 지금 있는 칸 id, null 가능
    private LiveAlert alert;       // null 가능
    private LeaveInfo leave;       // 출발 ±10분 안일 때만, 아니면 null
    private FixedStatus fixed;     // null 가능
    private String hint;           // 고정 일정에 늦을 때 조언 한 줄, null 가능
    private boolean replan;        // 다시 짜는 게 좋은가
    private AreaInfo area;         // 남은 일정이 없으면 null
    private WeatherInfo weather;   // 예보가 없거나 실패하면 null
    private List<StatusStop> stops = new ArrayList<>();
    private NextRoute next;        // null 가능
}
