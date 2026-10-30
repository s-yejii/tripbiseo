package com.tripbiseo.live.dto;

import java.util.ArrayList;
import java.util.List;

import lombok.Data;

/** POST /live/alternatives 응답 */
@Data
public class AlternativesResponse {
    private String title;     // 기본 "{고정 일정}에 늦지 않는 대안" 또는 "남은 일정 대안"
    private String subtitle;  // 기본 "지금 위치·시각에서 다시 계산했어요"
    private String weather;   // Gemini가 날씨를 봤을 때 한 줄, null 가능
    private String source;    // rule / gemini / gemini+rule
    private List<Alternative> alternatives = new ArrayList<>(); // 1~3개
}
