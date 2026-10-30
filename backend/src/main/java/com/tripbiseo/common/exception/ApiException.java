package com.tripbiseo.common.exception;

/**
 * 화면에 그대로 보여 줄 업무 오류. status는 HTTP 상태 코드(400 입력 오류, 401 로그인 필요, 404 없음, 409 충돌, 502·503·504 외부 API 문제).
 * GlobalExceptionHandler가 {"message": "..."} 응답으로 바꿉니다. (docs/API.md 공통 규칙)
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
