package com.tripbiseo.common.external;

import lombok.Data;

/** CODEF 개인카드 승인내역 한 줄 (정상 승인만. 취소·거절·가려진 값은 CodefClient가 뺌) */
@Data
public class CodefApproval {
    private String usedDate;      // 사용 날짜 "20261104" (resUsedDate)
    private String usedTime;      // 사용 시각 "113500" (resUsedTime)
    private String storeName;     // 가맹점 이름 (resMemberStoreName)
    private int amount;           // 금액(원). 부분취소면 취소 금액을 뺀 값, 해외면 원화 금액
    private String approvalNo;    // 승인번호 (resApprovalNo) — 같은 결제를 두 번 가져오지 않게 쓸 수 있음
    private String storeAddress;  // 가맹점 주소 (resMemberStoreAddr, 없으면 빈 글자)
    private String storeType;     // 가맹점 업종 (resMemberStoreType, 없으면 빈 글자)
}
