package com.tripbiseo.after.dto;

import lombok.Data;

/** payment 테이블 한 줄 (DB에 넣을 때 씀) */
@Data
public class Payment {
    private Long paymentId;   // 저장하면 DB가 만든 번호가 들어옴
    private Long tripId;
    private Long stopId;      // 결제 시각에 있던 일정 칸. 이동 중·계획 밖이면 null
    private String paidAt;    // 결제 시각 "2026-11-04T11:35"
    private String storeName; // 가게 이름
    private Integer amount;   // 금액(원)
    private String method;    // card / cash
    private String source;    // manual / card / capture
    private String memo;
}
