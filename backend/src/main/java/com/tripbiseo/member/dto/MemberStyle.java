package com.tripbiseo.member.dto;

import java.util.ArrayList;
import java.util.List;

import lombok.Data;

/**
 * 여행 스타일 (③ AI 일정이 MemberService.getStyle로 읽음).
 * TODO: ① 칸 확정 — member_style 테이블과 GET·PUT /api/members/me/style(확인 필요)을 정할 때 같이 맞추기
 */
@Data
public class MemberStyle {
    private String pace;                              // 속도: 여유롭게 / 보통 / 빡빡하게
    private List<String> likes = new ArrayList<>();   // 좋아하는 곳 (카페, 전시 …)
    private String walk;                              // 걷기: 적게 / 보통 / 많이
    private List<String> transports = new ArrayList<>(); // 이동 수단
    private boolean indoorWhenRain;                   // 비 오면 실내 위주
}
