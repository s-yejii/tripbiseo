package com.tripbiseo.after.dto;

import lombok.Data;

/** PUT /api/trips/{id}/payments/{paymentId}/payer 요청: 낸 사람 바꾸기 */
@Data
public class PayerRequest {
    private String payer; // 나 / 동행 1 / 동행 2 …
}
