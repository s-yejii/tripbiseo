package com.tripbiseo.plan.dto;

import lombok.Data;

/** Gemini 초안의 칸 하나. id는 후보 목록의 장소 id (목록 밖이면 null → 서버가 이름으로 찾음) */
@Data
public class AiDraftStop {
    private Integer day;
    private String slot;   // 하루 칸 번호 "S1"… (11/03). 꼭 갈 곳·시간 고정 일정은 null
    private String id;
    private String name;
    private String time;   // HH:mm
    private Integer stay;  // 머무는 분
    private Boolean fixed;
    private String reason; // 고른 이유 한 줄 (Gemini) → 서버가 리뷰 근거를 붙여 일정 칸 memo로
}
