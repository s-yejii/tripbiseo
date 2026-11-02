package com.tripbiseo.member.dto;

import lombok.Data;

/** GET /api/auth/check-id 응답 */
@Data
public class CheckIdResponse {
    private boolean available; // true면 쓸 수 있는 아이디
}