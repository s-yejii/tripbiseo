package com.tripbiseo.plan.dto;

import lombok.Data;

/**
 * POST /api/plans/capture 응답: 예약 캡처에서 읽은 고정 일정 후보. 저장하지 않아요 — 화면에서 사용자가 확인하고 고정 일정으로 넣어요.
 * 우리 DB에서 같은 이름의 장소를 찾으면 placeId·좌표가 있고, 못 찾으면 null.
 */
@Data
public class CaptureResponse {
    private String name;      // 장소·공연 이름
    private String date;      // yyyy-MM-dd, 못 읽으면 null
    private String time;      // HH:mm, 못 읽으면 null
    private String placeId;   // DB 장소 id, 못 찾으면 null
    private String address;
    private Double lat;
    private Double lng;
}
