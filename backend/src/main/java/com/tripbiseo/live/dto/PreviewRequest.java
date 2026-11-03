package com.tripbiseo.live.dto;

import java.util.List;

import lombok.Data;

/** POST /live/preview 요청: 사용자가 고친 남은 일정 (계산만 하고 저장 안 함) */
@Data
public class PreviewRequest {
    private Double lat;           // 지금 위치
    private Double lng;
    private List<AltStop> stops;  // 갈 순서대로. id가 있으면 남은 칸(stay만 바꿈), 없으면 새 장소. 목록에 없는 남은 칸은 뺀 것으로 봄
}
