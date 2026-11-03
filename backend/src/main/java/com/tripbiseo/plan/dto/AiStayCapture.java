package com.tripbiseo.plan.dto;

import lombok.Data;

/** Gemini가 숙소 예약 캡처에서 읽은 값 (JSON 답 모양). 안 보이면 null */
@Data
public class AiStayCapture {
    private String name;          // 숙소 이름
    private String address;       // 숙소 주소
    private String checkInDate;   // YYYY-MM-DD
    private String checkInTime;   // HH:mm
    private String checkOutDate;  // YYYY-MM-DD
    private String checkOutTime;  // HH:mm
}
