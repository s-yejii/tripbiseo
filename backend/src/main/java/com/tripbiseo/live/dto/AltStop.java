package com.tripbiseo.live.dto;

import lombok.Data;

/** 대안 한 개의 일정 칸 (/live/alternatives 응답, /live/apply 요청에 그대로 돌아옴) */
@Data
public class AltStop {
    private Long id;            // 남은 일정 칸 id, 새 장소면 null
    private String placeId;
    private String name;
    private String category;
    private Double lat;
    private Double lng;
    private Integer stay;
    private Boolean fixed;
    private String eta;         // 고정 일정은 계획 시각
    private Integer moveMin;    // 앞에서 오는 이동 분
    private String change;      // "60분 → 30분" 또는 "새로 추가", 안 바뀌면 null
    private String closedReason;
    private String closeAt;
    private Boolean closingSoon;
    private String naverUrl;    // DB에 없는 새 장소일 때만
}
