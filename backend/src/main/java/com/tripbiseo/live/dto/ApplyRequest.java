package com.tripbiseo.live.dto;

import java.util.ArrayList;
import java.util.List;

import lombok.Data;

/** POST /live/apply 요청: /live/alternatives의 대안 하나 그대로 + lat·lng (모르는 키는 무시) */
@Data
public class ApplyRequest {
    private String key;
    private List<AltStop> stops = new ArrayList<>(); // 1개 이상
    private Double lat;
    private Double lng;
}
