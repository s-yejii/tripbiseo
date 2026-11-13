package com.tripbiseo.live.dto;

import java.util.List;

import lombok.Data;

/** POST /live/assist 요청: 여행 중 채팅 한 마디 */
@Data
public class AssistRequest {
    private String message; // 1~300자
    private Double lat;
    private Double lng;
    private String prevMessage;        // 바로 앞 질문 (없으면 null). "이 중에 …"처럼 이어서 물을 때 (11/13)
    private List<String> prevPlaceIds; // 바로 앞에 찾은 장소 id 목록 (최대 10, 없으면 null)
}
