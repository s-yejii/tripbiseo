package com.tripbiseo.member.dto;

import lombok.Data;

/** PUT /api/members/me/password 요청 {currentPassword, newPassword} */
@Data
public class PasswordRequest {
    private String currentPassword;
    private String newPassword;
}
