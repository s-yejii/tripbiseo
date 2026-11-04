package com.tripbiseo.plan.dto;

import java.util.List;

import lombok.Data;

/** Gemini가 게시물 글에서 고른 장소 이름 (JSON 답 모양) */
@Data
public class AiLinkPlaces {
    private List<String> places;
}
