package com.tripbiseo.live.dto;

import lombok.Data;

/** POST /live/status 요청: 지금 위치. lat·lng가 숫자가 아니거나 없으면 400 "내 위치를 먼저 찾아 주세요." */
@Data
public class LocationRequest {
    private Double lat;
    private Double lng;
}
