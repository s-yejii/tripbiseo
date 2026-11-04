package com.tripbiseo.plan.dto;

import lombok.Data;

/** Gemini가 예약 캡처 사진에서 읽은 값 (JSON 답 모양). 안 보이면 null */
@Data
public class AiCapture {
    private String name;
    private String date;    // YYYY-MM-DD
    private String time;    // HH:mm
    private String address;
}
