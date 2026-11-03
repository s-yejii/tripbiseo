package com.tripbiseo.live.dto;

import lombok.Data;

/** /live/status의 stops[]: 오늘의 모든 칸 (done·skipped 포함) */
@Data
public class StatusStop {
    private Long id;
    private String placeId;
    private String name;
    private String category;
    private Double lat;
    private Double lng;
    private String time;
    private Integer stay;
    private Boolean fixed;
    private String eta;           // 남은 일정이면 예상 도착, 아니면 계획 time
    private String reach;         // done / here / closed / missed / fixed / ok / tight / no
    private String closeAt;       // 영업 정보 일부 (null 가능)
    private String closedReason;
    private Boolean closingSoon;
    private String importance;    // must / maybe / drop
    private Integer moveMin;      // 남은 칸만: 앞에서(지금 위치·앞 칸) 오는 이동 분, 아니면 null
    private String moveMode;      // 남은 칸만: walk / transit / car
    private Integer moveMeters;   // 남은 칸만: 이동 거리(m)
}
