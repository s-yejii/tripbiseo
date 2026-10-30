package com.tripbiseo.plan.dto;

import lombok.Data;

/** POST /api/plans/chat 요청 */
@Data
public class PlanChatRequest {
    private String message;             // 사용자 말 1~300자
    private PlanConditions conditions;  // 지금까지의 조건, 처음이면 null
}
