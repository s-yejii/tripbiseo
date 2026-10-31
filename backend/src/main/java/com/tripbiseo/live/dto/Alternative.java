package com.tripbiseo.live.dto;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Data;

/** 대안 하나 (A 시간 단축 / B 한 곳 빼기 / C 장소 변경 …) */
@Data
public class Alternative {
    private String key;          // "A"·"B"·"C" (순서대로 다시 붙임)
    @JsonProperty("short")       // short는 Java 예약어라 필드 이름을 shortName으로 두고 JSON 키만 short
    private String shortName;    // 탭 이름
    private String title;
    private String reason;       // 이유 한 문장
    private boolean recommended; // 정확히 하나만 true
    private String finalArrive;  // 고정 일정(없으면 마지막 일정) 예상 도착
    private int slackMin;
    private int closedCount;     // 도착 때 닫힌 곳 수
    private int changes;         // 바뀌는 칸 수
    private List<double[]> coords = new ArrayList<>(); // 전체 경로선
    private List<AltStop> stops = new ArrayList<>();
}
