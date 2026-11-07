package com.tripbiseo.after.dto;

import lombok.Data;

/**
 * 카드 승인내역 한 줄 (GET /api/trips/{id}/card-transactions).
 * 지금은 서버가 만든 목업. 나중에 CODEF 카드 승인내역(resApprovalDate·resApprovalTime·resMemberStoreName·resUsedAmount)으로 바꿀 자리
 */
@Data
public class CardTransaction {
    private String approvalDate; // 승인 날짜 "20261104" (CODEF resApprovalDate 모양)
    private String approvalTime; // 승인 시각 "113500" (CODEF resApprovalTime 모양)
    private String paidAt;       // 우리 모양 "2026-11-04T11:35" → 가져올 때 그대로 POST에 씀
    private int day;             // 몇째 날
    private String storeName;    // 가맹점 이름
    private int amount;          // 금액(원)
    private boolean imported;    // 이미 결제 내역에 가져왔나 (JSON 이름: imported)
}
