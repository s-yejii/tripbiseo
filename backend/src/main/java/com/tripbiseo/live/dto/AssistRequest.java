package com.tripbiseo.live.dto;

import lombok.Data;

/** POST /live/assist 요청: 여행 중 채팅 한 마디 */
@Data
public class AssistRequest {
    private String message; // 1~300자
    private Double lat;
    private Double lng;
}
