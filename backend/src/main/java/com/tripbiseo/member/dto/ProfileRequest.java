package com.tripbiseo.member.dto;

import lombok.Data;

/** PUT /api/members/me 요청 {nickname} */
@Data
public class ProfileRequest {
    private String nickname;
}
