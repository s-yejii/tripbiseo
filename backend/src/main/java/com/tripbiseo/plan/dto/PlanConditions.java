package com.tripbiseo.plan.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.Data;

/**
 * 여행 조건 (/plans/chat 응답, /plans/generate 요청).
 * title·region·mustVisit·fixed·style은 Gemini가 말하지 않으면 키가 없을 수 있어서 null인 칸은 응답에서 뺌
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PlanConditions {
    private String title;
    private String region;              // "내 위치·여기·근처"라고 하면 "내 위치"
    private String startDate;           // 없으면 오늘
    private String endDate;             // 없으면 startDate
    private Integer people;             // 없으면 1
    private List<String> transports;    // 없으면 ["subway","bus","walk"]
    private List<String> mustVisit;     // 꼭 갈 곳 이름
    private List<FixedVisit> fixed;     // 시간이 정해진 일정
    private String style;               // 여행 스타일 한 줄
    // ---- 11/03 여행 정보 화면에서 고른 값 (Gemini가 아니라 화면이 채움)
    private List<String> kinds;         // 어디 위주로: food · cafe · bar · tour (없으면 food·cafe)
    private List<String> tags;          // 이런 곳이면 더 좋아요: mood · quiet · value · no_wait · solo (가산점)
    private Double areaLat;             // 어디로 가요? 고른 지역 중심 (있으면 주소 글자 대신 이 반경에서 후보)
    private Double areaLng;
    private String areaType;            // gu(구) / dong(동) / spot(동네) → 반경 크기
    private String originName;          // 어디서 출발해요? (1일차 첫 장소는 여기서 가까운 곳부터)
    private Double originLat;
    private Double originLng;
    private List<PlanStay> stays;       // 담은 숙소 (그날 마지막 일정 = 숙소, 다음 날은 여기서 출발)
    // ---- 11/04 칩으로 못 고르는 요청 (Gemini가 말에서 뽑음 → ReviewNeedChecker가 후보 리뷰로 확인)
    private List<String> needs;         // 예: "4명이 앉을 자리", "주차되는 곳" (3개까지)
    // ---- 11/04 대화로 받는 우선순위·제한 (Gemini가 말에서 나눔, 조건 카드에서 사용자가 바꿀 수 있음)
    private List<String> wishVisit;     // 가능하면 갈 곳 이름 (시간이 되면 넣고, 못 넣으면 notes). mustVisit은 꼭 갈 곳
    private List<String> mustKinds;     // 꼭 넣을 종류 코드 ("선물 사기는 꼭" → shop). kinds에도 같이 들어감
    private List<String> avoidKinds;    // 빼 달라는 종류 코드 ("카페는 별로 안 가고 싶어" → cafe). 후보에서 뺌
    private List<String> avoidVisit;    // 피하고 싶은 곳 이름 ("롯데월드는 가 봐서 빼 줘"). 후보에서 뺌
    private FixedVisit finish;          // 여행이 끝나는 곳·도착 시각 {name, time} ("KTX 19시 광명역" → 광명역 18:30)
    private Integer maxWalkMin;         // 한 번에 걷는 시간 상한(분) ("오래 걷기 싫어" → 15)
    private List<String> asked;         // 대화에서 이미 물어본 질문 (visit · avoid · time). 서버가 다음 질문을 고를 때 씀
}
