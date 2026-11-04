package com.tripbiseo.plan.dto;

import lombok.Data;

/** POST /api/plans/chat 응답 */
@Data
public class PlanChatResponse {
    private String reply;               // AI 답 (없으면 "조건을 정리했어요. 확인해 주세요.")
    private PlanConditions conditions;
    private String question;            // 다음 질문 (서버가 비어 있는 조건을 보고 고름, 11/04)
}
