package com.tripbiseo.after.dto;

import lombok.Data;

/** POST /api/trips/{id}/payments 요청: 결제 하나 */
@Data
public class PaymentRequest {
    private String paidAt;    // "2026-11-04T11:35" (yyyy-MM-ddTHH:mm)
    private String storeName; // 가게 이름
    private Integer amount;   // 금액(원), 1 이상
    private String method;    // card / cash (없으면 card)
    private String source;    // manual / card / capture (없으면 manual)
    private String memo;      // 메모 (없어도 됨)
    private String payer;     // 낸 사람 (없으면 "나")
}
