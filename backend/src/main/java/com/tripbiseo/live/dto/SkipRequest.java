package com.tripbiseo.live.dto;

import lombok.Data;

/** POST /live/skip 요청: 못 가게 된 칸 하나 빼기 (11/12) */
@Data
public class SkipRequest {
    private Long stopId;
    private Double lat;
    private Double lng;
}
