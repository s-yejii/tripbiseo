package com.tripbiseo.member.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.Data;

/** GET /api/config/public 응답. 키가 없으면 빈 객체 {} (NON_NULL: null인 칸은 응답에서 뺌) */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PublicConfigResponse {
    private String kakaoJsKey;
}
