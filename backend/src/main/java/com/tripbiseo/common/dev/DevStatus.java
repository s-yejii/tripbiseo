package com.tripbiseo.common.dev;

import lombok.Data;

/** 개발자 모드 상태 응답 */
@Data
public class DevStatus {
    private boolean allowed; // 시연 계정인지 (아니면 프론트가 개발자 모드를 열지 않음)
    private String now; // 서버가 지금으로 보는 시각 "2026-11-07T18:05"
    private long offsetMinutes; // 진짜 시각과의 차이(분). 0이면 진짜 시각
}
