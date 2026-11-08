package com.tripbiseo.live.dto;

import lombok.Data;

/** Gemini 답: 질문을 SQL로 바꿀 수 있으면 sql·explain, 없으면 reason */
@Data
public class SqlAnswer {
    private Boolean supported; // SQL로 찾을 수 있는 질문인가
    private String sql;        // supported일 때: SELECT 문 (빈칸 :route·:me·:next·:slack 사용)
    private String explain;    // supported일 때: 어떻게 찾았는지 한 줄 (화면에 보여 줌)
    private String reason;     // 아닐 때: 왜 못 찾는지 한 줄 (화면에 보여 줌)
}
