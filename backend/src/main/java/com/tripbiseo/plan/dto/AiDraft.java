package com.tripbiseo.plan.dto;

import java.util.ArrayList;
import java.util.List;

import lombok.Data;

/** Gemini가 후보 장소에서 고른 일정 초안 (JSON 답 모양) */
@Data
public class AiDraft {
    private String title;
    private List<AiDraftStop> stops = new ArrayList<>();
}
