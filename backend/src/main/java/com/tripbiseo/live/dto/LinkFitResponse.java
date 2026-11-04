package com.tripbiseo.live.dto;

import java.util.ArrayList;
import java.util.List;

import lombok.Data;

/** 여행 중 링크로 찾은 장소를 넣어 본 결과 (저장 안 함) */
@Data
public class LinkFitResponse {
    private boolean fits;                 // true면 다른 칸을 안 바꾸고 바로 넣어도 돼요
    private String fixedName;             // 기준이 되는 고정 일정 이름 (없으면 null)
    private String message;               // 한 줄 안내
    private Alternative best;             // 방해가 가장 적은 자리에 그대로 넣은 일정
    private List<Alternative> alternatives = new ArrayList<>(); // 고를 수 있는 방법 (추천이 맨 앞). fits이고 겹치는 곳이 없으면 빈 목록
}
