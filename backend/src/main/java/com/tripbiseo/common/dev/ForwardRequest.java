package com.tripbiseo.common.dev;

import lombok.Data;

/** 시연 시계 빨리 감기 요청 */
@Data
public class ForwardRequest {
    private int minutes; // 1 ~ 720
}
