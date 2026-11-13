package com.tripbiseo.live.dto;

import lombok.Data;

/** AI SQL의 빈칸(:route·:me·:next·:slack·:prev)에 서버가 넣을 값. 좌표는 'SRID=4326;POINT(경도 위도)' 글자 */
@Data
public class AskSituation {
    private String route;   // 지금 가는 길 LINESTRING. 대중교통이거나 남은 일정이 없으면 null
    private String me;      // 내 위치 POINT
    private String next;    // 다음 장소 POINT. 없으면 null
    private Integer slack;  // 다음 일정까지 여유(분). 없으면 null
    private String prev;    // 바로 앞 결과 장소 id "123,456" (이어지는 질문일 때만, 아니면 null)
    private Double meLat;   // 실제 걷는 길로 다시 잴 때 쓰는 좌표 (Gemini에는 안 보냄)
    private Double meLng;
    private Double nextLat; // 다음 장소. 없으면 null
    private Double nextLng;
}
