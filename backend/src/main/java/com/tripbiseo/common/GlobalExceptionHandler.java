package com.tripbiseo.common;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** 모든 오류를 {message} 한 모양으로. 화면은 message를 그대로 보여 줍니다. */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<Map<String, Object>> api(ApiException e) {
        if (e.getStatus() >= 500) log.warn("[{}] {}", e.getStatus(), e.getMessage());
        return body(e.getStatus(), e.getMessage());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> badJson(HttpMessageNotReadableException e) {
        return body(400, "요청 형식이 올바르지 않아요.");
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<Map<String, Object>> missing(MissingServletRequestParameterException e) {
        return body(400, e.getParameterName() + " 값이 필요해요.");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> unknown(Exception e) {
        log.error("처리하지 못한 오류", e);
        return body(500, "서버 오류가 났어요. 잠시 후 다시 시도해 주세요.");
    }

    private static ResponseEntity<Map<String, Object>> body(int status, String message) {
        return ResponseEntity.status(status).body(Map.of("message", message));
    }
}
