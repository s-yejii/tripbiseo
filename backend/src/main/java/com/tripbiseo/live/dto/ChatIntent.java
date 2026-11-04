package com.tripbiseo.live.dto;

import java.util.ArrayList;
import java.util.List;

import lombok.Data;

/** 여행 중 채팅 말 분류 결과 (규칙 또는 Gemini JSON이 이 모양) */
@Data
public class ChatIntent {
    private String kind;                         // recommend / change / question
    private String category;                     // recommend일 때 장소 종류: 카페 / 디저트 / 식당 / 술집
    private List<String> tags = new ArrayList<>(); // recommend일 때 원하는 분위기: 혼밥 / 조용 / 분위기 / 데이트 / 가성비 / 웨이팅없음
}
