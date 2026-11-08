package com.tripbiseo.after.dto;

import lombok.Data;

/** POST·PUT /api/trips/{id}/companions 요청: 이름 하나 */
@Data
public class CompanionRequest {
    private String name; // 1~20자
}
