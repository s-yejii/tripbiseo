package com.tripbiseo.after.dto;

import lombok.Data;

/** 결제 하나 (화면에 보낼 모양) */
@Data
public class PaymentResponse {
    private Long id;
    private Long stopId;      // 결제 시각에 있던 일정 칸. null이면 이동 중·계획 밖
    private int day;          // 몇째 날 = 결제 날짜 − 여행 시작 날짜 + 1
    private String paidAt;    // HH:mm
    private String storeName;
    private int amount;
    private String method;    // card / cash
    private String source;    // manual / card / capture
    private String memo;
    private String payer;     // 낸 사람: 나 / 동행 1 / 동행 2 …
}
