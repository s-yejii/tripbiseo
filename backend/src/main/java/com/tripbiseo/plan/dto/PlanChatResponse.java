package com.tripbiseo.plan.dto;

import lombok.Data;

/** POST /api/plans/chat 응답 */
@Data
public class PlanChatResponse {
    private String reply;               // AI 답 (없으면 "조건을 정리했어요. 확인해 주세요.")
    private PlanConditions conditions;
}
