package com.tripbiseo.plan.service;

import com.tripbiseo.plan.dto.CaptureResponse;
import com.tripbiseo.plan.dto.ReceiptResponse;
import com.tripbiseo.plan.dto.StayCaptureResponse;

/** ③ 예약 캡처 읽기 (OCR): 사진 → 장소 이름·날짜·시각 (고정 일정 후보) */
public interface CaptureService {

    /** 예약 캡처 사진을 읽어 고정 일정 후보로. 사진은 저장하지 않음. 시연 계정이 시연용 사진을 올리면 정해 둔 값 */
    CaptureResponse readCapture(long memberId, byte[] image, String imageType);

    /** 숙소 예약 캡처를 읽어 숙소 후보로 (이름·주소·체크인·체크아웃 + 위치). 사진은 저장하지 않음 */
    StayCaptureResponse readStayCapture(byte[] image, String imageType);

    /** 영수증 사진을 읽어 결제 후보로 (가게·결제 시각·합계 + 장소). 사진도 결과도 저장하지 않음 (⑤ 결제 메모가 확인 후 저장). 시연 계정이 시연용 사진을 올리면 정해 둔 값 */
    ReceiptResponse readReceipt(long memberId, byte[] image, String imageType);
}
