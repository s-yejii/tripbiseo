package com.tripbiseo.plan.dto;

import lombok.Data;

/** POST /api/plans/capture/receipt 응답: 영수증에서 읽은 결제 후보 (저장 안 함, ⑤ 결제 메모가 확인 후 저장) */
@Data
public class ReceiptResponse {
    private String storeName;
    private String paidAt;       // yyyy-MM-dd HH:mm, 못 읽으면 null
    private Integer totalAmount; // 원, 못 읽으면 null
    private String address;
    private String placeId;      // DB 장소 id, 못 찾으면 null
    private Double lat;
    private Double lng;
    private boolean checkNeeded; // 사람이 꼭 확인해야 하는지 (금액·시각을 못 읽었거나 품목 합이 다름)
    private String note;         // checkNeeded 이유 한 줄
}
