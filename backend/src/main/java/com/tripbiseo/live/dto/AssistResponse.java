package com.tripbiseo.live.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.Data;

/**
 * POST /live/assist 응답. kind에 따라 같이 오는 칸이 달라서 null인 칸은 응답에서 뺌
 *   ask-radius → category / alternatives → data / text → text
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AssistResponse {
    private String kind;                 // ask-radius / alternatives / text
    private String category;             // ask-radius일 때 (기본 "카페")
    private List<String> tags;           // ask-radius일 때 원하는 분위기 (예: ["혼밥"]). 화면이 /live/recommend에 그대로 넘김
    private AlternativesResponse data;   // alternatives일 때 (reason "request")
    private String text;                 // text일 때
}
