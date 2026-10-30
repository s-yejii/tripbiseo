package com.tripbiseo.trip.dto;

import lombok.Data;

/** 대화 기록 한 줄 (GET·POST /api/trips/{id}/chats) */
@Data
public class ChatMessage {
    private String role;    // me / ai
    private String text;    // 2000자까지
    private String at;      // yyyy-MM-ddTHH:mm (저장할 때는 :ss가 붙어도 됨)
}
