package com.tripbiseo.live.dto;

import lombok.Data;

/** POST /live/alternatives 요청 */
@Data
public class AlternativesRequest {
    private Double lat;
    private Double lng;
    private String reason = "delay"; // 이유 (Gemini에게 그대로 전달)
    private String request;          // 사용자 요청. "비·실내·추워·더워·날씨"가 들어가면 실내 위주
}
