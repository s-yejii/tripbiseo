package com.tripbiseo.live.dto;

import java.util.ArrayList;
import java.util.List;

import lombok.Data;

/** POST /live/apply 응답 */
@Data
public class ApplyResponse {
    private String key;
    private String message;    // "일정을 바꿨어요!" 또는 "{고정 일정}에 늦지 않게 일정을 바꿨어요!"
    private String nextAlert;  // null 가능
    private List<AppliedStop> stops = new ArrayList<>();
}
