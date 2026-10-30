package com.tripbiseo.live.dto;

import lombok.Data;

/** POST /live/insert 요청: 추천 장소를 다음 일정으로 */
@Data
public class InsertRequest {
    private String placeId; // 장소 카드의 id
    private Double lat;
    private Double lng;
}
