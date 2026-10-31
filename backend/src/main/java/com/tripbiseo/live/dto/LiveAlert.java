package com.tripbiseo.live.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.Data;

/** 화면 맨 위 알림 1개 */
@Data
public class LiveAlert {
    private String key;     // 중복 알림 방지 키. 앞부분 overdue- / closed- / late- / leave- / missed- / tight- / next-
    private String level;   // danger / warn
    private String title;
    private String text;
    @JsonInclude(JsonInclude.Include.NON_NULL) // leave- 알림에만 true, 그 밖에는 키 없음
    private Boolean repeat;
}
