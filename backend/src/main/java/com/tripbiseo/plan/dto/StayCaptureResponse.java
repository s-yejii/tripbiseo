package com.tripbiseo.plan.dto;

import lombok.Data;

/**
 * POST /api/plans/capture/stay 응답: 숙소 예약 캡처에서 읽은 숙소 후보. 저장하지 않아요 — 화면에서 확인·수정한 뒤 숙소로 넣어요.
 * 주소(없으면 이름)로 카카오에서 위치를 찾으면 lat·lng가 있고, 못 찾으면 null (화면에서 지도를 눌러 정함).
 */
@Data
public class StayCaptureResponse {
    private String name;
    private String address;       // 사진의 주소, 없으면 찾은 위치의 주소
    private String checkInDate;   // yyyy-MM-dd, 못 읽으면 null
    private String checkInTime;   // HH:mm, 못 읽으면 null
    private String checkOutDate;
    private String checkOutTime;
    private Double lat;
    private Double lng;
}
