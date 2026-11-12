package com.tripbiseo.plan.dto;

import lombok.Data;

/** Gemini가 영수증 사진에서 읽은 값 (JSON 답 모양). 안 보이면 null */
@Data
public class AiReceipt {
    private String storeName;
    private String paidDate;     // YYYY-MM-DD
    private String paidTime;     // HH:mm
    private String totalAmount;  // 합계(결제 금액). "150,000"처럼 올 수 있어서 글자로 받고 서버가 숫자로 바꿈
    private String address;
    private Boolean isSumDifferent; // 품목 금액을 더한 값이 합계와 다르면 true
}
