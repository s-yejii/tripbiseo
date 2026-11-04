package com.tripbiseo.live.dto;

import lombok.Data;

/** GET /live/swap-candidates 응답 한 줄: 장소 카드 + 그 장소로 바꿨을 때 계산 */
@Data
public class SwapCandidate {
    private String id;            // 장소 id (retrip_place.id)
    private String name;
    private String category;
    private String address;
    private Double lat;
    private Double lng;
    private Double rating;
    private Integer reviewCount;
    private String naverUrl;
    private Integer distance;     // 바꿀 칸에서 거리(m)
    private Integer moveMin;      // 앞 장소에서 이 장소까지 이동 분
    private String eta;           // 이 장소 예상 도착
    private String finalArrive;   // 바꾸면 고정 일정(없으면 마지막 칸) 예상 도착
    private int slackMin;         // 바꾸면 여유(분), 음수면 지각
}
