package com.tripbiseo.common.dev;

import lombok.Data;

/** 시연 시계 배속 요청 { "speed": 30 }. 0이면 멈춤, 1이면 진짜 시간처럼 */
@Data
public class SpeedRequest {
    private int speed;
}
