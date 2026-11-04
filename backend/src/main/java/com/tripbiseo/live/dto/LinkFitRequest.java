package com.tripbiseo.live.dto;

import java.util.List;

import lombok.Data;

/** 여행 중 링크로 찾은 장소들을 넣어 보기 요청 */
@Data
public class LinkFitRequest {
    private Double lat;             // 지금 위치
    private Double lng;
    private List<LinkPlace> places; // 넣을 장소 (1곳 이상)

    /** 넣을 장소 하나 */
    @Data
    public static class LinkPlace {
        private String placeId;     // DB에 없는 장소면 null
        private String name;
        private String category;
        private String address;
        private Double lat;
        private Double lng;
    }
}
