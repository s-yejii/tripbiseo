package com.tripbiseo.plan.service;

import com.tripbiseo.plan.dto.CaptureResponse;

/** ③ 예약 캡처 읽기 (OCR): 사진 → 장소 이름·날짜·시각 (고정 일정 후보) */
public interface CaptureService {

    /** 예약 캡처 사진을 읽어 고정 일정 후보로. 사진은 저장하지 않음 */
    CaptureResponse readCapture(byte[] image, String imageType);
}
