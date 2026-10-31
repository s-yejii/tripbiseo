package com.tripbiseo.trip.dto;

import lombok.Data;

/** 일정 한 칸 (공통 데이터 Stop, DB trip_stop). 컬럼 이름이 달라요: day↔day_no, time↔start_time, stay↔stay_min, mode↔move_mode */
@Data
public class Stop {
    private Long id;            // 저장 전 초안이면 null
    private Long tripId;        // 초안이면 null
    private Integer day;        // 몇째 날 (1부터)
    private String placeId;     // retrip_place.id, DB에 없는 장소면 null
    private String name;        // 1~120자
    private String category;
    private String address;
    private Double lat;
    private Double lng;
    private String time;        // 시작 시각 HH:mm
    private Integer stay;       // 머무는 시간(분) 0~720
    private Boolean fixed;      // 늦으면 안 되는 일정(공연·예약)
    private String mode;        // 앞 장소에서 오는 수단 walk / transit / taxi, null이면 거리로 자동
    private String memo;        // 200자까지
    private String state;       // todo / done / skipped
    private String arrivedAt;   // GPS 150m 안 도착 시각 yyyy-MM-ddTHH:mm:ss
    private String departedAt;  // 200m 밖으로 두 번 연속 나간 시각
}
