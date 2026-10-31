package com.tripbiseo.live.dto;

import lombok.Data;

/** POST /live/track 요청: GPS 한 점 */
@Data
public class TrackRequest {
    private Double lat;
    private Double lng;
    private Double accuracy; // GPS 정확도(m), 선택
}
