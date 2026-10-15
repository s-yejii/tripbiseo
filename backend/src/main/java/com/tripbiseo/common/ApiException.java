package com.tripbiseo.common;

/**
 * 화면에 그대로 보여줄 업무 오류. status는 HTTP 상태 코드(400 입력 오류, 404 없음, 409 버전 충돌, 502 외부 API 실패, 503 키 없음 …).
 * GlobalExceptionHandler가 {code, message} 응답으로 바꿉니다.
 */
public class ApiException extends RuntimeException {

    private final int status;

    public ApiException(int status, String message) {
        super(message);
        this.status = status;
    }

    public int getStatus() {
        return status;
    }

    public static ApiException fail(int status, String message) {
        return new ApiException(status, message);
    }
}
