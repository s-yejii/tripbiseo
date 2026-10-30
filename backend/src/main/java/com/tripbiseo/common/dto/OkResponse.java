package com.tripbiseo.common.dto;

import lombok.Data;

/** 돌려줄 값이 없는 API의 성공 응답 { "ok": true } */
@Data
public class OkResponse {
    private boolean ok = true;
}
