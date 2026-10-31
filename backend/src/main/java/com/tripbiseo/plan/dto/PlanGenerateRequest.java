package com.tripbiseo.plan.dto;

import java.util.ArrayList;
import java.util.List;

import lombok.Data;

/** POST /api/plans/generate 요청 */
@Data
public class PlanGenerateRequest {
    private PlanConditions conditions;                 // 필수
    private List<String> messages = new ArrayList<>(); // 사용자가 한 말 (Gemini 참고용)
    private Double lat;                                // 지금 GPS (Gemini에는 보내지 않음, 서버가 근처 후보를 고를 때만)
    private Double lng;
}
