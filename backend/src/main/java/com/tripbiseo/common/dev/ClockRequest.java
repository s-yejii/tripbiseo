package com.tripbiseo.common.dev;

import lombok.Data;

/** 시연 시계 맞추기 요청 */
@Data
public class ClockRequest {
    private String date; // "2026-11-07" (비우면 오늘)
    private String time; // "18:05"
}
