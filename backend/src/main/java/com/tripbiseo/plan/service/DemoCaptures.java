package com.tripbiseo.plan.service;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.tripbiseo.plan.dto.CaptureResponse;
import com.tripbiseo.plan.dto.ReceiptResponse;

/**
 * 시연 계정이 올린 "시연용 캡처 사진"이면 Gemini 대신 정해 둔 값을 돌려줘요 (③, 11/12 주형: "시연 땐 돌려도 고정값").
 * 사진 구분은 파일 지문(SHA-256)으로 해서, 같은 사진 파일이면 항상 같은 결과예요. 다른 사진이나 일반 계정은 진짜로 읽어요.
 * 촬영 때 받은 진짜 예약 문자 캡처로 바꾸려면 아래 표에 지문 한 줄씩 추가하면 돼요 (지문 구하기: certutil -hashfile 파일 SHA256).
 * 사진 파일은 커밋하지 않고 발표 PC 바탕화면 "착착트립_시연캡처" 폴더에 둬요.
 */
@Component
public class DemoCaptures {

    public static final long DEMO_DELAY_MS = 1500; // 스캔 모션이 보이게 잠깐 기다림

    private final Map<String, CaptureResponse> reserves = new HashMap<>();
    private final Map<String, ReceiptResponse> receipts = new HashMap<>();

    // 11/13: 촬영 날짜가 11/16(월)~17(화)로 정해져서 시연 사진·날짜를 새로 만듦 (예전 사진은 바탕화면 착착트립_시연캡처/예전_1114_15)
    public DemoCaptures() {
        // 예약 문자: 산청숯불가든 월 19:00 5명 (시연_예약_산청숯불가든.png)
        reserves.put("bcf037f90ae32f5ba753e61f20dbb43220bb82583a9ffe01801657a4f207b47b",
                makeReserve("산청숯불가든 을지로", "naver-1850187827", "2026-11-16", "19:00", "서울 중구 을지로 114-6 1층", 37.5658389, 126.9906186));
        // 예약 문자: 스시도 화 12:30 5명 (시연_예약_스시도.png)
        reserves.put("2246fb13b1f9ffa27435c5f5b16e10db2bb9a28e8dfcccc5111a48d9d0249a18",
                makeReserve("스시도", "naver-1008521969", "2026-11-17", "12:30", "서울 강남구 개포로 662 101호", 37.4952278, 127.0850148));
        // 영수증: 산청숯불가든 150,000원 20:52 (시연_영수증_산청숯불가든.png)
        receipts.put("30d51adb87e7f28ccc5f00c75e9f0766e381d6d485f04ac2a1b4cd1752364841",
                makeReceipt("산청숯불가든 을지로", "naver-1850187827", "2026-11-16 20:52", 150000, "서울 중구 을지로 114-6 1층", 37.5658389, 126.9906186));
    }

    /** 시연용 예약 캡처면 정해 둔 값, 아니면 null */
    public CaptureResponse findReserve(byte[] image) {
        return reserves.get(toHash(image));
    }

    /** 시연용 영수증이면 정해 둔 값, 아니면 null */
    public ReceiptResponse findReceipt(byte[] image) {
        return receipts.get(toHash(image));
    }

    /** 스캔 모션이 보이게 잠깐 기다림 (시연 고정값일 때만) */
    public void waitForScan() {
        try {
            Thread.sleep(DEMO_DELAY_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt(); // 서버가 멈추는 중이면 기다리지 않고 넘어감
        }
    }

    /** 사진 바이트 → SHA-256 지문(16진수 글자). MessageDigest: 자바 기본 해시 계산기 */
    private String toHash(byte[] image) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(image));
        } catch (NoSuchAlgorithmException e) {
            return ""; // SHA-256은 모든 자바에 있어서 실제로는 오지 않음
        }
    }

    private CaptureResponse makeReserve(String name, String placeId, String date, String time, String address, double lat, double lng) {
        CaptureResponse response = new CaptureResponse();
        response.setName(name);
        response.setPlaceId(placeId);
        response.setDate(date);
        response.setTime(time);
        response.setAddress(address);
        response.setLat(lat);
        response.setLng(lng);
        return response;
    }

    private ReceiptResponse makeReceipt(String storeName, String placeId, String paidAt, int amount, String address, double lat, double lng) {
        ReceiptResponse response = new ReceiptResponse();
        response.setStoreName(storeName);
        response.setPlaceId(placeId);
        response.setPaidAt(paidAt);
        response.setTotalAmount(amount);
        response.setAddress(address);
        response.setLat(lat);
        response.setLng(lng);
        return response;
    }
}
