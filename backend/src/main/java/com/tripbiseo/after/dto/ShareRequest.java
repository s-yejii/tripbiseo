package com.tripbiseo.after.dto;

import lombok.Data;

/** PUT /api/trips/{id}/companions/{companionId}/share 요청: 더치페이 낼 돈. null이면 자동으로 되돌림 */
@Data
public class ShareRequest {
    private Integer amount; // 0 이상, null = 자동
}
