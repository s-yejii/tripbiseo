package com.tripbiseo.live.dto;

import lombok.Data;

/** AI에게 묻기로 찾은 장소 한 곳 (화면 카드·지도 핀) */
@Data
public class AskedPlace {
    private String id;
    private String name;
    private String kind;           // cafe / food / bar …
    private String category;       // 세부 종류 (예: 카페,디저트)
    private Double lat;
    private Double lng;
    private Double rating;         // 보정 평점
    private Integer reviewCount;
    private String useTime;        // 영업시간 원문 (영업 중인지는 서버가 봄)
    private String restDate;       // 휴무 원문
    private Integer detourMinutes; // 가는 길에서 몇 분 더 (가는 길이 없으면 null)
    private Double joinLat;        // 가는 길 위에서 이 장소와 가장 가까운 점 = 지도 점선 시작
    private Double joinLng;
    private String closeAt;        // 닫는 시각 HH:mm (영업시간을 모르거나 24시간이면 null)
    private Boolean closingSoon;   // 곧 닫음
    private Integer slackAfter;    // 들러서 30분 머물면 다음 일정까지 남는 여유(분). 계산할 수 없으면 null
}
